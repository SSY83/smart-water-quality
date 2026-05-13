package com.waterquality.service;

import cn.hutool.core.util.IdUtil;
import com.waterquality.dto.AlertResult;
import com.waterquality.dto.AnalysisResult;
import com.waterquality.dto.PushTask;
import com.waterquality.entity.AlertRecord;
import com.waterquality.entity.MonitoringPoint;
import com.waterquality.enums.AlertLevel;
import com.waterquality.mapper.AlertRecordMapper;
import com.waterquality.mapper.MonitoringPointMapper;
import com.waterquality.websocket.AlertWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.PriorityBlockingQueue;

@Service
public class AlertPushService {

    private static final Logger log = LoggerFactory.getLogger(AlertPushService.class);

    private final AlertRecordMapper alertRecordMapper;
    private final MonitoringPointMapper monitoringPointMapper;
    private final SmsService smsService;
    private final AlertWebSocketHandler webSocketHandler;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    private final Executor retryExecutor;

    private final PriorityBlockingQueue<PushTask> retryQueue;

    private static final int MAX_RETRY = 5;
    private static final int[] RETRY_INTERVALS_MINUTES = {1, 2, 4, 8, 16};

    public AlertPushService(AlertRecordMapper alertRecordMapper,
                             MonitoringPointMapper monitoringPointMapper,
                             SmsService smsService,
                             AlertWebSocketHandler webSocketHandler,
                             @Qualifier("retryExecutor") Executor retryExecutor) {
        this.alertRecordMapper = alertRecordMapper;
        this.monitoringPointMapper = monitoringPointMapper;
        this.smsService = smsService;
        this.webSocketHandler = webSocketHandler;
        this.retryExecutor = retryExecutor;
        this.retryQueue = new PriorityBlockingQueue<>();
    }

    /**
     * 推送预警 - 同步函数调用接口
     */
    public AlertResult pushAlert(AnalysisResult result) {
        if (result == null || result.getAlertLevel() == AlertLevel.NORMAL.getLevelCode()) {
            return AlertResult.skipped();
        }

        try {
            // 生成告警记录
            AlertRecord record = new AlertRecord();
            record.setPointId(Long.parseLong(result.getPointId()));
            record.setAlertLevel(result.getAlertLevel());
            record.setAlertType(determineAlertType(result));
            record.setDetails(buildDetailsJson(result));
            record.setPushStatus("pending");
            record.setCreateTime(LocalDateTime.now());
            record.setRetryCount(0);
            alertRecordMapper.insert(record);

            String alertId = String.valueOf(record.getId());

            // 根据异常等级选择推送渠道
            List<String> channels = determineChannels(result.getAlertLevel());

            // 轻度异常：仅写入数据库
            if (result.getAlertLevel() == AlertLevel.MILD.getLevelCode()) {
                record.setPushStatus("sent");
                alertRecordMapper.updateById(record);
                return AlertResult.success(alertId);
            }

            // 中度异常：数据库 + WebSocket
            if (result.getAlertLevel() == AlertLevel.MODERATE.getLevelCode()) {
                String payload = buildAlertPayload(record);
                webSocketHandler.broadcastAlert(payload);
                record.setPushStatus("sent");
                alertRecordMapper.updateById(record);
                return AlertResult.success(alertId);
            }

            // 重度异常：全部渠道 (数据库 + WebSocket + 短信)
            String payload = buildAlertPayload(record);
            webSocketHandler.broadcastAlert(payload);

            PushTask task = new PushTask(IdUtil.fastSimpleUUID(), record.getId(), channels);
            task.setRetryCount(0);
            task.setNextRetryTime(LocalDateTime.now());
            retryQueue.offer(task);

            return AlertResult.success(alertId);

        } catch (Exception e) {
            log.error("告警推送失败: pointId={}", result.getPointId(), e);
            return AlertResult.failed();
        }
    }

    private String determineAlertType(AnalysisResult result) {
        if (result.getDetails() == null) return "combined";
        if (result.getDetails().containsKey("turbidity") &&
            toDouble(result.getDetails().get("turbidity")) > 30) return "turbidity";
        if (result.getDetails().containsKey("cod") &&
            toDouble(result.getDetails().get("cod")) > 30) return "cod";
        if (result.getDetails().containsKey("ph")) {
            double ph = toDouble(result.getDetails().get("ph"));
            if (ph < 6.5 || ph > 8.5) return "ph";
        }
        return "combined";
    }

    private double toDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (Exception e) {
            return 0.0;
        }
    }

    private List<String> determineChannels(int alertLevel) {
        switch (alertLevel) {
            case 1: return Collections.singletonList("platform");
            case 2: return Arrays.asList("platform", "websocket");
            case 3: return Arrays.asList("platform", "websocket", "sms");
            default: return Collections.singletonList("platform");
        }
    }

    private String buildDetailsJson(AnalysisResult result) {
        StringBuilder sb = new StringBuilder("{");
        if (result.getDetails() != null) {
            result.getDetails().forEach((k, v) ->
                sb.append("\"").append(k).append("\":\"").append(v).append("\","));
        }
        sb.append("\"confidence\":").append(result.getConfidence()).append(",");
        sb.append("\"finalScore\":").append(result.getFinalScore());
        sb.append("}");
        return sb.toString();
    }

    private String buildAlertPayload(AlertRecord record) {
        MonitoringPoint point = monitoringPointMapper.selectById(record.getPointId());
        String pointName = point != null ? point.getName() : "未知监测点";
        return String.format(
            "{\"type\":\"alert\",\"data\":{\"alertId\":%d,\"pointName\":\"%s\",\"level\":%d,\"type\":\"%s\",\"time\":\"%s\"}}",
            record.getId(), pointName, record.getAlertLevel(),
            record.getAlertType(), record.getCreateTime());
    }

    /**
     * 定时扫描重试队列 (每5分钟)
     */
    @Scheduled(fixedDelay = 300000)
    public void retryFailedPushes() {
        List<PushTask> tasks = new ArrayList<>();
        retryQueue.drainTo(tasks);
        for (PushTask task : tasks) {
            retryExecutor.execute(() -> executeRetry(task));
        }
    }

    private void executeRetry(PushTask task) {
        if (task.getRetryCount() >= MAX_RETRY) {
            AlertRecord record = new AlertRecord();
            record.setId(task.getAlertId());
            record.setPushStatus("failed");
            alertRecordMapper.updateById(record);
            log.error("告警重试全部失败: alertId={}", task.getAlertId());
            return;
        }

        try {
            boolean success = smsService.sendAlert(task.getAlertId());
            if (success) {
                AlertRecord record = new AlertRecord();
                record.setId(task.getAlertId());
                record.setPushStatus("sent");
                alertRecordMapper.updateById(record);
            } else {
                task.setRetryCount(task.getRetryCount() + 1);
                int delayMinutes = RETRY_INTERVALS_MINUTES[
                    Math.min(task.getRetryCount() - 1, RETRY_INTERVALS_MINUTES.length - 1)];
                task.setNextRetryTime(LocalDateTime.now().plusMinutes(delayMinutes));
                retryQueue.offer(task);
            }
        } catch (Exception e) {
            log.error("重试推送失败: alertId={}", task.getAlertId(), e);
            task.setRetryCount(task.getRetryCount() + 1);
            task.setNextRetryTime(LocalDateTime.now().plusMinutes(1));
            retryQueue.offer(task);
        }
    }
}

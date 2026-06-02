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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.PriorityBlockingQueue;

@Service
public class AlertPushService {

    private static final Logger log = LoggerFactory.getLogger(AlertPushService.class);

    @Value("${alert.threshold.turbidity.mild:5.0}")
    private double turbidityMild;

    @Value("${alert.threshold.turbidity.moderate:30.0}")
    private double turbidityModerate;

    @Value("${alert.threshold.turbidity.severe:80.0}")
    private double turbiditySevere;

    @Value("${alert.threshold.cod.mild:15.0}")
    private double codMild;

    @Value("${alert.threshold.cod.moderate:30.0}")
    private double codModerate;

    @Value("${alert.threshold.cod.severe:50.0}")
    private double codSevere;

    @Value("${alert.threshold.ph.min:6.5}")
    private double phMin;

    @Value("${alert.threshold.ph.max:8.5}")
    private double phMax;

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

    public AlertResult pushAlert(AnalysisResult result) {
        if (result == null || result.getAlertLevel() == AlertLevel.NORMAL.getLevelCode()) {
            return AlertResult.skipped();
        }

        try {
            Long pointId = Long.parseLong(result.getPointId());

            // 检查告警升级：查看该监测点最近的告警记录
            int escalatedLevel = checkAlertEscalation(pointId, result.getAlertLevel());

            AlertRecord record = new AlertRecord();
            record.setPointId(pointId);
            record.setAlertLevel(escalatedLevel);
            record.setAlertType(determineAlertType(result));
            record.setDetails(buildDetailsJson(result));
            record.setPushStatus("pending");
            record.setCreateTime(LocalDateTime.now());
            record.setRetryCount(0);
            alertRecordMapper.insert(record);

            String alertId = String.valueOf(record.getId());

            List<String> channels = determineChannels(escalatedLevel);

            if (escalatedLevel == AlertLevel.MILD.getLevelCode()) {
                record.setPushStatus("sent");
                alertRecordMapper.updateById(record);
                return AlertResult.success(alertId);
            }

            if (escalatedLevel == AlertLevel.MODERATE.getLevelCode()) {
                String payload = buildAlertPayload(record);
                webSocketHandler.broadcastAlert(payload);
                record.setPushStatus("sent");
                alertRecordMapper.updateById(record);
                return AlertResult.success(alertId);
            }

            String payload = buildAlertPayload(record);
            webSocketHandler.broadcastAlert(payload);

            // 重度告警立即发送短信（不等重试周期）
            retryExecutor.execute(() -> smsService.sendAlert(record.getId()));

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

    /**
     * 多参数联合告警规则：综合浊度+COD+pH组合判断
     */
    public int evaluateCombinedAlert(double turbidity, double cod, double ph) {
        int turbidityLevel = evaluateTurbidity(turbidity);
        int codLevel = evaluateCod(cod);
        int phLevel = evaluatePh(ph);

        // 多个参数同时超标 → 升级
        int abnormalCount = (turbidityLevel > 0 ? 1 : 0) +
                (codLevel > 0 ? 1 : 0) +
                (phLevel > 0 ? 1 : 0);

        int maxLevel = Math.max(turbidityLevel, Math.max(codLevel, phLevel));

        if (abnormalCount >= 3) {
            return Math.min(maxLevel + 1, AlertLevel.SEVERE.getLevelCode());
        }
        if (abnormalCount >= 2 && maxLevel >= AlertLevel.MODERATE.getLevelCode()) {
            return Math.min(maxLevel + 1, AlertLevel.SEVERE.getLevelCode());
        }
        return maxLevel;
    }

    private int evaluateTurbidity(double turbidity) {
        if (turbidity >= turbiditySevere) return AlertLevel.SEVERE.getLevelCode();
        if (turbidity >= turbidityModerate) return AlertLevel.MODERATE.getLevelCode();
        if (turbidity >= turbidityMild) return AlertLevel.MILD.getLevelCode();
        return AlertLevel.NORMAL.getLevelCode();
    }

    private int evaluateCod(double cod) {
        if (cod >= codSevere) return AlertLevel.SEVERE.getLevelCode();
        if (cod >= codModerate) return AlertLevel.MODERATE.getLevelCode();
        if (cod >= codMild) return AlertLevel.MILD.getLevelCode();
        return AlertLevel.NORMAL.getLevelCode();
    }

    private int evaluatePh(double ph) {
        if (ph < phMin - 1.0 || ph > phMax + 1.0) return AlertLevel.SEVERE.getLevelCode();
        if (ph < phMin || ph > phMax) return AlertLevel.MODERATE.getLevelCode();
        return AlertLevel.NORMAL.getLevelCode();
    }

    /**
     * 检查告警升级：同一监测点短时间内频繁告警则自动升级
     */
    private int checkAlertEscalation(Long pointId, int currentLevel) {
        try {
            // 查询该监测点过去30分钟内的告警记录
            List<AlertRecord> recentAlerts = alertRecordMapper.selectByPointAndTimeRange(
                    pointId,
                    LocalDateTime.now().minusMinutes(30),
                    LocalDateTime.now(),
                    10);

            if (recentAlerts.size() >= 5) {
                return Math.min(currentLevel + 2, AlertLevel.SEVERE.getLevelCode());
            }
            if (recentAlerts.size() >= 3) {
                return Math.min(currentLevel + 1, AlertLevel.SEVERE.getLevelCode());
            }
        } catch (Exception e) {
            log.warn("告警升级检查失败: pointId={}", pointId, e);
        }
        return currentLevel;
    }

    /**
     * 告警统计报表
     */
    public Map<String, Object> getAlertStatistics(List<Long> pointIds,
                                                   LocalDateTime start, LocalDateTime end) {
        Map<String, Object> stats = new LinkedHashMap<>();

        long totalCount = 0;
        Map<Integer, Long> levelCounts = new LinkedHashMap<>();
        for (int i = 1; i <= 3; i++) {
            levelCounts.put(i, 0L);
        }
        Map<String, Long> typeCounts = new HashMap<>();

        for (Long pointId : pointIds) {
            List<AlertRecord> alerts = alertRecordMapper.selectByPointAndTimeRange(
                    pointId, start, end, 1000);
            totalCount += alerts.size();
            for (AlertRecord a : alerts) {
                levelCounts.merge(a.getAlertLevel(), 1L, Long::sum);
                if (a.getAlertType() != null) {
                    typeCounts.merge(a.getAlertType(), 1L, Long::sum);
                }
            }
        }

        stats.put("totalCount", totalCount);
        stats.put("period", start.toString() + " ~ " + end.toString());
        stats.put("byLevel", levelCounts);
        stats.put("byType", typeCounts);

        // 升级统计
        long escalatedCount = alertRecordMapper.countSince(start);
        stats.put("recentPushedCount", escalatedCount);

        return stats;
    }

    /**
     * 获取告警统计 (简化版，单监测点)
     */
    public Map<String, Object> getPointAlertStats(Long pointId, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<AlertRecord> alerts = alertRecordMapper.selectByPointAndTimeRange(
                pointId, since, LocalDateTime.now(), 1000);

        long total = alerts.size();
        long confirmed = alerts.stream().filter(a -> "confirmed".equals(a.getPushStatus())).count();
        long failed = alerts.stream().filter(a -> "failed".equals(a.getPushStatus())).count();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("pointId", pointId);
        stats.put("days", days);
        stats.put("total", total);
        stats.put("confirmed", confirmed);
        stats.put("failed", failed);
        stats.put("confirmRate", total > 0 ? Math.round((double) confirmed / total * 10000.0) / 100.0 : 0);

        // 按等级分布
        Map<Integer, Long> levelDist = new HashMap<>();
        for (AlertRecord a : alerts) {
            levelDist.merge(a.getAlertLevel(), 1L, Long::sum);
        }
        stats.put("levelDistribution", levelDist);
        return stats;
    }

    private String determineAlertType(AnalysisResult result) {
        if (result.getDetails() == null) return "combined";
        double turbidity = toDouble(result.getDetails().getOrDefault("turbidity", 0));
        double cod = toDouble(result.getDetails().getOrDefault("cod", 0));
        double ph = toDouble(result.getDetails().getOrDefault("ph", 7.0));

        StringBuilder type = new StringBuilder();
        if (turbidity > turbidityMild) type.append("turbidity,");
        if (cod > codMild) type.append("cod,");
        if (ph < phMin || ph > phMax) type.append("ph,");

        if (type.length() > 0) {
            return type.substring(0, type.length() - 1);
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

package com.waterquality.service;

import com.waterquality.entity.AlertRecord;
import com.waterquality.entity.MonitoringPoint;
import com.waterquality.entity.User;
import com.waterquality.mapper.AlertRecordMapper;
import com.waterquality.mapper.MonitoringPointMapper;
import com.waterquality.mapper.UserMapper;
import com.waterquality.service.sms.SmsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    @Value("${sms.retry-max:3}")
    private int maxRetry;

    @Value("${sms.circuit-breaker.failure-threshold:5}")
    private int failureThreshold;

    @Value("${sms.circuit-breaker.open-duration:600}")
    private long openDurationMs;

    private final AlertRecordMapper alertRecordMapper;
    private final MonitoringPointMapper monitoringPointMapper;
    private final UserMapper userMapper;
    private final SmsProvider smsProvider;

    private final AtomicInteger failureCount = new AtomicInteger(0);
    private volatile long circuitOpenTime = 0;
    private volatile boolean circuitOpen = false;

    public SmsService(AlertRecordMapper alertRecordMapper,
                      MonitoringPointMapper monitoringPointMapper,
                      UserMapper userMapper,
                      SmsProvider smsProvider) {
        this.alertRecordMapper = alertRecordMapper;
        this.monitoringPointMapper = monitoringPointMapper;
        this.userMapper = userMapper;
        this.smsProvider = smsProvider;
    }

    /**
     * 异步发送短信告警
     */
    @Async("smsExecutor")
    public boolean sendAlert(Long alertId) {
        if (isCircuitOpen()) {
            log.warn("短信服务已熔断，跳过发送: alertId={}", alertId);
            return false;
        }

        AlertRecord record = alertRecordMapper.selectById(alertId);
        if (record == null) return false;

        MonitoringPoint point = monitoringPointMapper.selectById(record.getPointId());
        String pointName = point != null ? point.getName() : "未知监测点";

        String phone = getReceiverPhone(record, point);
        if (phone == null || phone.isEmpty()) {
            log.warn("无法获取告警接收手机号: alertId={}", alertId);
            return false;
        }

        String content = buildSmsContent(alertId, pointName, record);
        if (content == null) return false;

        if ("mock".equals(smsProvider.getName())) {
            log.info("[MockSMS] 收件人={}, 内容={}", phone, content);
            failureCount.set(0);
            return true;
        }

        return sendViaProvider(phone, content, record);
    }

    private boolean sendViaProvider(String phone, String content, AlertRecord record) {
        int retries = 0;
        while (retries < maxRetry) {
            try {
                boolean success = smsProvider.send(phone, content, "水质监测");
                if (success) {
                    failureCount.set(0);
                    return true;
                }
            } catch (Exception e) {
                log.error("短信发送异常: alertId={}, retry={}", record.getId(), retries, e);
            }

            retries++;
            if (retries < maxRetry) {
                try {
                    Thread.sleep((long) Math.pow(2, retries) * 1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        int failures = failureCount.incrementAndGet();
        if (failures >= failureThreshold) {
            openCircuit();
        }
        return false;
    }

    private String buildSmsContent(Long alertId, String pointName, AlertRecord record) {
        String levelDesc = record.getAlertLevelDescription();
        return String.format("【水质预警】%s%s，请及时处理。告警ID:%d",
                pointName, levelDesc, alertId);
    }

    private String getReceiverPhone(AlertRecord record, MonitoringPoint point) {
        // 1. 优先从监测点获取联系人手机号
        if (point != null && point.getContactPhone() != null
                && !point.getContactPhone().isEmpty()) {
            return point.getContactPhone();
        }
        // 2. 回退到查询管理员手机号
        List<User> admins = userMapper.selectByRole("admin");
        if (admins != null && !admins.isEmpty() && admins.get(0).getPhone() != null) {
            return admins.get(0).getPhone();
        }
        // 3. 默认号码（生产环境需替换）
        return "13800000000";
    }

    // 熔断器实现
    private boolean isCircuitOpen() {
        if (!circuitOpen) return false;
        if (System.currentTimeMillis() - circuitOpenTime > openDurationMs) {
            // 半开状态，允许通过
            circuitOpen = false;
            failureCount.set(0);
            log.info("短信服务熔断器进入半开状态");
            return false;
        }
        return true;
    }

    private void openCircuit() {
        circuitOpen = true;
        circuitOpenTime = System.currentTimeMillis();
        log.warn("短信服务熔断器已打开，持续{}ms", openDurationMs);
    }
}

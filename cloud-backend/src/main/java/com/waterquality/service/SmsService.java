package com.waterquality.service;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.waterquality.entity.AlertRecord;
import com.waterquality.entity.MonitoringPoint;
import com.waterquality.mapper.AlertRecordMapper;
import com.waterquality.mapper.MonitoringPointMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    @Value("${sms.api-url}")
    private String smsApiUrl;

    @Value("${sms.api-key}")
    private String smsApiKey;

    @Value("${sms.retry-max:3}")
    private int maxRetry;

    @Value("${sms.connect-timeout:3}")
    private int connectTimeout;

    @Value("${sms.read-timeout:5}")
    private int readTimeout;

    @Value("${sms.circuit-breaker.failure-threshold:5}")
    private int failureThreshold;

    @Value("${sms.circuit-breaker.open-duration:600}")
    private long openDurationMs;

    private final AlertRecordMapper alertRecordMapper;
    private final MonitoringPointMapper monitoringPointMapper;

    private final AtomicInteger failureCount = new AtomicInteger(0);
    private volatile long circuitOpenTime = 0;
    private volatile boolean circuitOpen = false;

    private final Map<String, String> smsContentCache = new ConcurrentHashMap<>();

    public SmsService(AlertRecordMapper alertRecordMapper,
                      MonitoringPointMapper monitoringPointMapper) {
        this.alertRecordMapper = alertRecordMapper;
        this.monitoringPointMapper = monitoringPointMapper;
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

        String content = buildSmsContent(alertId, pointName, record);
        if (content == null) return false;

        int retries = 0;
        while (retries < maxRetry) {
            try {
                JSONObject body = new JSONObject();
                body.set("mobile", getReceiverPhone(record));
                body.set("content", content);
                body.set("sign", "水质监测");

                HttpResponse response = HttpRequest.post(smsApiUrl)
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + smsApiKey)
                        .body(body.toString())
                        .timeout(readTimeout * 1000)
                        .setConnectionTimeout(connectTimeout * 1000)
                        .execute();

                if (response.isOk()) {
                    JSONObject respJson = JSONUtil.parseObj(response.body());
                    if ("SUCCESS".equals(respJson.getStr("code"))) {
                        failureCount.set(0);
                        return true;
                    } else {
                        log.warn("短信API返回失败: {}", respJson.getStr("message"));
                    }
                }
            } catch (Exception e) {
                log.error("短信发送异常: alertId={}, retry={}", alertId, retries, e);
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

    private String getReceiverPhone(AlertRecord record) {
        // 从监测点配置中获取负责人手机号
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

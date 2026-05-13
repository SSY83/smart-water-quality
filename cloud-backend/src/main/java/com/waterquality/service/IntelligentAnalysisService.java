package com.waterquality.service;

import com.waterquality.dto.AnalysisResult;
import com.waterquality.dto.AlertResult;
import com.waterquality.enums.AlertLevel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 云端智能分析服务 - 负责接收边缘端分析结果，执行多源数据融合验证
 */
@Service
public class IntelligentAnalysisService {

    @Value("${fusion.image-weight:0.6}")
    private double imageWeight;

    @Value("${fusion.sensor-weight:0.4}")
    private double sensorWeight;

    @Value("${fusion.time-window-ms:500}")
    private long timeWindowMs;

    @Value("${fusion.turbidity-threshold:80.0}")
    private double turbidityThreshold;

    @Value("${fusion.cod-threshold:50.0}")
    private double codThreshold;

    private final AlertPushService alertPushService;
    private final DataCollectionService dataCollectionService;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    public IntelligentAnalysisService(AlertPushService alertPushService,
                                       DataCollectionService dataCollectionService) {
        this.alertPushService = alertPushService;
        this.dataCollectionService = dataCollectionService;
    }

    /**
     * 接收边缘端分析结果，触发后续处理
     */
    public AlertResult processAnalysisResult(AnalysisResult result) {
        if (result == null) {
            throw new IllegalArgumentException("分析结果不能为空");
        }

        // 正常数据不触发预警，仅存储
        if (result.getAlertLevel() == AlertLevel.NORMAL.getLevelCode()) {
            dataCollectionService.receiveData(result);
            return AlertResult.skipped();
        }

        // 存储数据
        dataCollectionService.receiveData(result);

        // 触发预警推送
        return alertPushService.pushAlert(result);
    }

    /**
     * 云端验证融合 - 对边缘端上传的结果进行二次验证
     */
    public double verifyFusionScore(double imageScore, double sensorScore) {
        return imageScore * imageWeight + sensorScore * sensorWeight;
    }

    /**
     * 根据融合分数确定异常等级
     */
    public int determineAlertLevel(double finalScore) {
        if (finalScore < 0.4) return AlertLevel.NORMAL.getLevelCode();
        if (finalScore < 0.7) return AlertLevel.MILD.getLevelCode();
        if (finalScore < 0.9) return AlertLevel.MODERATE.getLevelCode();
        return AlertLevel.SEVERE.getLevelCode();
    }

    /**
     * 降级模式：当模型推理失败时，基于传感器数据进行规则判断
     */
    @SuppressWarnings("unchecked")
    public AnalysisResult ruleBasedAnalysis(Long pointId, Map<String, Object> sensorData) {
        double turbidity = toDouble(sensorData.getOrDefault("turbidity", 0.0));
        double cod = toDouble(sensorData.getOrDefault("cod", 0.0));
        double ph = toDouble(sensorData.getOrDefault("ph", 7.0));

        double turbScore = Math.min(turbidity / turbidityThreshold, 1.0);
        double codScore = Math.min(cod / codThreshold, 1.0);
        double phScore = Math.abs(ph - 7.0) / 3.5;

        double sensorScore = turbScore * 0.5 + codScore * 0.3 + phScore * 0.2;
        int alertLevel = determineAlertLevel(sensorScore);

        AnalysisResult result = new AnalysisResult();
        result.setPointId(String.valueOf(pointId));
        result.setTimestamp(LocalDateTime.now());
        result.setAlertLevel(alertLevel);
        result.setSensorScore(sensorScore);
        result.setConfidence(sensorScore * 0.8);
        Map<String, Object> details = new HashMap<>();
        sensorData.forEach((k, v) -> details.put(k, v));
        result.setDetails(details);
        return result;
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
}

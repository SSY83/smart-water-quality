package com.waterquality.service;

import com.waterquality.dto.AlertResult;
import com.waterquality.dto.AnalysisResult;
import com.waterquality.entity.WaterQualityData;
import com.waterquality.enums.AlertLevel;
import com.waterquality.mapper.WaterQualityDataMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class IntelligentAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(IntelligentAnalysisService.class);

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
    private final WaterQualityDataMapper waterQualityDataMapper;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    public IntelligentAnalysisService(AlertPushService alertPushService,
                                       DataCollectionService dataCollectionService,
                                       WaterQualityDataMapper waterQualityDataMapper) {
        this.alertPushService = alertPushService;
        this.dataCollectionService = dataCollectionService;
        this.waterQualityDataMapper = waterQualityDataMapper;
    }

    public AlertResult processAnalysisResult(AnalysisResult result) {
        if (result == null) {
            throw new IllegalArgumentException("分析结果不能为空");
        }

        if (result.getAlertLevel() == AlertLevel.NORMAL.getLevelCode()) {
            dataCollectionService.receiveData(result);
            return AlertResult.skipped();
        }

        dataCollectionService.receiveData(result);
        return alertPushService.pushAlert(result);
    }

    public double verifyFusionScore(double imageScore, double sensorScore) {
        return imageScore * imageWeight + sensorScore * sensorWeight;
    }

    public int determineAlertLevel(double finalScore) {
        if (finalScore < 0.4) return AlertLevel.NORMAL.getLevelCode();
        if (finalScore < 0.7) return AlertLevel.MILD.getLevelCode();
        if (finalScore < 0.9) return AlertLevel.MODERATE.getLevelCode();
        return AlertLevel.SEVERE.getLevelCode();
    }

    /**
     * 滑动窗口异常趋势检测
     * 将时间窗口内的数据分成前后两段，比较均值变化趋势
     */
    public Map<String, Object> detectTrendAnomaly(Long pointId, int windowMinutes) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusMinutes(windowMinutes);
        LocalDateTime mid = start.plusMinutes(windowMinutes / 2);

        List<WaterQualityData> windowData = waterQualityDataMapper
                .selectWindowByPoint(pointId, start, end);

        if (windowData.size() < 4) {
            Map<String, Object> result = new HashMap<>();
            result.put("trend", "insufficient_data");
            result.put("message", "窗口内数据不足，至少需要4条记录");
            return result;
        }

        // 分前后两段
        List<WaterQualityData> firstHalf = windowData.stream()
                .filter(d -> !d.getTimestamp().isAfter(mid)).collect(Collectors.toList());
        List<WaterQualityData> secondHalf = windowData.stream()
                .filter(d -> d.getTimestamp().isAfter(mid)).collect(Collectors.toList());

        if (firstHalf.isEmpty() || secondHalf.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("trend", "uneven_distribution");
            return result;
        }

        double firstAvg = firstHalf.stream()
                .mapToDouble(d -> d.getFinalScore() != null ? d.getFinalScore().doubleValue() : 0.0)
                .average().orElse(0.0);
        double secondAvg = secondHalf.stream()
                .mapToDouble(d -> d.getFinalScore() != null ? d.getFinalScore().doubleValue() : 0.0)
                .average().orElse(0.0);

        double changeRate = firstAvg > 0 ? (secondAvg - firstAvg) / firstAvg : 0.0;

        String trend;
        int riskLevel;
        if (changeRate > 0.5) {
            trend = "rapidly_deteriorating";
            riskLevel = 3;
        } else if (changeRate > 0.2) {
            trend = "deteriorating";
            riskLevel = 2;
        } else if (changeRate < -0.3) {
            trend = "improving";
            riskLevel = 0;
        } else {
            trend = "stable";
            riskLevel = 1;
        }

        // 检测突变点（连续两点间变化超过阈值）
        List<Map<String, Object>> anomalies = new ArrayList<>();
        for (int i = 1; i < windowData.size(); i++) {
            double prev = windowData.get(i - 1).getFinalScore() != null ?
                    windowData.get(i - 1).getFinalScore().doubleValue() : 0.0;
            double curr = windowData.get(i).getFinalScore() != null ?
                    windowData.get(i).getFinalScore().doubleValue() : 0.0;
            if (Math.abs(curr - prev) > 0.3) {
                Map<String, Object> anomaly = new HashMap<>();
                anomaly.put("timestamp", windowData.get(i).getTimestamp().toString());
                anomaly.put("prevScore", prev);
                anomaly.put("currScore", curr);
                anomaly.put("jump", curr - prev);
                anomalies.add(anomaly);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("pointId", pointId);
        result.put("windowMinutes", windowMinutes);
        result.put("dataPoints", windowData.size());
        result.put("firstHalfAvg", Math.round(firstAvg * 1000.0) / 1000.0);
        result.put("secondHalfAvg", Math.round(secondAvg * 1000.0) / 1000.0);
        result.put("changeRate", Math.round(changeRate * 1000.0) / 1000.0);
        result.put("trend", trend);
        result.put("riskLevel", riskLevel);
        result.put("anomalyPoints", anomalies);
        return result;
    }

    /**
     * 与历史基线对比，检测当前是否显著偏离
     */
    public Map<String, Object> compareWithHistory(Long pointId, double currentScore,
                                                    double currentTurbidity, double currentCod, double currentPh) {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> baseline = waterQualityDataMapper.selectBaseline(
                pointId, now.minusDays(30), now);

        double baselineAvg = 0.0;
        double baselineStd = 0.0;
        if (baseline != null && baseline.get("baseline") != null) {
            Number baselineNum = (Number) baseline.get("baseline");
            baselineAvg = baselineNum != null ? baselineNum.doubleValue() : 0.0;
        }
        if (baseline != null && baseline.get("stddev") != null) {
            Number stddevNum = (Number) baseline.get("stddev");
            baselineStd = stddevNum != null ? stddevNum.doubleValue() : 0.0;
        }

        // Z-score 偏离度
        double deviation;
        if (baselineStd > 0.01) {
            deviation = (currentScore - baselineAvg) / baselineStd;
        } else {
            deviation = currentScore - baselineAvg > 0.3 ? 3.0 : 0.0;
        }

        String level;
        if (deviation > 3.0) {
            level = "critical_deviation";
        } else if (deviation > 2.0) {
            level = "significant_deviation";
        } else if (deviation > 1.0) {
            level = "slight_deviation";
        } else {
            level = "normal";
        }

        Map<String, Object> result = new HashMap<>();
        result.put("pointId", pointId);
        result.put("baselineAvg", Math.round(baselineAvg * 1000.0) / 1000.0);
        result.put("baselineStd", Math.round(baselineStd * 1000.0) / 1000.0);
        result.put("currentScore", currentScore);
        result.put("deviation", Math.round(deviation * 100.0) / 100.0);
        result.put("level", level);
        result.put("currentTurbidity", currentTurbidity);
        result.put("currentCod", currentCod);
        result.put("currentPh", currentPh);
        return result;
    }

    /**
     * 基于近期趋势预测未来水质等级
     * 使用简单线性回归对最近N个数据点进行拟合
     */
    public Map<String, Object> predictWaterQuality(Long pointId, int predictMinutesAhead) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusHours(2);
        List<WaterQualityData> recentData = waterQualityDataMapper
                .selectWindowByPoint(pointId, start, end);

        if (recentData.size() < 5) {
            Map<String, Object> result = new HashMap<>();
            result.put("prediction", "insufficient_data");
            result.put("predictedLevel", -1);
            result.put("confidence", 0.0);
            return result;
        }

        int n = recentData.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            double x = i;
            double y = recentData.get(i).getFinalScore() != null ?
                    recentData.get(i).getFinalScore().doubleValue() : 0.0;
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }

        double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        double intercept = (sumY - slope * sumX) / n;

        // 预测未来时点的分数
        long avgIntervalMs = 0;
        if (n >= 2) {
            long first = recentData.get(0).getTimestamp()
                    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            long last = recentData.get(n - 1).getTimestamp()
                    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            avgIntervalMs = (last - first) / (n - 1);
        }
        int steps = (int) ((predictMinutesAhead * 60000L) / Math.max(avgIntervalMs, 60000));
        double predictedScore = intercept + slope * (n + steps - 1);
        predictedScore = Math.max(0.0, Math.min(1.0, predictedScore));

        int predictedLevel = determineAlertLevel(predictedScore);

        // R² 拟合度
        double yMean = sumY / n;
        double ssTot = 0, ssRes = 0;
        for (int i = 0; i < n; i++) {
            double y = recentData.get(i).getFinalScore() != null ?
                    recentData.get(i).getFinalScore().doubleValue() : 0.0;
            double yPred = intercept + slope * i;
            ssTot += (y - yMean) * (y - yMean);
            ssRes += (y - yPred) * (y - yPred);
        }
        double rSquared = ssTot > 0 ? 1 - ssRes / ssTot : 0;

        Map<String, Object> result = new HashMap<>();
        result.put("pointId", pointId);
        result.put("predictMinutesAhead", predictMinutesAhead);
        result.put("predictedScore", Math.round(predictedScore * 1000.0) / 1000.0);
        result.put("predictedLevel", predictedLevel);
        result.put("predictedLevelName", AlertLevel.nameOf(predictedLevel));
        result.put("confidence", Math.round(rSquared * 100.0) / 100.0);
        result.put("trendDirection", slope > 0.02 ? "rising" : slope < -0.02 ? "falling" : "stable");
        result.put("slope", Math.round(slope * 10000.0) / 10000.0);
        result.put("dataPointsUsed", n);
        return result;
    }

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

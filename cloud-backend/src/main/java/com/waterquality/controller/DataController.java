package com.waterquality.controller;

import com.waterquality.dto.AnalysisResult;
import com.waterquality.dto.AlertResult;
import com.waterquality.dto.Result;
import com.waterquality.service.IntelligentAnalysisService;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.Map;

@RestController
@RequestMapping("/api/data")
public class DataController {

    private final IntelligentAnalysisService intelligentAnalysisService;

    public DataController(IntelligentAnalysisService intelligentAnalysisService) {
        this.intelligentAnalysisService = intelligentAnalysisService;
    }

    @PostMapping("/report")
    public Result<AlertResult> reportData(@Valid @RequestBody AnalysisResult result,
                                           HttpServletRequest request) {
        AlertResult alertResult = intelligentAnalysisService.processAnalysisResult(result);
        return Result.success(alertResult);
    }

    @PostMapping("/heartbeat")
    public Result<Void> heartbeat(@RequestParam String deviceId) {
        return Result.success(null);
    }

    /**
     * 滑动窗口异常趋势检测
     */
    @GetMapping("/analysis/trend/{pointId}")
    public Result<Map<String, Object>> trendAnalysis(
            @PathVariable Long pointId,
            @RequestParam(defaultValue = "30") int windowMinutes) {
        Map<String, Object> trend = intelligentAnalysisService.detectTrendAnomaly(pointId, windowMinutes);
        return Result.success(trend);
    }

    /**
     * 与历史基线对比
     */
    @GetMapping("/analysis/compare/{pointId}")
    public Result<Map<String, Object>> historyCompare(
            @PathVariable Long pointId,
            @RequestParam(defaultValue = "0") double currentScore,
            @RequestParam(defaultValue = "0") double turbidity,
            @RequestParam(defaultValue = "0") double cod,
            @RequestParam(defaultValue = "7") double ph) {
        Map<String, Object> comparison = intelligentAnalysisService.compareWithHistory(
                pointId, currentScore, turbidity, cod, ph);
        return Result.success(comparison);
    }

    /**
     * 水质预测
     */
    @GetMapping("/analysis/predict/{pointId}")
    public Result<Map<String, Object>> predictQuality(
            @PathVariable Long pointId,
            @RequestParam(defaultValue = "30") int predictMinutesAhead) {
        Map<String, Object> prediction = intelligentAnalysisService.predictWaterQuality(
                pointId, predictMinutesAhead);
        return Result.success(prediction);
    }

    /**
     * 云端融合验证
     */
    @PostMapping("/analysis/verify-fusion")
    public Result<Map<String, Object>> verifyFusion(
            @RequestParam double imageScore,
            @RequestParam double sensorScore) {
        double finalScore = intelligentAnalysisService.verifyFusionScore(imageScore, sensorScore);
        int level = intelligentAnalysisService.determineAlertLevel(finalScore);
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("finalScore", finalScore);
        result.put("alertLevel", level);
        return Result.success(result);
    }
}

package com.waterquality.controller;

import com.waterquality.dto.QueryParams;
import com.waterquality.dto.Result;
import com.waterquality.entity.WaterQualityData;
import com.waterquality.service.VisualizationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/visualization")
public class VisualizationController {

    private final VisualizationService visualizationService;

    public VisualizationController(VisualizationService visualizationService) {
        this.visualizationService = visualizationService;
    }

    @PostMapping("/trend")
    public Result<List<Map<String, Object>>> getTrendData(@RequestBody QueryParams params) {
        List<Map<String, Object>> trendData = visualizationService.getTrendData(params);
        return Result.success(trendData, (long) trendData.size());
    }

    @PostMapping("/heatmap")
    public Result<List<Map<String, Object>>> getHeatmapData(@RequestBody QueryParams params) {
        List<Map<String, Object>> heatmapData = visualizationService.getHeatmapData(
                params.getPointIds(), params.getStartTime(), params.getEndTime());
        return Result.success(heatmapData, (long) heatmapData.size());
    }

    @PostMapping("/query")
    public Result<List<WaterQualityData>> queryData(@RequestBody QueryParams params) {
        List<WaterQualityData> dataList = visualizationService.queryData(params);
        return Result.success(dataList, (long) dataList.size());
    }

    /**
     * 缓存统计信息
     */
    @GetMapping("/cache-stats")
    public Result<Map<String, Object>> getCacheStats() {
        return Result.success(visualizationService.getCacheStats());
    }

    /**
     * 缓存预热（手动触发）
     */
    @PostMapping("/cache-warmup")
    public Result<Void> warmUpCache(@RequestBody List<Long> pointIds) {
        visualizationService.warmUpCache(pointIds);
        return Result.success(null);
    }
}

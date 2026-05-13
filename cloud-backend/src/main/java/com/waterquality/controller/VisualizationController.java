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

    /**
     * 获取水质趋势曲线数据
     */
    @PostMapping("/trend")
    public Result<List<Map<String, Object>>> getTrendData(@RequestBody QueryParams params) {
        List<Map<String, Object>> trendData = visualizationService.getTrendData(params);
        return Result.success(trendData, (long) trendData.size());
    }

    /**
     * 获取热力图数据
     */
    @PostMapping("/heatmap")
    public Result<List<Map<String, Object>>> getHeatmapData(@RequestBody QueryParams params) {
        List<Map<String, Object>> heatmapData = visualizationService.getHeatmapData(
                params.getPointIds(), params.getStartTime(), params.getEndTime());
        return Result.success(heatmapData, (long) heatmapData.size());
    }

    /**
     * 分页查询水质历史数据
     */
    @PostMapping("/query")
    public Result<List<WaterQualityData>> queryData(@RequestBody QueryParams params) {
        List<WaterQualityData> dataList = visualizationService.queryData(params);
        return Result.success(dataList, (long) dataList.size());
    }
}

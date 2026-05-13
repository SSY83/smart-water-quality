package com.waterquality.service;

import cn.hutool.core.util.StrUtil;
import com.waterquality.dto.QueryParams;
import com.waterquality.entity.WaterQualityData;
import com.waterquality.mapper.WaterQualityDataMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class VisualizationService {

    private final WaterQualityDataMapper waterQualityDataMapper;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${cache.query-ttl:300}")
    private int queryCacheTtl;

    public VisualizationService(WaterQualityDataMapper waterQualityDataMapper) {
        this.waterQualityDataMapper = waterQualityDataMapper;
    }

    /**
     * 查询水质趋势数据（用于ECharts折线图）
     */
    public List<Map<String, Object>> getTrendData(QueryParams params) {
        if (!params.validate()) {
            return Collections.emptyList();
        }

        String cacheKey = buildCacheKey(params);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cached = redisTemplate != null ?
                (List<Map<String, Object>>) redisTemplate.opsForValue().get(cacheKey) : null;
        if (cached != null) {
            return cached;
        }

        List<Long> pointIds = params.getPointIds();
        if (pointIds == null || pointIds.isEmpty()) {
            return Collections.emptyList();
        }

        int offset = (params.getPage() - 1) * params.getPageSize();
        List<WaterQualityData> dataList = waterQualityDataMapper.selectByPointsAndTimeRange(
                pointIds,
                params.getStartTime() != null ? params.getStartTime() : LocalDateTime.now().minusDays(7),
                params.getEndTime() != null ? params.getEndTime() : LocalDateTime.now(),
                offset,
                params.getPageSize());

        List<Map<String, Object>> trendData = new ArrayList<>();
        for (WaterQualityData data : dataList) {
            Map<String, Object> point = new HashMap<>();
            point.put("timestamp", data.getTimestamp().toString());
            point.put("pointId", data.getPointId());
            point.put("turbidityNtu", data.getTurbidityNtu());
            point.put("codValue", data.getCodValue());
            point.put("phValue", data.getPhValue());
            point.put("alertLevel", data.getAlertLevel());
            point.put("turbidityLevel", data.getTurbidityLevel());
            trendData.add(point);
        }

        if (redisTemplate != null) {
            redisTemplate.opsForValue().set(cacheKey, trendData, queryCacheTtl, TimeUnit.SECONDS);
        }
        return trendData;
    }

    /**
     * 获取热力图数据（各监测点平均异常分数）
     */
    public List<Map<String, Object>> getHeatmapData(List<Long> pointIds,
                                                     LocalDateTime start, LocalDateTime end) {
        List<Map<String, Object>> heatmapData = new ArrayList<>();
        if (start == null) start = LocalDateTime.now().minusDays(7);
        if (end == null) end = LocalDateTime.now();

        for (Long pointId : pointIds) {
            Object avgData = waterQualityDataMapper.selectAvgByPoint(pointId, start, end);
            if (avgData != null) {
                Map<String, Object> point = new HashMap<>();
                point.put("pointId", pointId);
                point.put("avgAlert", avgData);
                heatmapData.add(point);
            }
        }
        return heatmapData;
    }

    /**
     * 查询分页水质数据
     */
    public List<WaterQualityData> queryData(QueryParams params) {
        if (!params.validate()) {
            return Collections.emptyList();
        }
        List<Long> pointIds = params.getPointIds();
        if (pointIds == null || pointIds.isEmpty()) {
            return Collections.emptyList();
        }
        int offset = (params.getPage() - 1) * params.getPageSize();
        return waterQualityDataMapper.selectByPointsAndTimeRange(
                pointIds,
                params.getStartTime() != null ? params.getStartTime() : LocalDateTime.now().minusDays(7),
                params.getEndTime() != null ? params.getEndTime() : LocalDateTime.now(),
                offset,
                params.getPageSize());
    }

    private String buildCacheKey(QueryParams params) {
        String pointIdsHash = params.getPointIds() != null ?
                String.join(",", params.getPointIds().stream().map(String::valueOf).toArray(String[]::new)) : "all";
        String start = params.getStartTime() != null ? params.getStartTime().toString() : "null";
        String end = params.getEndTime() != null ? params.getEndTime().toString() : "null";
        String dataType = StrUtil.nullToDefault(params.getDataType(), "all");
        return "query:" + pointIdsHash + ":" + start + ":" + end + ":" + dataType;
    }
}

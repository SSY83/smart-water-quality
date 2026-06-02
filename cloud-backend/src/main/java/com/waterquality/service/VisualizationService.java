package com.waterquality.service;

import cn.hutool.core.util.StrUtil;
import com.waterquality.dto.QueryParams;
import com.waterquality.entity.WaterQualityData;
import com.waterquality.mapper.WaterQualityDataMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class VisualizationService {

    private static final Logger log = LoggerFactory.getLogger(VisualizationService.class);

    private final WaterQualityDataMapper waterQualityDataMapper;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${cache.query-ttl:300}")
    private int queryCacheTtl;

    @Value("${cache.query-ttl-random-offset:60}")
    private int queryCacheTtlRandomOffset;

    @Value("${cache.null-value-ttl:60}")
    private int nullValueTtl;

    // 互斥锁，防止缓存击穿
    private final ConcurrentHashMap<String, ReentrantLock> keyLocks = new ConcurrentHashMap<>();

    private static final String NULL_VALUE_MARKER = "__NULL__";

    public VisualizationService(WaterQualityDataMapper waterQualityDataMapper) {
        this.waterQualityDataMapper = waterQualityDataMapper;
    }

    /**
     * 查询水质趋势数据（带缓存防护）
     */
    public List<Map<String, Object>> getTrendData(QueryParams params) {
        if (!params.validate()) {
            return Collections.emptyList();
        }

        String cacheKey = buildCacheKey(params);

        // 1. 尝试从缓存获取
        List<Map<String, Object>> cached = getFromCache(cacheKey);
        if (cached != null) {
            if (cached.isEmpty() || (cached.size() == 1 && NULL_VALUE_MARKER.equals(
                    cached.get(0).get("_marker")))) {
                return Collections.emptyList(); // 缓存的空值标记
            }
            return cached;
        }

        // 2. 互斥锁防击穿
        ReentrantLock lock = keyLocks.computeIfAbsent(cacheKey, k -> new ReentrantLock());
        lock.lock();
        try {
            // 双重检查
            cached = getFromCache(cacheKey);
            if (cached != null) {
                if (cached.isEmpty() || (cached.size() == 1 && NULL_VALUE_MARKER.equals(
                        cached.get(0).get("_marker")))) {
                    return Collections.emptyList();
                }
                return cached;
            }

            List<Long> pointIds = params.getPointIds();
            if (pointIds == null || pointIds.isEmpty()) {
                cacheNullValue(cacheKey);
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

            if (trendData.isEmpty()) {
                cacheNullValue(cacheKey);
            } else {
                setToCache(cacheKey, trendData);
            }
            return trendData;

        } finally {
            lock.unlock();
            keyLocks.remove(cacheKey);
        }
    }

    /**
     * 获取热力图数据
     */
    public List<Map<String, Object>> getHeatmapData(List<Long> pointIds,
                                                     LocalDateTime start, LocalDateTime end) {
        String cacheKey = "heatmap:" + String.join(",", pointIds.stream()
                .map(String::valueOf).toArray(String[]::new)) + ":" + start + ":" + end;

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cached = getFromCache(cacheKey);
        if (cached != null) return cached;

        List<Map<String, Object>> heatmapData = new ArrayList<>();
        if (start == null) start = LocalDateTime.now().minusDays(7);
        if (end == null) end = LocalDateTime.now();

        List<java.util.Map<String, Object>> aggregation =
                waterQualityDataMapper.selectAggregationByPoints(pointIds, start, end);

        for (java.util.Map<String, Object> row : aggregation) {
            Map<String, Object> point = new HashMap<>();
            point.put("pointId", row.get("point_id"));
            point.put("avgTurbidity", row.get("avg_turbidity"));
            point.put("avgCod", row.get("avg_cod"));
            point.put("avgPh", row.get("avg_ph"));
            point.put("avgAlert", row.get("avg_alert"));
            point.put("count", row.get("cnt"));
            heatmapData.add(point);
        }

        setToCache(cacheKey, heatmapData);
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

    /**
     * 缓存预热：预先加载热点数据到缓存
     */
    public void warmUpCache(List<Long> hotPointIds) {
        if (redisTemplate == null) return;
        log.info("开始缓存预热，热点监测点数: {}", hotPointIds.size());

        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(1);

        for (Long pointId : hotPointIds) {
            try {
                QueryParams params = new QueryParams();
                params.setPointIds(Collections.singletonList(pointId));
                params.setStartTime(start);
                params.setEndTime(end);
                params.setPage(1);
                params.setPageSize(20);
                getTrendData(params);
            } catch (Exception e) {
                log.warn("缓存预热失败: pointId={}", pointId, e);
            }
        }
        log.info("缓存预热完成");
    }

    /**
     * 缓存统计信息
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("redisEnabled", redisTemplate != null);
        stats.put("queryTtl", queryCacheTtl);
        stats.put("queryTtlRandomOffset", queryCacheTtlRandomOffset);
        stats.put("nullValueTtl", nullValueTtl);
        if (redisTemplate != null) {
            try {
                Long dbSize = redisTemplate.getConnectionFactory().getConnection().dbSize();
                stats.put("approximateKeys", dbSize);
            } catch (Exception e) {
                stats.put("approximateKeys", "unavailable");
            }
        }
        return stats;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getFromCache(String key) {
        if (redisTemplate == null) return null;
        try {
            return (List<Map<String, Object>>) redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("缓存读取失败: key={}", key, e);
            return null;
        }
    }

    private void setToCache(String key, List<Map<String, Object>> value) {
        if (redisTemplate == null) return;
        try {
            int ttl = queryCacheTtl + new Random().nextInt(queryCacheTtlRandomOffset + 1);
            redisTemplate.opsForValue().set(key, value, ttl, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("缓存写入失败: key={}", key, e);
        }
    }

    /**
     * 缓存空值防止穿透
     */
    private void cacheNullValue(String key) {
        if (redisTemplate == null) return;
        try {
            Map<String, Object> nullMarker = new HashMap<>();
            nullMarker.put("_marker", NULL_VALUE_MARKER);
            List<Map<String, Object>> nullList = Collections.singletonList(nullMarker);
            redisTemplate.opsForValue().set(key, nullList, nullValueTtl, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("空值缓存写入失败: key={}", key, e);
        }
    }

    private String buildCacheKey(QueryParams params) {
        String pointIdsHash = params.getPointIds() != null ?
                String.join(",", params.getPointIds().stream().map(String::valueOf)
                        .toArray(String[]::new)) : "all";
        String start = params.getStartTime() != null ? params.getStartTime().toString() : "null";
        String end = params.getEndTime() != null ? params.getEndTime().toString() : "null";
        String dataType = StrUtil.nullToDefault(params.getDataType(), "all");
        return "query:" + pointIdsHash + ":" + start + ":" + end + ":" + dataType;
    }
}

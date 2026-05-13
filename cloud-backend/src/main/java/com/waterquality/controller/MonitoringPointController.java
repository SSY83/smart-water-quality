package com.waterquality.controller;

import com.waterquality.dto.Result;
import com.waterquality.entity.MonitoringPoint;
import com.waterquality.mapper.MonitoringPointMapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/monitoring-points")
public class MonitoringPointController {

    private final MonitoringPointMapper monitoringPointMapper;

    public MonitoringPointController(MonitoringPointMapper monitoringPointMapper) {
        this.monitoringPointMapper = monitoringPointMapper;
    }

    /**
     * 获取所有监测点
     */
    @GetMapping
    public Result<List<MonitoringPoint>> listAll() {
        List<MonitoringPoint> points = monitoringPointMapper.selectList(null);
        return Result.success(points, (long) points.size());
    }

    /**
     * 获取单个监测点详情
     */
    @GetMapping("/{id}")
    public Result<MonitoringPoint> getById(@PathVariable Long id) {
        MonitoringPoint point = monitoringPointMapper.selectById(id);
        if (point == null) {
            return Result.error("1004", "监测点不存在");
        }
        return Result.success(point);
    }

    /**
     * 获取在线监测点
     */
    @GetMapping("/online")
    public Result<List<MonitoringPoint>> listOnline() {
        List<MonitoringPoint> all = monitoringPointMapper.selectList(null);
        List<MonitoringPoint> online = all.stream()
                .filter(MonitoringPoint::isOnline)
                .collect(Collectors.toList());
        return Result.success(online, (long) online.size());
    }
}

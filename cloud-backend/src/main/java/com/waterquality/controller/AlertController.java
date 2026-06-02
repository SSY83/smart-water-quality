package com.waterquality.controller;

import com.waterquality.dto.Result;
import com.waterquality.entity.AlertRecord;
import com.waterquality.mapper.AlertRecordMapper;
import com.waterquality.service.AlertPushService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertRecordMapper alertRecordMapper;
    private final AlertPushService alertPushService;

    public AlertController(AlertRecordMapper alertRecordMapper,
                           AlertPushService alertPushService) {
        this.alertRecordMapper = alertRecordMapper;
        this.alertPushService = alertPushService;
    }

    @GetMapping("/point/{pointId}")
    public Result<List<AlertRecord>> getAlertsByPoint(
            @PathVariable Long pointId,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "50") int limit) {
        LocalDateTime start = startTime != null ?
                LocalDateTime.parse(startTime) : LocalDateTime.now().minusDays(7);
        LocalDateTime end = endTime != null ?
                LocalDateTime.parse(endTime) : LocalDateTime.now();
        List<AlertRecord> alerts = alertRecordMapper.selectByPointAndTimeRange(pointId, start, end, limit);
        return Result.success(alerts, (long) alerts.size());
    }

    @PostMapping("/{alertId}/confirm")
    public Result<Void> confirmAlert(@PathVariable Long alertId,
                                      @RequestAttribute(required = false) Long userId) {
        AlertRecord record = alertRecordMapper.selectById(alertId);
        if (record == null) {
            return Result.error("1004", "告警记录不存在");
        }
        record.setPushStatus("confirmed");
        record.setConfirmTime(LocalDateTime.now());
        if (userId != null) {
            record.setConfirmedBy(userId);
        }
        alertRecordMapper.updateById(record);
        return Result.success(null);
    }

    /**
     * 解除/关闭告警
     */
    @PostMapping("/{alertId}/dismiss")
    public Result<Void> dismissAlert(@PathVariable Long alertId) {
        AlertRecord record = alertRecordMapper.selectById(alertId);
        if (record == null) {
            return Result.error("1004", "告警记录不存在");
        }
        record.setPushStatus("dismissed");
        alertRecordMapper.updateById(record);
        return Result.success(null);
    }

    /**
     * 多参数联合告警规则评估
     */
    @GetMapping("/evaluate-combined")
    public Result<Map<String, Object>> evaluateCombined(
            @RequestParam double turbidity,
            @RequestParam double cod,
            @RequestParam double ph) {
        int combinedLevel = alertPushService.evaluateCombinedAlert(turbidity, cod, ph);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("turbidity", turbidity);
        result.put("cod", cod);
        result.put("ph", ph);
        result.put("combinedLevel", combinedLevel);
        result.put("levelName", com.waterquality.enums.AlertLevel.nameOf(combinedLevel));
        return Result.success(result);
    }

    /**
     * 告警统计报表 (多点聚合)
     */
    @PostMapping("/statistics")
    public Result<Map<String, Object>> alertStatistics(
            @RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Number> pointIdsRaw = (List<Number>) request.get("pointIds");
        List<Long> pointIds = new ArrayList<>();
        if (pointIdsRaw != null) {
            for (Number n : pointIdsRaw) {
                pointIds.add(n.longValue());
            }
        }

        LocalDateTime start = request.containsKey("startTime") ?
                LocalDateTime.parse(request.get("startTime").toString()) :
                LocalDateTime.now().minusDays(7);
        LocalDateTime end = request.containsKey("endTime") ?
                LocalDateTime.parse(request.get("endTime").toString()) :
                LocalDateTime.now();

        Map<String, Object> stats = alertPushService.getAlertStatistics(pointIds, start, end);
        return Result.success(stats);
    }

    /**
     * 单监测点告警统计
     */
    @GetMapping("/stats/{pointId}")
    public Result<Map<String, Object>> pointAlertStats(
            @PathVariable Long pointId,
            @RequestParam(defaultValue = "7") int days) {
        Map<String, Object> stats = alertPushService.getPointAlertStats(pointId, days);
        return Result.success(stats);
    }

    @GetMapping("/pending-retry")
    public Result<List<AlertRecord>> getPendingRetry(@RequestParam(defaultValue = "50") int limit) {
        List<AlertRecord> pending = alertRecordMapper.selectPendingRetry(limit);
        return Result.success(pending, (long) pending.size());
    }
}

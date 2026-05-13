package com.waterquality.controller;

import com.waterquality.dto.Result;
import com.waterquality.entity.AlertRecord;
import com.waterquality.mapper.AlertRecordMapper;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertRecordMapper alertRecordMapper;

    public AlertController(AlertRecordMapper alertRecordMapper) {
        this.alertRecordMapper = alertRecordMapper;
    }

    /**
     * 查询某个监测点的历史告警
     */
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

    /**
     * 确认告警
     */
    @PostMapping("/{alertId}/confirm")
    public Result<Void> confirmAlert(@PathVariable Long alertId,
                                      @RequestAttribute Long userId) {
        AlertRecord record = alertRecordMapper.selectById(alertId);
        if (record == null) {
            return Result.error("1004", "告警记录不存在");
        }
        record.setPushStatus("confirmed");
        record.setConfirmTime(LocalDateTime.now());
        record.setConfirmedBy(userId);
        alertRecordMapper.updateById(record);
        return Result.success(null);
    }

    /**
     * 查询待重试的告警
     */
    @GetMapping("/pending-retry")
    public Result<List<AlertRecord>> getPendingRetry(@RequestParam(defaultValue = "50") int limit) {
        List<AlertRecord> pending = alertRecordMapper.selectPendingRetry(limit);
        return Result.success(pending, (long) pending.size());
    }
}

package com.waterquality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("alert_record")
public class AlertRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long pointId;
    private Integer alertLevel;
    private String alertType;
    private String details;
    private String pushStatus;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private LocalDateTime createTime;
    private LocalDateTime confirmTime;
    private Long confirmedBy;

    public String getAlertLevelDescription() {
        if (alertLevel == null) return "未知";
        switch (alertLevel) {
            case 1: return "轻度异常";
            case 2: return "中度异常";
            case 3: return "重度异常";
            default: return "未知";
        }
    }
}

package com.waterquality.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("monitoring_point")
public class MonitoringPoint extends BaseEntity {
    private String name;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String deviceId;
    private Integer status;
    private LocalDateTime lastOnlineTime;
    private String locationDesc;
    private String contactPhone;

    public boolean isOnline() {
        if (lastOnlineTime == null) {
            return false;
        }
        return java.time.Duration.between(lastOnlineTime, LocalDateTime.now()).toMinutes() < 5;
    }
}

package com.waterquality.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("edge_device")
public class EdgeDevice {
    private Long id;
    private String deviceSn;
    private String deviceType;
    private String firmwareVersion;
    private LocalDateTime lastHeartbeat;
    private Integer storageUsageMb;
    private String ipAddress;
    private Long pointId;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

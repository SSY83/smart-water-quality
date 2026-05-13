package com.waterquality.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_config")
public class SysConfig {
    private Long id;
    private String configKey;
    private String configValue;
    private String description;
    private Integer isSensitive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

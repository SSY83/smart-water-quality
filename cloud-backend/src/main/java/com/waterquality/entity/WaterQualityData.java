package com.waterquality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("water_quality_data")
public class WaterQualityData {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long pointId;
    private LocalDateTime timestamp;
    private Integer turbidityLevel;
    private BigDecimal turbidityNtu;
    private BigDecimal codValue;
    private BigDecimal phValue;
    private String pollutionTypes;
    private Integer alertLevel;
    private BigDecimal confidence;
    private BigDecimal finalScore;
    private BigDecimal imageScore;
    private BigDecimal sensorScore;
    private String originalImageUrl;
    private LocalDateTime createdAt;
}

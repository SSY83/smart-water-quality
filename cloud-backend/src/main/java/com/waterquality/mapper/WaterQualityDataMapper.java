package com.waterquality.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.waterquality.entity.WaterQualityData;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface WaterQualityDataMapper extends BaseMapper<WaterQualityData> {

    @Select("SELECT * FROM water_quality_data WHERE point_id = #{pointId} " +
            "AND timestamp BETWEEN #{start} AND #{end} ORDER BY timestamp ASC LIMIT 1000")
    List<WaterQualityData> selectByPointAndTimeRange(
            @Param("pointId") Long pointId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Select("<script>SELECT * FROM water_quality_data WHERE point_id IN (" +
            "<foreach collection='pointIds' item='id' separator=','>#{id}</foreach>) " +
            "AND timestamp BETWEEN #{start} AND #{end} ORDER BY timestamp ASC " +
            "LIMIT #{offset}, #{limit}</script>")
    List<WaterQualityData> selectByPointsAndTimeRange(
            @Param("pointIds") List<Long> pointIds,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("offset") int offset,
            @Param("limit") int limit);

    @Select("SELECT AVG(alert_level) as avg_alert, AVG(turbidity_ntu) as avg_turbidity " +
            "FROM water_quality_data WHERE point_id = #{pointId} " +
            "AND timestamp BETWEEN #{start} AND #{end}")
    Object selectAvgByPoint(@Param("pointId") Long pointId,
                            @Param("start") LocalDateTime start,
                            @Param("end") LocalDateTime end);

    @Select("SELECT * FROM water_quality_data WHERE point_id = #{pointId} " +
            "AND timestamp BETWEEN #{start} AND #{end} ORDER BY timestamp ASC")
    List<WaterQualityData> selectWindowByPoint(
            @Param("pointId") Long pointId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    List<java.util.Map<String, Object>> selectAggregationByPoints(
            @Param("pointIds") List<Long> pointIds,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Select("SELECT AVG(alert_level) as baseline, STDDEV(alert_level) as stddev " +
            "FROM water_quality_data WHERE point_id = #{pointId} " +
            "AND timestamp BETWEEN #{start} AND #{end}")
    java.util.Map<String, Object> selectBaseline(
            @Param("pointId") Long pointId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Insert("<script>" +
            "INSERT INTO water_quality_data (point_id, timestamp, turbidity_level, " +
            "turbidity_ntu, cod_value, ph_value, pollution_types, alert_level, " +
            "confidence, final_score, image_score, sensor_score, created_at) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.pointId}, #{item.timestamp}, #{item.turbidityLevel}, " +
            "#{item.turbidityNtu}, #{item.codValue}, #{item.phValue}, #{item.pollutionTypes}, " +
            "#{item.alertLevel}, #{item.confidence}, #{item.finalScore}, " +
            "#{item.imageScore}, #{item.sensorScore}, #{item.createdAt})" +
            "</foreach></script>")
    int batchInsert(@Param("list") List<WaterQualityData> list);
}

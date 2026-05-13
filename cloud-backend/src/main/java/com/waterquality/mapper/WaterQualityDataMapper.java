package com.waterquality.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.waterquality.entity.WaterQualityData;
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
}

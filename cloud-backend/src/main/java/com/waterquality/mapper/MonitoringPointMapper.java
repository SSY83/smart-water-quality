package com.waterquality.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.waterquality.entity.MonitoringPoint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface MonitoringPointMapper extends BaseMapper<MonitoringPoint> {

    @Update("UPDATE monitoring_point SET status = 1, last_online_time = #{time} WHERE device_id = #{deviceId}")
    int updateOnline(@Param("deviceId") String deviceId, @Param("time") LocalDateTime time);

    @Update("UPDATE monitoring_point SET status = 0 WHERE last_online_time < #{threshold}")
    int markOfflineDevices(@Param("threshold") LocalDateTime threshold);

    @Select("SELECT * FROM monitoring_point WHERE device_id = #{deviceId}")
    MonitoringPoint selectByDeviceId(@Param("deviceId") String deviceId);
}

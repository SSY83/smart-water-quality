package com.waterquality.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.waterquality.entity.EdgeDevice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface EdgeDeviceMapper extends BaseMapper<EdgeDevice> {

    @Update("UPDATE edge_device SET last_heartbeat = NOW(), ip_address = #{ip}, status = 1 " +
            "WHERE device_sn = #{deviceSn}")
    int updateHeartbeat(@Param("deviceSn") String deviceSn, @Param("ip") String ip);

    @Update("UPDATE edge_device SET status = 0 WHERE last_heartbeat < #{threshold}")
    int markOfflineDevices(@Param("threshold") LocalDateTime threshold);

    @Select("SELECT * FROM edge_device WHERE device_sn = #{deviceSn}")
    EdgeDevice selectByDeviceSn(@Param("deviceSn") String deviceSn);
}

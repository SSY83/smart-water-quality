package com.waterquality.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.waterquality.entity.AlertRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AlertRecordMapper extends BaseMapper<AlertRecord> {

    @Select("SELECT * FROM alert_record WHERE point_id = #{pointId} " +
            "AND create_time BETWEEN #{start} AND #{end} ORDER BY create_time DESC LIMIT #{limit}")
    List<AlertRecord> selectByPointAndTimeRange(
            @Param("pointId") Long pointId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("limit") int limit);

    @Select("SELECT * FROM alert_record WHERE push_status = 'pending' " +
            "AND next_retry_time <= NOW() ORDER BY next_retry_time ASC LIMIT #{limit}")
    List<AlertRecord> selectPendingRetry(@Param("limit") int limit);

    @Update("UPDATE alert_record SET push_status = #{status}, retry_count = #{count}, " +
            "next_retry_time = #{nextRetry} WHERE id = #{id}")
    int updatePushStatus(@Param("id") Long id,
                         @Param("status") String status,
                         @Param("count") int count,
                         @Param("nextRetry") LocalDateTime nextRetry);

    @Select("SELECT COUNT(*) FROM alert_record WHERE push_status = 'sent' " +
            "AND create_time >= #{since}")
    long countSince(@Param("since") LocalDateTime since);
}

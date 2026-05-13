package com.waterquality.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.waterquality.entity.SysConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysConfigMapper extends BaseMapper<SysConfig> {

    @Select("SELECT config_value FROM sys_config WHERE config_key = #{key}")
    String selectValueByKey(@Param("key") String key);
}

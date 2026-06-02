package com.waterquality;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.waterquality.mapper")
@EnableAsync
@EnableScheduling
@EnableCaching
public class WaterQualityApplication {

    public static void main(String[] args) {
        SpringApplication.run(WaterQualityApplication.class, args);
    }
}

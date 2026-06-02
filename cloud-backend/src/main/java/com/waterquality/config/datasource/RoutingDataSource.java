package com.waterquality.config.datasource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.Map;

public class RoutingDataSource extends AbstractRoutingDataSource {

    private static final Logger log = LoggerFactory.getLogger(RoutingDataSource.class);

    public RoutingDataSource(DataSource writeDataSource, DataSource readDataSource) {
        Map<Object, Object> dataSources = new java.util.HashMap<>();
        dataSources.put(DataSourceType.READ_WRITE, writeDataSource);
        dataSources.put(DataSourceType.READ_ONLY, readDataSource);
        setTargetDataSources(dataSources);
        setDefaultTargetDataSource(writeDataSource);
        afterPropertiesSet();
    }

    @Override
    protected Object determineCurrentLookupKey() {
        DataSourceType type = DataSourceContextHolder.get();
        if (type == null) {
            return DataSourceType.READ_WRITE;
        }
        return type;
    }

    @Override
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
        log.info("读写分离数据源已初始化: 写库(默认) + 读库");
    }
}

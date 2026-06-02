-- H2 演示模式数据库初始化（零外部依赖，启动即用）
-- MySQL 方言 → H2 兼容语法

-- 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(200) NOT NULL,
    phone VARCHAR(20),
    email VARCHAR(100),
    role VARCHAR(20) NOT NULL DEFAULT 'readonly',
    status TINYINT NOT NULL DEFAULT 1,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (username)
);

-- 监测点表
CREATE TABLE IF NOT EXISTS monitoring_point (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    longitude DECIMAL(10,7) NOT NULL,
    latitude DECIMAL(10,7) NOT NULL,
    device_id VARCHAR(50),
    status TINYINT NOT NULL DEFAULT 0,
    last_online_time TIMESTAMP,
    location_desc VARCHAR(200),
    contact_phone VARCHAR(20),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (device_id)
);

-- 水质数据表
CREATE TABLE IF NOT EXISTS water_quality_data (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    point_id BIGINT NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    turbidity_level TINYINT NOT NULL DEFAULT 0,
    turbidity_ntu DECIMAL(10,2),
    cod_value DECIMAL(10,2),
    ph_value DECIMAL(10,2),
    pollution_types VARCHAR(500),
    alert_level INT DEFAULT 0,
    confidence DECIMAL(10,4),
    final_score DECIMAL(10,4),
    image_score DECIMAL(10,4),
    sensor_score DECIMAL(10,4),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_wq_point_time ON water_quality_data(point_id, timestamp);
CREATE INDEX IF NOT EXISTS idx_wq_point_alert ON water_quality_data(point_id, alert_level, final_score);

-- 告警记录表
CREATE TABLE IF NOT EXISTS alert_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    point_id BIGINT NOT NULL,
    alert_level INT NOT NULL,
    alert_type VARCHAR(100),
    details CLOB,
    push_status VARCHAR(20) DEFAULT 'pending',
    retry_count INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    confirm_time TIMESTAMP,
    confirmed_by BIGINT
);
CREATE INDEX IF NOT EXISTS idx_alert_point_time ON alert_record(point_id, create_time);
CREATE INDEX IF NOT EXISTS idx_alert_retry ON alert_record(push_status, create_time);

-- 权限关联表
CREATE TABLE IF NOT EXISTS user_point_permission (
    user_id BIGINT NOT NULL,
    point_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, point_id)
);

-- 边缘设备表
CREATE TABLE IF NOT EXISTS edge_device (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_sn VARCHAR(50) NOT NULL,
    device_type VARCHAR(50),
    firmware_version VARCHAR(20),
    last_heartbeat TIMESTAMP,
    point_id BIGINT,
    status TINYINT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (device_sn)
);
CREATE INDEX IF NOT EXISTS idx_device_heartbeat ON edge_device(status, last_heartbeat);

-- 系统配置表
CREATE TABLE IF NOT EXISTS sys_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(100) NOT NULL,
    config_value CLOB,
    is_sensitive TINYINT DEFAULT 0,
    UNIQUE (config_key)
);

-- 预置数据由 DemoDataInitializer.java 在启动时自动生成
-- 包括: 管理员账号、3个监测点、7天模拟水质数据

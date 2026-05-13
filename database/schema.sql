-- ============================================================
-- 智慧水利水质智能监测与预警系统 - 数据库初始化脚本
-- 版本: V2.0
-- 数据库: MySQL 8.0+
-- ============================================================

CREATE DATABASE IF NOT EXISTS smart_water_quality
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE smart_water_quality;

-- ============================================================
-- 1. 用户表 (sys_user)
-- ============================================================
CREATE TABLE sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    username VARCHAR(50) NOT NULL COMMENT '用户名',
    password VARCHAR(100) NOT NULL COMMENT 'BCrypt加密密码',
    phone VARCHAR(11) NULL COMMENT '手机号',
    email VARCHAR(100) NULL COMMENT '邮箱',
    role VARCHAR(20) NOT NULL DEFAULT 'readonly' COMMENT '角色: admin/user/readonly',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE INDEX uk_username (username),
    INDEX idx_phone (phone),
    INDEX idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- ============================================================
-- 2. 监测点配置表 (monitoring_point)
-- ============================================================
CREATE TABLE monitoring_point (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    name VARCHAR(100) NOT NULL COMMENT '监测点名称',
    longitude DECIMAL(10,7) NOT NULL COMMENT '经度',
    latitude DECIMAL(10,7) NOT NULL COMMENT '纬度',
    device_id VARCHAR(50) NULL COMMENT '关联设备序列号',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0-离线, 1-在线',
    last_online_time DATETIME NULL COMMENT '最后在线时间',
    location_desc VARCHAR(200) NULL COMMENT '地理位置描述',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX uk_device_id (device_id),
    INDEX idx_status (status),
    INDEX idx_location (longitude, latitude)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='监测点配置表';

-- ============================================================
-- 3. 水质数据表 (water_quality_data) - 按月分区
-- ============================================================
CREATE TABLE water_quality_data (
    id BIGINT AUTO_INCREMENT COMMENT '主键ID',
    point_id BIGINT NOT NULL COMMENT '监测点ID',
    timestamp DATETIME NOT NULL COMMENT '数据采集时间',
    turbidity_level TINYINT NOT NULL DEFAULT 0 COMMENT '浑浊度等级: 0-清晰,1-轻度,2-中度,3-重度',
    turbidity_ntu DECIMAL(10,2) NULL COMMENT '浊度值(NTU)',
    cod_value DECIMAL(10,2) NULL COMMENT 'COD数值(mg/L)',
    ph_value DECIMAL(3,2) NULL COMMENT 'pH数值',
    pollution_types JSON NULL COMMENT '污染物类型JSON',
    alert_level TINYINT NOT NULL DEFAULT 0 COMMENT '综合异常等级: 0-正常,1-轻度,2-中度,3-重度',
    confidence DECIMAL(4,3) NULL COMMENT '置信度(0-1)',
    final_score DECIMAL(4,3) NULL COMMENT '综合异常分数',
    image_score DECIMAL(4,3) NULL COMMENT '影像分析分数',
    sensor_score DECIMAL(4,3) NULL COMMENT '传感器分析分数',
    original_image_url VARCHAR(500) NULL COMMENT '原始图像存储路径',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    PRIMARY KEY (id, timestamp),
    INDEX idx_point_time (point_id, timestamp),
    INDEX idx_alert_level (alert_level),
    INDEX idx_timestamp (timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='水质分析数据表'
PARTITION BY RANGE (TO_DAYS(timestamp)) (
    PARTITION p202601 VALUES LESS THAN (TO_DAYS('2026-02-01')),
    PARTITION p202602 VALUES LESS THAN (TO_DAYS('2026-03-01')),
    PARTITION p202603 VALUES LESS THAN (TO_DAYS('2026-04-01')),
    PARTITION p202604 VALUES LESS THAN (TO_DAYS('2026-05-01')),
    PARTITION p202605 VALUES LESS THAN (TO_DAYS('2026-06-01')),
    PARTITION p202606 VALUES LESS THAN (TO_DAYS('2026-07-01')),
    PARTITION p202607 VALUES LESS THAN (TO_DAYS('2026-08-01')),
    PARTITION p202608 VALUES LESS THAN (TO_DAYS('2026-09-01')),
    PARTITION p202609 VALUES LESS THAN (TO_DAYS('2026-10-01')),
    PARTITION p202610 VALUES LESS THAN (TO_DAYS('2026-11-01')),
    PARTITION p202611 VALUES LESS THAN (TO_DAYS('2026-12-01')),
    PARTITION p202612 VALUES LESS THAN (TO_DAYS('2027-01-01')),
    PARTITION p_future VALUES LESS THAN MAXVALUE
);

-- ============================================================
-- 4. 告警记录表 (alert_record)
-- ============================================================
CREATE TABLE alert_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    point_id BIGINT NOT NULL COMMENT '监测点ID',
    alert_level TINYINT NOT NULL COMMENT '异常等级: 1-轻度,2-中度,3-重度',
    alert_type VARCHAR(50) NOT NULL COMMENT '告警类型: turbidity/cod/ph/combined',
    details TEXT NULL COMMENT '异常详情JSON',
    push_status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '推送状态: pending/sent/failed/confirmed',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '已重试次数',
    next_retry_time DATETIME NULL COMMENT '下次重试时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '告警时间',
    confirm_time DATETIME NULL COMMENT '确认时间',
    confirmed_by BIGINT NULL COMMENT '确认人ID',
    INDEX idx_point_alert_time (point_id, create_time),
    INDEX idx_push_status (push_status),
    INDEX idx_alert_level (alert_level),
    INDEX idx_next_retry (next_retry_time),
    CONSTRAINT fk_alert_point FOREIGN KEY (point_id)
        REFERENCES monitoring_point(id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_alert_user FOREIGN KEY (confirmed_by)
        REFERENCES sys_user(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警记录表';

-- ============================================================
-- 5. 用户-监测点权限表 (user_point_permission)
-- ============================================================
CREATE TABLE user_point_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    point_id BIGINT NOT NULL COMMENT '监测点ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX uk_user_point (user_id, point_id),
    CONSTRAINT fk_perm_user FOREIGN KEY (user_id)
        REFERENCES sys_user(id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_perm_point FOREIGN KEY (point_id)
        REFERENCES monitoring_point(id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-监测点权限关联表';

-- ============================================================
-- 6. 边缘端设备表 (edge_device)
-- ============================================================
CREATE TABLE edge_device (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    device_sn VARCHAR(64) NOT NULL COMMENT '设备序列号',
    device_type VARCHAR(20) NOT NULL COMMENT '设备类型: raspberry_pi/jetson_nano',
    firmware_version VARCHAR(20) NOT NULL DEFAULT '1.0.0' COMMENT '固件版本号',
    last_heartbeat DATETIME NULL COMMENT '最后心跳时间',
    storage_usage_mb INT DEFAULT 0 COMMENT '存储已使用量(MB)',
    ip_address VARCHAR(45) NULL COMMENT '设备IP地址',
    point_id BIGINT NULL COMMENT '关联监测点ID',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-离线,1-在线,2-故障',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX uk_device_sn (device_sn),
    INDEX idx_point_id (point_id),
    INDEX idx_last_heartbeat (last_heartbeat),
    CONSTRAINT fk_edge_device_point FOREIGN KEY (point_id)
        REFERENCES monitoring_point(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='边缘端设备表';

-- ============================================================
-- 7. 系统配置表 (sys_config)
-- ============================================================
CREATE TABLE sys_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    config_key VARCHAR(100) NOT NULL COMMENT '配置键',
    config_value TEXT NOT NULL COMMENT '配置值(JSON格式)',
    description VARCHAR(500) NULL COMMENT '配置说明',
    is_sensitive TINYINT NOT NULL DEFAULT 0 COMMENT '是否敏感配置',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX uk_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

-- ============================================================
-- 8. 告警确认记录表 (alert_confirmation)
-- ============================================================
CREATE TABLE alert_confirmation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    alert_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    confirm_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX uk_alert_user (alert_id, user_id),
    CONSTRAINT fk_conf_alert FOREIGN KEY (alert_id)
        REFERENCES alert_record(id) ON DELETE CASCADE,
    CONSTRAINT fk_conf_user FOREIGN KEY (user_id)
        REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警确认记录表';

-- ============================================================
-- 9. 预置配置数据
-- ============================================================
INSERT INTO sys_config (config_key, config_value, description) VALUES
('sms.api.url', '"https://api.smsprovider.com/v2/send"', '短信平台API地址'),
('sms.retry.max', '3', '短信发送最大重试次数'),
('alert.cache.days', '7', '异常告警本地缓存保留天数'),
('mqtt.qos', '1', 'MQTT消息QoS等级'),
('fusion.image_weight', '0.6', '多源融合影像权重'),
('fusion.sensor_weight', '0.4', '多源融合传感器权重'),
('fusion.time_window_ms', '500', '时间对齐窗口(毫秒)');

-- ============================================================
-- 10. 预置管理员用户 (密码: admin123, BCrypt加密)
-- ============================================================
INSERT INTO sys_user (username, password, phone, role, status) VALUES
('admin', '$2b$10$Z2uig.gev.AkzwL09kjwCOq0uimFWaSc2fn/.LsTU8aHyecjmSabG', '13800000000', 'admin', 1);

-- ============================================================
-- 11. 预置监测点数据
-- ============================================================
INSERT INTO monitoring_point (name, longitude, latitude, location_desc) VALUES
('钱塘江监测点A', 120.2123456, 30.2456789, '钱塘江上游-杭州段'),
('富春江监测点B', 119.9632100, 30.0543200, '富春江中游-桐庐段'),
('新安江监测点C', 119.2543000, 29.6123400, '新安江上游-淳安段');

-- ============================================================
-- 12. 预置边缘设备
-- ============================================================
INSERT INTO edge_device (device_sn, device_type, point_id, status) VALUES
('RPI4B-2026-001', 'raspberry_pi', 1, 0),
('JNANO-2026-001', 'jetson_nano', 2, 0);

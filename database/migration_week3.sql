-- ===================================================
-- Week 3 数据库迁移脚本
-- 兼容: MySQL 8.4
-- 用法: mysql -u root -p < database/migration_week3.sql
-- ===================================================

DELIMITER //

CREATE PROCEDURE IF NOT EXISTS add_index_if_missing(
    IN tbl VARCHAR(64), IN idx_name VARCHAR(64), IN idx_cols VARCHAR(256))
BEGIN
    DECLARE idx_count INT DEFAULT 0;
    SELECT COUNT(*) INTO idx_count FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = tbl
          AND index_name = idx_name;
    IF idx_count = 0 THEN
        SET @ddl = CONCAT('CREATE INDEX ', idx_name, ' ON ', tbl, '(', idx_cols, ')');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //

DELIMITER ;

-- 1. 水质数据表: 复合索引优化趋势查询
CALL add_index_if_missing('water_quality_data', 'idx_point_alert_score',
    'point_id, timestamp, alert_level, final_score');

-- 2. 告警记录表: 重试查询复合索引
CALL add_index_if_missing('alert_record', 'idx_retry_status_time',
    'push_status, next_retry_time');

-- 3. 边缘设备表: 心跳查询索引
CALL add_index_if_missing('edge_device', 'idx_device_status_heartbeat',
    'status, last_heartbeat');

-- 4. 告警记录表: 告警类型索引
CALL add_index_if_missing('alert_record', 'idx_alert_type', 'alert_type');

DROP PROCEDURE IF EXISTS add_index_if_missing;

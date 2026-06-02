package com.waterquality.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
@Profile("demo")
public class DemoDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);
    private static final Random RANDOM = new Random(42);
    private final JdbcTemplate jdbc;

    private static final String[] POINT_NAMES = {"钱塘江监测点A", "富春江监测点B", "新安江监测点C"};
    private static final String[] LOCATIONS = {"钱塘江上游-杭州段", "富春江中游-桐庐段", "新安江上游-淳安段"};
    private static final int DEMO_DAYS = 2; // 演示数据天数
    private static final int INTERVAL_MIN = 5; // 采样间隔（分钟）

    public DemoDataInitializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(String... args) {
        log.info("=== Demo mode: initializing data ===");

        createTables();
        insertAdminUser();
        insertMonitoringPoints();
        generateWaterQualityData();
        generateAlertRecords();

        log.info("=== Demo mode: initialization complete ===");
        log.info("Login: admin / admin123");
        log.info("H2 Console: http://localhost:8080/h2-console (URL: jdbc:h2:mem:smart_water_quality, user: sa)");
    }

    private void createTables() {
        log.info("Creating tables...");

        jdbc.execute("CREATE TABLE IF NOT EXISTS sys_user (" +
            "id BIGINT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(50) NOT NULL, " +
            "password VARCHAR(200) NOT NULL, phone VARCHAR(20), email VARCHAR(100), " +
            "role VARCHAR(20) NOT NULL DEFAULT 'readonly', status TINYINT NOT NULL DEFAULT 1, " +
            "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
            "CONSTRAINT uk_username UNIQUE (username))");

        jdbc.execute("CREATE TABLE IF NOT EXISTS monitoring_point (" +
            "id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL, " +
            "longitude DOUBLE NOT NULL, latitude DOUBLE NOT NULL, device_id VARCHAR(50), " +
            "status TINYINT NOT NULL DEFAULT 0, last_online_time TIMESTAMP, " +
            "location_desc VARCHAR(200), contact_phone VARCHAR(20), " +
            "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
            "CONSTRAINT uk_device_id UNIQUE (device_id))");

        jdbc.execute("CREATE TABLE IF NOT EXISTS water_quality_data (" +
            "id BIGINT AUTO_INCREMENT PRIMARY KEY, point_id BIGINT NOT NULL, " +
            "timestamp TIMESTAMP NOT NULL, turbidity_level TINYINT DEFAULT 0, " +
            "turbidity_ntu DECIMAL(10,2), cod_value DECIMAL(10,2), ph_value DECIMAL(10,2), " +
            "pollution_types VARCHAR(500), alert_level INT DEFAULT 0, " +
            "confidence DECIMAL(10,4), final_score DECIMAL(10,4), " +
            "image_score DECIMAL(10,4), sensor_score DECIMAL(10,4), " +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_wq_point_time ON water_quality_data(point_id, timestamp)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_wq_point_alert ON water_quality_data(point_id, alert_level, final_score)");

        jdbc.execute("CREATE TABLE IF NOT EXISTS alert_record (" +
            "id BIGINT AUTO_INCREMENT PRIMARY KEY, point_id BIGINT NOT NULL, " +
            "alert_level INT NOT NULL, alert_type VARCHAR(100), details CLOB, " +
            "push_status VARCHAR(20) DEFAULT 'pending', retry_count INT DEFAULT 0, " +
            "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
            "confirm_time TIMESTAMP, confirmed_by BIGINT)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_alert_point_time ON alert_record(point_id, create_time)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_alert_retry ON alert_record(push_status, create_time)");

        jdbc.execute("CREATE TABLE IF NOT EXISTS user_point_permission (" +
            "user_id BIGINT NOT NULL, point_id BIGINT NOT NULL, PRIMARY KEY (user_id, point_id))");

        jdbc.execute("CREATE TABLE IF NOT EXISTS edge_device (" +
            "id BIGINT AUTO_INCREMENT PRIMARY KEY, device_sn VARCHAR(50) NOT NULL, " +
            "device_type VARCHAR(50), firmware_version VARCHAR(20), last_heartbeat TIMESTAMP, " +
            "point_id BIGINT, status TINYINT DEFAULT 0, " +
            "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
            "CONSTRAINT uk_device_sn UNIQUE (device_sn))");

        jdbc.execute("CREATE TABLE IF NOT EXISTS sys_config (" +
            "id BIGINT AUTO_INCREMENT PRIMARY KEY, config_key VARCHAR(100) NOT NULL, " +
            "config_value CLOB, is_sensitive TINYINT DEFAULT 0, " +
            "CONSTRAINT uk_config_key UNIQUE (config_key))");

        log.info("Tables created (8 tables)");
    }

    private void insertAdminUser() {
        String hash = new BCryptPasswordEncoder().encode("admin123");
        jdbc.update("INSERT INTO sys_user (username, password, phone, email, role, status) VALUES (?,?,?,?,?,?)",
            "admin", hash, "13800000000", "admin@water.com", "admin", 1);
        log.info("Admin user: admin / admin123");
    }

    private void insertMonitoringPoints() {
        jdbc.update("INSERT INTO monitoring_point (id, name, longitude, latitude, device_id, status, location_desc, contact_phone) VALUES (?,?,?,?,?,?,?,?)",
            1, POINT_NAMES[0], 120.20523, 30.24385, "DEV-QT-001", 1, LOCATIONS[0], "13800000001");
        jdbc.update("INSERT INTO monitoring_point (id, name, longitude, latitude, device_id, status, location_desc, contact_phone) VALUES (?,?,?,?,?,?,?,?)",
            2, POINT_NAMES[1], 119.65283, 29.81367, "DEV-FC-001", 1, LOCATIONS[1], "13800000002");
        jdbc.update("INSERT INTO monitoring_point (id, name, longitude, latitude, device_id, status, location_desc, contact_phone) VALUES (?,?,?,?,?,?,?,?)",
            3, POINT_NAMES[2], 119.04125, 29.60873, "DEV-XA-001", 1, LOCATIONS[2], "13800000003");
        log.info("Monitoring points: 3 (钱塘江/富春江/新安江)");
    }

    private void generateWaterQualityData() {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(DEMO_DAYS);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String sql = "INSERT INTO water_quality_data (point_id, timestamp, turbidity_level, " +
            "turbidity_ntu, cod_value, ph_value, pollution_types, alert_level, " +
            "confidence, final_score, image_score, sensor_score) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";

        List<Object[]> batch = new ArrayList<>();
        int total = 0;
        int recordsPerPoint = DEMO_DAYS * 24 * 60 / INTERVAL_MIN;
        log.info("Generating ~{} records per point ({} days, {}min interval)...", recordsPerPoint, DEMO_DAYS, INTERVAL_MIN);

        for (int pointId = 1; pointId <= 3; pointId++) {
            LocalDateTime t = start;
            int count = 0;
            while (t.isBefore(end)) {
                double hourFactor = 1.0 + 0.3 * Math.sin((t.getHour() - 6) * Math.PI / 12);
                double weekdayFactor = 1.0 + 0.15 * (t.getDayOfWeek().getValue() <= 5 ? 1 : 0);
                double factor = hourFactor * weekdayFactor;

                double ph = 7.0 + RANDOM.nextGaussian() * 0.3;
                double turbidity = Math.abs(RANDOM.nextGaussian() * 3 + 2) * factor;
                double cod = Math.abs(RANDOM.nextGaussian() * 4 + 8) * factor;

                if (RANDOM.nextDouble() < 0.03) turbidity = 50 + RANDOM.nextDouble() * 100;
                if (RANDOM.nextDouble() < 0.05) cod = 30 + RANDOM.nextDouble() * 50;

                int turbidityLevel = turbidity >= 80 ? 3 : turbidity >= 30 ? 2 : turbidity >= 5 ? 1 : 0;
                int alertLevel = turbidity >= 80 || cod >= 50 ? 3 :
                    turbidity >= 30 || cod >= 30 || ph < 6.5 || ph > 8.5 ? 2 :
                    turbidity >= 5 || cod >= 15 ? 1 : 0;

                double imageScore = alertLevel >= 2 ? 0.6 + RANDOM.nextDouble() * 0.38 :
                    alertLevel == 1 ? 0.2 + RANDOM.nextDouble() * 0.35 :
                    0.01 + RANDOM.nextDouble() * 0.17;
                double sensorScore = Math.min(turbidity / 100, 1.0) * 0.4 +
                    Math.min(cod / 60, 1.0) * 0.35 +
                    Math.min(Math.abs(ph - 7.0) / 3.5, 1.0) * 0.25;
                double finalScore = imageScore * 0.6 + sensorScore * 0.4;
                double confidence = 0.75 + RANDOM.nextDouble() * 0.24;

                String pollutionTypes = alertLevel >= 3 ? "[\"sediment\",\"industrial_waste\"]" :
                    alertLevel == 2 ? "[\"algae\",\"sediment\"]" :
                    alertLevel == 1 ? "[\"algae\"]" : "[\"clear\"]";

                batch.add(new Object[]{pointId, t.format(fmt), turbidityLevel,
                    r(turbidity), r(cod), r(ph), pollutionTypes, alertLevel,
                    r(confidence), r(finalScore), r(imageScore), r(sensorScore)});

                if (batch.size() >= 500) {
                    jdbc.batchUpdate(sql, batch);
                    total += batch.size();
                    batch.clear();
                }
                t = t.plusMinutes(INTERVAL_MIN);
                count++;
            }
            log.info("  Point {}: {} records", pointId, count);
        }
        if (!batch.isEmpty()) {
            jdbc.batchUpdate(sql, batch);
            total += batch.size();
        }
        log.info("Water quality data: {} total records", total);
    }

    private void generateAlertRecords() {
        int count = jdbc.update(
            "INSERT INTO alert_record (point_id, alert_level, alert_type, details, push_status, retry_count, create_time, confirm_time) " +
            "SELECT point_id, alert_level, 'turbidity,cod', '{\"source\":\"demo\"}', " +
            "CASE WHEN alert_level >= 2 THEN 'sent' ELSE 'pending' END, 0, timestamp, " +
            "CASE WHEN alert_level >= 2 THEN DATEADD('MINUTE', 5, timestamp) ELSE NULL END " +
            "FROM water_quality_data WHERE alert_level >= 1");
        log.info("Alert records: {} generated", count);
    }

    private static BigDecimal r(double v) {
        return BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_UP);
    }
}

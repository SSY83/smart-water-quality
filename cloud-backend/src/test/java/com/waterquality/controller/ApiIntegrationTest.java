package com.waterquality.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waterquality.constant.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.profiles.active=local",
    "mqtt.broker-url=",
    "spring.autoconfigure.exclude=" +
        "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
        "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration"
})
@AutoConfigureMockMvc(addFilters = false)  // 跳过JWT过滤器以便测试
@DisplayName("API 集成测试")
class ApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ==================== Auth API ====================

    @Nested
    @DisplayName("认证接口 POST /api/auth/login")
    class AuthTests {

        @Test
        @DisplayName("正确用户名密码登录成功")
        void login_success() throws Exception {
            String body = "{\"username\":\"admin\",\"password\":\"123456\"}";
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS))
                    .andExpect(jsonPath("$.data.token").exists())
                    .andExpect(jsonPath("$.data.username").value("admin"));
        }

        @Test
        @DisplayName("错误密码登录失败")
        void login_wrongPassword_fails() throws Exception {
            String body = "{\"username\":\"admin\",\"password\":\"wrong\"}";
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED));
        }

        @Test
        @DisplayName("密码为空返回错误")
        void login_emptyPassword_fails() throws Exception {
            String body = "{\"username\":\"admin\",\"password\":\"\"}";
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    // ==================== Monitoring Points API ====================

    @Nested
    @DisplayName("监测点接口 GET /api/monitoring-points")
    class MonitoringPointTests {

        @Test
        @DisplayName("获取监测点列表")
        void getMonitoringPoints_returnsList() throws Exception {
            mockMvc.perform(get("/api/monitoring-points"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS))
                    .andExpect(jsonPath("$.data").isArray());
        }
    }

    // ==================== Data Report API ====================

    @Nested
    @DisplayName("数据上报接口 POST /api/data/report")
    class DataReportTests {

        @Test
        @DisplayName("上报正常水质数据")
        void reportData_normal() throws Exception {
            Map<String, Object> report = buildReport(1, 0, 3.0, 10.0, 7.1);
            mockMvc.perform(post("/api/data/report")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(report)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS));
        }

        @Test
        @DisplayName("上报轻度异常数据")
        void reportData_mild() throws Exception {
            Map<String, Object> report = buildReport(1, 1, 10.0, 20.0, 7.3);
            mockMvc.perform(post("/api/data/report")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(report)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS));
        }

        @Test
        @DisplayName("上报重度异常数据")
        void reportData_severe() throws Exception {
            Map<String, Object> report = buildReport(1, 3, 85.0, 60.0, 5.0);
            mockMvc.perform(post("/api/data/report")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(report)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS));
        }

        @Test
        @DisplayName("缺少pointId返回400")
        void reportData_missingPointId_fails() throws Exception {
            Map<String, Object> report = new HashMap<>();
            report.put("timestamp", LocalDateTime.now().toString());
            report.put("alertLevel", 0);
            mockMvc.perform(post("/api/data/report")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(report)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ==================== Visualization API ====================

    @Nested
    @DisplayName("可视化接口")
    class VisualizationTests {

        @Test
        @DisplayName("趋势查询: 正常参数返回数据")
        void trendQuery_validParams() throws Exception {
            Map<String, Object> params = new HashMap<>();
            params.put("pointIds", Arrays.asList(1, 2, 3));
            params.put("startTime", LocalDateTime.now().minusDays(1).toString());
            params.put("endTime", LocalDateTime.now().toString());
            params.put("page", 1);
            params.put("pageSize", 20);

            mockMvc.perform(post("/api/visualization/trend")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(params)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS));
        }

        @Test
        @DisplayName("热力图查询: 多点聚合")
        void heatmapQuery_multiplePoints() throws Exception {
            Map<String, Object> params = new HashMap<>();
            params.put("pointIds", Arrays.asList(1, 2, 3));
            params.put("startTime", LocalDateTime.now().minusDays(7).toString());
            params.put("endTime", LocalDateTime.now().toString());

            mockMvc.perform(post("/api/visualization/heatmap")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(params)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS));
        }

        @Test
        @DisplayName("分页查询: 默认参数")
        void pageQuery_defaultParams() throws Exception {
            Map<String, Object> params = new HashMap<>();
            params.put("pointIds", Arrays.asList(1));
            params.put("page", 1);
            params.put("pageSize", 10);

            mockMvc.perform(post("/api/visualization/query")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(params)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS));
        }

        @Test
        @DisplayName("分页查询: pageSize超过100拦截")
        void pageQuery_tooLarge_fails() throws Exception {
            Map<String, Object> params = new HashMap<>();
            params.put("pointIds", Arrays.asList(1));
            params.put("page", 1);
            params.put("pageSize", 200);

            mockMvc.perform(post("/api/visualization/query")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(params)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isEmpty());
        }
    }

    // ==================== Alert API ====================

    @Nested
    @DisplayName("告警接口")
    class AlertTests {

        @Test
        @DisplayName("查询监测点告警")
        void getAlertsByPoint_returnsList() throws Exception {
            mockMvc.perform(get("/api/alerts/point/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS));
        }

        @Test
        @DisplayName("联合规则评估")
        void evaluateCombinedAlert() throws Exception {
            mockMvc.perform(get("/api/alerts/evaluate-combined")
                    .param("turbidity", "85.0")
                    .param("cod", "55.0")
                    .param("ph", "5.0"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS))
                    .andExpect(jsonPath("$.data.combinedLevel").value(3));
        }

        @Test
        @DisplayName("告警统计")
        void alertStatistics() throws Exception {
            Map<String, Object> request = new HashMap<>();
            request.put("pointIds", Arrays.asList(1, 2, 3));
            request.put("startTime", LocalDateTime.now().minusDays(7).toString());
            request.put("endTime", LocalDateTime.now().toString());

            mockMvc.perform(post("/api/alerts/statistics")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS));
        }
    }

    // ==================== Enhanced Analysis API ====================

    @Nested
    @DisplayName("增强分析接口 (第2周新增)")
    class EnhancedAnalysisTests {

        @Test
        @DisplayName("融合验证接口")
        void verifyFusion() throws Exception {
            mockMvc.perform(post("/api/data/analysis/verify-fusion")
                    .param("imageScore", "0.8")
                    .param("sensorScore", "0.6"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS))
                    .andExpect(jsonPath("$.data.finalScore").exists())
                    .andExpect(jsonPath("$.data.alertLevel").exists());
        }
    }

    // ==================== Helpers ====================

    private Map<String, Object> buildReport(int pointId, int alertLevel,
                                             double turbidity, double cod, double ph) {
        Map<String, Object> report = new HashMap<>();
        report.put("pointId", String.valueOf(pointId));
        report.put("timestamp", LocalDateTime.now().toString());
        report.put("alertLevel", alertLevel);
        report.put("confidence", 0.85);
        report.put("finalScore", 0.45);
        report.put("imageScore", 0.55);
        report.put("sensorScore", 0.35);
        report.put("turbidityLevel", alertLevel);

        Map<String, Object> details = new HashMap<>();
        details.put("turbidity", turbidity);
        details.put("cod", cod);
        details.put("ph", ph);
        report.put("details", details);

        return report;
    }
}

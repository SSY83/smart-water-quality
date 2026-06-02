package com.waterquality.service;

import com.waterquality.dto.AnalysisResult;
import com.waterquality.enums.AlertLevel;
import com.waterquality.mapper.WaterQualityDataMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("智能分析服务测试")
class IntelligentAnalysisServiceTest {

    @Mock
    private WaterQualityDataMapper waterQualityDataMapper;

    @Mock
    private AlertPushService alertPushService;

    @Mock
    private DataCollectionService dataCollectionService;

    private IntelligentAnalysisService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new IntelligentAnalysisService(
                alertPushService, dataCollectionService, waterQualityDataMapper);
        // 注入@Value配置值（Mockito不会注入Spring @Value）
        setField(service, "imageWeight", 0.6);
        setField(service, "sensorWeight", 0.4);
        setField(service, "turbidityThreshold", 80.0);
        setField(service, "codThreshold", 50.0);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    @DisplayName("融合分数计算正确 (0.6*0.8 + 0.4*0.6 = 0.72)")
    void verifyFusionScore_correctWeighted() {
        double score = service.verifyFusionScore(0.8, 0.6);
        assertEquals(0.72, score, 0.001);
    }

    @Test
    @DisplayName("正常等级判定: finalScore < 0.4")
    void determineAlertLevel_normal() {
        assertEquals(AlertLevel.NORMAL.getLevelCode(), service.determineAlertLevel(0.0));
        assertEquals(AlertLevel.NORMAL.getLevelCode(), service.determineAlertLevel(0.39));
    }

    @Test
    @DisplayName("轻度异常: 0.4 <= finalScore < 0.7")
    void determineAlertLevel_mild() {
        assertEquals(AlertLevel.MILD.getLevelCode(), service.determineAlertLevel(0.4));
        assertEquals(AlertLevel.MILD.getLevelCode(), service.determineAlertLevel(0.69));
    }

    @Test
    @DisplayName("中度异常: 0.7 <= finalScore < 0.9")
    void determineAlertLevel_moderate() {
        assertEquals(AlertLevel.MODERATE.getLevelCode(), service.determineAlertLevel(0.7));
        assertEquals(AlertLevel.MODERATE.getLevelCode(), service.determineAlertLevel(0.89));
    }

    @Test
    @DisplayName("重度异常: finalScore >= 0.9")
    void determineAlertLevel_severe() {
        assertEquals(AlertLevel.SEVERE.getLevelCode(), service.determineAlertLevel(0.9));
        assertEquals(AlertLevel.SEVERE.getLevelCode(), service.determineAlertLevel(1.0));
    }

    @Test
    @DisplayName("规则引擎降级: 正常水质")
    void ruleBasedAnalysis_normal() {
        Map<String, Object> sensorData = new HashMap<>();
        sensorData.put("turbidity", 3.0);
        sensorData.put("cod", 10.0);
        sensorData.put("ph", 7.1);

        AnalysisResult result = service.ruleBasedAnalysis(1L, sensorData);
        assertEquals(AlertLevel.NORMAL.getLevelCode(), result.getAlertLevel());
    }

    @Test
    @DisplayName("规则引擎降级: 重度污染")
    void ruleBasedAnalysis_severe() {
        Map<String, Object> sensorData = new HashMap<>();
        sensorData.put("turbidity", 100.0);
        sensorData.put("cod", 60.0);
        sensorData.put("ph", 5.0);

        AnalysisResult result = service.ruleBasedAnalysis(1L, sensorData);
        assertEquals(AlertLevel.SEVERE.getLevelCode(), result.getAlertLevel());
    }

    @Test
    @DisplayName("规则引擎降级: pH异常合并高浊度")
    void ruleBasedAnalysis_phAnomaly() {
        Map<String, Object> sensorData = new HashMap<>();
        sensorData.put("turbidity", 85.0);
        sensorData.put("cod", 10.0);
        sensorData.put("ph", 4.0);  // 强酸 + 高浊度

        AnalysisResult result = service.ruleBasedAnalysis(1L, sensorData);
        assertTrue(result.getAlertLevel() >= AlertLevel.MILD.getLevelCode());
    }

    @Test
    @DisplayName("processAnalysisResult null输入抛出异常")
    void processAnalysisResult_nullInput() {
        assertThrows(IllegalArgumentException.class,
                () -> service.processAnalysisResult(null));
    }

    @Test
    @DisplayName("融合权重配置边界值测试")
    void fusionWeight_boundaryValues() {
        // 纯影像: 0.8
        double score1 = service.verifyFusionScore(0.8, 0.0);
        assertEquals(0.48, score1, 0.001);

        // 纯传感器: 0.6
        double score2 = service.verifyFusionScore(0.0, 0.6);
        assertEquals(0.24, score2, 0.001);

        // 极端值
        double score3 = service.verifyFusionScore(1.0, 1.0);
        assertEquals(1.0, score3, 0.001);
    }
}

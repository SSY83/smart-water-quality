package com.waterquality.service;

import com.waterquality.dto.AlertResult;
import com.waterquality.dto.AnalysisResult;
import com.waterquality.enums.AlertLevel;
import com.waterquality.mapper.AlertRecordMapper;
import com.waterquality.mapper.MonitoringPointMapper;
import com.waterquality.websocket.AlertWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("告警推送服务测试")
class AlertPushServiceTest {

    @Mock
    private AlertRecordMapper alertRecordMapper;

    @Mock
    private MonitoringPointMapper monitoringPointMapper;

    @Mock
    private SmsService smsService;

    @Mock
    private AlertWebSocketHandler webSocketHandler;

    @Mock
    private Executor retryExecutor;

    private AlertPushService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new AlertPushService(
                alertRecordMapper, monitoringPointMapper, smsService,
                webSocketHandler, retryExecutor);
        setField(service, "turbidityMild", 5.0);
        setField(service, "turbidityModerate", 30.0);
        setField(service, "turbiditySevere", 80.0);
        setField(service, "codMild", 15.0);
        setField(service, "codModerate", 30.0);
        setField(service, "codSevere", 50.0);
        setField(service, "phMin", 6.5);
        setField(service, "phMax", 8.5);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    @DisplayName("null输入返回跳过结果")
    void pushAlert_nullInput_returnsSkipped() {
        AlertResult result = service.pushAlert(null);
        assertNotNull(result);
        assertEquals("SKIPPED", result.getPushStatus());
    }

    @Test
    @DisplayName("正常等级不触发告警")
    void pushAlert_normalLevel_skipped() {
        AnalysisResult result = new AnalysisResult();
        result.setPointId("1");
        result.setAlertLevel(AlertLevel.NORMAL.getLevelCode());

        AlertResult alertResult = service.pushAlert(result);
        assertNotNull(alertResult);
        assertEquals("SKIPPED", alertResult.getPushStatus());
    }

    // ---- 多参数联合规则测试 ----

    @Test
    @DisplayName("联合规则: 三参数正常 → 正常")
    void evaluateCombinedAlert_allNormal() {
        int level = service.evaluateCombinedAlert(3.0, 10.0, 7.1);
        assertEquals(AlertLevel.NORMAL.getLevelCode(), level);
    }

    @Test
    @DisplayName("联合规则: 单参数轻度 → 轻度")
    void evaluateCombinedAlert_singleMild() {
        int level = service.evaluateCombinedAlert(10.0, 10.0, 7.1);  // 仅浊度轻度
        assertEquals(AlertLevel.MILD.getLevelCode(), level);
    }

    @Test
    @DisplayName("联合规则: 两参数异常 → 升级")
    void evaluateCombinedAlert_multiParameter_upgrade() {
        // 浊度中度 + COD中度 → 应升级
        int level = service.evaluateCombinedAlert(40.0, 35.0, 7.0);
        assertTrue(level >= AlertLevel.MODERATE.getLevelCode());
    }

    @Test
    @DisplayName("联合规则: 三参数全异常 → 重度")
    void evaluateCombinedAlert_allAbnormal_severe() {
        int level = service.evaluateCombinedAlert(85.0, 55.0, 5.0);
        assertEquals(AlertLevel.SEVERE.getLevelCode(), level);
    }

    @Test
    @DisplayName("联合规则: pH严重异常")
    void evaluateCombinedAlert_phSevere() {
        int level = service.evaluateCombinedAlert(3.0, 10.0, 4.0);  // pH=4 重度
        assertEquals(AlertLevel.SEVERE.getLevelCode(), level);
    }

    @Test
    @DisplayName("联合规则: 浊度边界值")
    void evaluateCombinedAlert_boundaryValues() {
        // 恰好等于阈值
        assertEquals(AlertLevel.MILD.getLevelCode(),
                service.evaluateCombinedAlert(5.0, 10.0, 7.0));
        assertEquals(AlertLevel.MODERATE.getLevelCode(),
                service.evaluateCombinedAlert(30.0, 10.0, 7.0));
        assertEquals(AlertLevel.SEVERE.getLevelCode(),
                service.evaluateCombinedAlert(80.0, 10.0, 7.0));
    }

    @Test
    @DisplayName("告警等级名称映射正确")
    void alertLevel_mapping() {
        assertEquals("正常", AlertLevel.nameOf(0));
        assertEquals("轻度异常", AlertLevel.nameOf(1));
        assertEquals("中度异常", AlertLevel.nameOf(2));
        assertEquals("重度异常", AlertLevel.nameOf(3));
    }
}

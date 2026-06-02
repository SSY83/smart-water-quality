package com.waterquality.mqtt;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.waterquality.dto.AnalysisResult;
import com.waterquality.entity.EdgeDevice;
import com.waterquality.mapper.EdgeDeviceMapper;
import com.waterquality.service.IntelligentAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.Executor;

@Component
public class MqttMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(MqttMessageHandler.class);

    private final IntelligentAnalysisService intelligentAnalysisService;
    private final EdgeDeviceMapper edgeDeviceMapper;
    private final Executor mqttExecutor;

    public MqttMessageHandler(IntelligentAnalysisService intelligentAnalysisService,
                               EdgeDeviceMapper edgeDeviceMapper,
                               @Qualifier("mqttExecutor") Executor mqttExecutor) {
        this.intelligentAnalysisService = intelligentAnalysisService;
        this.edgeDeviceMapper = edgeDeviceMapper;
        this.mqttExecutor = mqttExecutor;
    }

    /**
     * 处理MQTT消息 - 异步处理避免阻塞MQTT线程
     */
    public void handleMessage(String topic, byte[] payload) {
        mqttExecutor.execute(() -> {
            try {
                String jsonStr = new String(payload);
                String[] topicParts = topic.split("/");
                if (topicParts.length < 3) {
                    log.warn("无效的MQTT主题: {}", topic);
                    return;
                }
                String pointId = topicParts[2];
                String dataType = topicParts.length > 3 ? topicParts[3] : "unknown";

                if ("image_analysis".equals(dataType)) {
                    handleImageAnalysis(pointId, jsonStr);
                } else if ("sensor_data".equals(dataType)) {
                    handleSensorData(pointId, jsonStr);
                } else if ("heartbeat".equals(dataType)) {
                    handleHeartbeat(pointId, jsonStr);
                } else {
                    log.warn("未知的数据类型: {}", dataType);
                }
            } catch (Exception e) {
                log.error("MQTT消息处理失败: topic={}", topic, e);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void handleImageAnalysis(String pointId, String jsonStr) {
        JSONObject json = JSONUtil.parseObj(jsonStr);
        AnalysisResult result = new AnalysisResult();
        result.setPointId(pointId);
        result.setTimestamp(parseTimestamp(json));
        result.setAlertLevel(json.getInt("alertLevel", 0));
        result.setConfidence(json.getDouble("confidence", 0.0));
        result.setFinalScore(json.getDouble("finalScore", 0.0));
        result.setImageScore(json.getDouble("imageScore", 0.0));
        result.setTurbidityLevel(json.getInt("turbidityLevel", 0));
        result.setPollutionTypes(json.getStr("pollutionTypes"));

        Map<String, Object> details = json.get("details", Map.class);
        result.setDetails(details);

        intelligentAnalysisService.processAnalysisResult(result);
    }

    @SuppressWarnings("unchecked")
    private void handleSensorData(String pointId, String jsonStr) {
        JSONObject json = JSONUtil.parseObj(jsonStr);
        Map<String, Object> sensorData = (Map<String, Object>) json.get("data");

        if (sensorData == null) return;

        // 云端规则引擎降级模式分析
        AnalysisResult result = intelligentAnalysisService.ruleBasedAnalysis(
                Long.parseLong(pointId), sensorData);
        intelligentAnalysisService.processAnalysisResult(result);
    }

    private LocalDateTime parseTimestamp(JSONObject json) {
        try {
            return LocalDateTime.parse(json.getStr("timestamp", LocalDateTime.now().toString()));
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    private void handleHeartbeat(String pointId, String jsonStr) {
        try {
            JSONObject json = JSONUtil.parseObj(jsonStr);
            String deviceSn = json.getStr("deviceId", json.getStr("device_sn", pointId));
            if (deviceSn != null && !deviceSn.isEmpty()) {
                EdgeDevice device = edgeDeviceMapper.selectById(deviceSn);
                if (device != null) {
                    device.setLastHeartbeat(LocalDateTime.now());
                    device.setStatus(1);
                    edgeDeviceMapper.updateById(device);
                }
            }
        } catch (Exception e) {
            log.debug("心跳处理异常: pointId={}", pointId, e);
        }
    }
}

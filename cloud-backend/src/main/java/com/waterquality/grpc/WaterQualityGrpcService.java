package com.waterquality.grpc;

import com.waterquality.dto.AlertResult;
import com.waterquality.dto.AnalysisResult;
import com.waterquality.entity.EdgeDevice;
import com.waterquality.mapper.EdgeDeviceMapper;
import com.waterquality.proto.*;
import com.waterquality.service.AlertPushService;
import com.waterquality.service.IntelligentAnalysisService;
import com.waterquality.websocket.AlertWebSocketHandler;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
public class WaterQualityGrpcService
        extends WaterQualityEdgeServiceGrpc.WaterQualityEdgeServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(WaterQualityGrpcService.class);

    private final IntelligentAnalysisService intelligentAnalysisService;
    private final AlertPushService alertPushService;
    private final AlertWebSocketHandler webSocketHandler;
    private final EdgeDeviceMapper edgeDeviceMapper;

    public WaterQualityGrpcService(IntelligentAnalysisService intelligentAnalysisService,
                                   AlertPushService alertPushService,
                                   AlertWebSocketHandler webSocketHandler,
                                   EdgeDeviceMapper edgeDeviceMapper) {
        this.intelligentAnalysisService = intelligentAnalysisService;
        this.alertPushService = alertPushService;
        this.webSocketHandler = webSocketHandler;
        this.edgeDeviceMapper = edgeDeviceMapper;
    }

    // ---- Bidirectional Streaming ----

    @Override
    public StreamObserver<WaterQualityMessage> streamAnalysisResults(
            StreamObserver<EdgeCommand> responseObserver) {
        return new StreamObserver<WaterQualityMessage>() {
            @Override
            public void onNext(WaterQualityMessage msg) {
                try {
                    AnalysisResult result = convertProtoToAnalysisResult(msg);
                    AlertResult alertResult = intelligentAnalysisService.processAnalysisResult(result);

                    EdgeCommand ack = EdgeCommand.newBuilder()
                            .setType(EdgeCommand.CommandType.ACK)
                            .setCommandId(msg.getPointId() + "-" + System.currentTimeMillis())
                            .setMessage("ACK:" + alertResult.getAlertId())
                            .setServerTimestampMs(System.currentTimeMillis())
                            .build();
                    responseObserver.onNext(ack);
                } catch (Exception e) {
                    log.error("Stream message processing failed", e);
                }
            }

            @Override
            public void onError(Throwable t) {
                log.error("Stream error from edge", t);
                responseObserver.onCompleted();
            }

            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
    }

    // ---- Unary: ReportAlert ----

    @Override
    public void reportAlert(AlertRequest request,
                            StreamObserver<AlertResponse> responseObserver) {
        try {
            AnalysisResult result = new AnalysisResult();
            result.setPointId(request.getPointId());
            result.setTimestamp(LocalDateTime.now());
            result.setAlertLevel(request.getAlertLevel());
            result.setConfidence((double) request.getConfidence());
            result.setFinalScore((double) request.getFinalScore());

            AlertResult cloudAlert = alertPushService.pushAlert(result);

            AlertResponse response = AlertResponse.newBuilder()
                    .setAlertId(cloudAlert.getAlertId())
                    .setStatus("ACKED")
                    .setServerTimestampMs(System.currentTimeMillis())
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("ReportAlert failed", e);
            responseObserver.onError(
                io.grpc.Status.INTERNAL
                    .withDescription("Alert processing failed: " + e.getMessage())
                    .asRuntimeException()
            );
        }
    }

    // ---- Unary: GetModelVersion ----

    @Override
    public void getModelVersion(ModelVersionRequest request,
                                StreamObserver<ModelVersionResponse> responseObserver) {
        try {
            String latestMobile = getConfigValue("model.mobilenet_version", "1.0.0");
            String latestUnet = getConfigValue("model.unet_version", "1.0.0");

            boolean mobileUpdate = !latestMobile.equals(request.getCurrentMobilenetVersion());
            boolean unetUpdate = !latestUnet.equals(request.getCurrentUnetVersion());

            ModelVersionResponse response = ModelVersionResponse.newBuilder()
                    .setLatestMobilenetVersion(latestMobile)
                    .setLatestUnetVersion(latestUnet)
                    .setMobilenetUpdateAvailable(mobileUpdate)
                    .setUnetUpdateAvailable(unetUpdate)
                    .setMobilenetDownloadUrl(mobileUpdate
                        ? getConfigValue("model.mobilenet_url", "") : "")
                    .setUnetDownloadUrl(unetUpdate
                        ? getConfigValue("model.unet_url", "") : "")
                    .setServerTimestampMs(System.currentTimeMillis())
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("GetModelVersion failed", e);
            responseObserver.onError(
                io.grpc.Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException()
            );
        }
    }

    // ---- Unary: HealthCheck ----

    @Override
    public void healthCheck(HealthCheckRequest request,
                            StreamObserver<HealthCheckResponse> responseObserver) {
        try {
            EdgeDevice device = edgeDeviceMapper.selectByDeviceSn(request.getDeviceId());
            if (device != null) {
                device.setLastHeartbeat(LocalDateTime.now());
                edgeDeviceMapper.updateById(device);
            }

            HealthCheckResponse response = HealthCheckResponse.newBuilder()
                    .setStatus("OK")
                    .setServerTimestampMs(System.currentTimeMillis())
                    .setRecommendedHeartbeatIntervalSec(30)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("HealthCheck failed", e);
            responseObserver.onNext(HealthCheckResponse.newBuilder()
                    .setStatus("DEGRADED")
                    .setServerTimestampMs(System.currentTimeMillis())
                    .build());
            responseObserver.onCompleted();
        }
    }

    // ---- Helper methods ----

    private AnalysisResult convertProtoToAnalysisResult(WaterQualityMessage msg) {
        AnalysisResult result = new AnalysisResult();
        result.setPointId(msg.getPointId());
        result.setTimestamp(LocalDateTime.now());

        if (msg.hasImageAnalysis()) {
            ImageAnalysis ia = msg.getImageAnalysis();
            result.setTurbidityLevel(ia.getTurbidityLevel());
            result.setConfidence((double) ia.getConfidence());
            result.setImageScore((double) ia.getImageScore());
            result.setPollutionTypes(ia.getPollutionTypes());
            if (ia.getPollutionProbsCount() > 0) {
                Map<String, Object> details = new HashMap<>();
                details.put("pollutionProbs", ia.getPollutionProbsList());
                result.setDetails(details);
            }
        }

        if (msg.hasAlertData()) {
            AlertData ad = msg.getAlertData();
            result.setAlertLevel(ad.getAlertLevel());
            result.setFinalScore((double) ad.getFinalScore());
            if (result.getConfidence() == null) {
                result.setConfidence((double) ad.getConfidence());
            }
        }

        if (msg.hasSensorData()) {
            SensorData sd = msg.getSensorData();
            Map<String, Object> details = result.getDetails();
            if (details == null) details = new HashMap<>();
            details.put("turbidity", sd.getTurbidityNtu());
            details.put("cod", sd.getCodValue());
            details.put("ph", sd.getPhValue());
            result.setDetails(details);
        }

        return result;
    }

    private String getConfigValue(String key, String defaultValue) {
        // Config values stored in system_config table or application.yml
        // For now, return defaults. Extend with SysConfigMapper when available.
        return defaultValue;
    }
}

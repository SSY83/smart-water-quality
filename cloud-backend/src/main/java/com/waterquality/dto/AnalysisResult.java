package com.waterquality.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.Map;

public class AnalysisResult {

    @NotBlank
    private String pointId;

    @NotNull
    private LocalDateTime timestamp;

    @NotNull
    private Integer alertLevel;

    private Map<String, Object> details;

    private Double confidence;

    private Double finalScore;

    private Double imageScore;

    private Double sensorScore;

    private Integer turbidityLevel;

    private String pollutionTypes;

    private String segmentationMask;

    public String getPointId() { return pointId; }
    public void setPointId(String pointId) { this.pointId = pointId; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public Integer getAlertLevel() { return alertLevel; }
    public void setAlertLevel(Integer alertLevel) { this.alertLevel = alertLevel; }

    public Map<String, Object> getDetails() { return details; }
    public void setDetails(Map<String, Object> details) { this.details = details; }

    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }

    public Double getFinalScore() { return finalScore; }
    public void setFinalScore(Double finalScore) { this.finalScore = finalScore; }

    public Double getImageScore() { return imageScore; }
    public void setImageScore(Double imageScore) { this.imageScore = imageScore; }

    public Double getSensorScore() { return sensorScore; }
    public void setSensorScore(Double sensorScore) { this.sensorScore = sensorScore; }

    public Integer getTurbidityLevel() { return turbidityLevel; }
    public void setTurbidityLevel(Integer turbidityLevel) { this.turbidityLevel = turbidityLevel; }

    public String getPollutionTypes() { return pollutionTypes; }
    public void setPollutionTypes(String pollutionTypes) { this.pollutionTypes = pollutionTypes; }

    public String getSegmentationMask() { return segmentationMask; }
    public void setSegmentationMask(String segmentationMask) { this.segmentationMask = segmentationMask; }
}

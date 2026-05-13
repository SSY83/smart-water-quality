package com.waterquality.dto;

public class AlertResult {
    private String alertId;
    private String pushStatus;

    public AlertResult() {}

    public AlertResult(String alertId, String pushStatus) {
        this.alertId = alertId;
        this.pushStatus = pushStatus;
    }

    public static AlertResult skipped() {
        return new AlertResult("", "SKIPPED");
    }

    public static AlertResult failed() {
        return new AlertResult("", "FAILED");
    }

    public static AlertResult success(String alertId) {
        return new AlertResult(alertId, "SUCCESS");
    }

    public String getAlertId() { return alertId; }
    public void setAlertId(String alertId) { this.alertId = alertId; }
    public String getPushStatus() { return pushStatus; }
    public void setPushStatus(String pushStatus) { this.pushStatus = pushStatus; }
}

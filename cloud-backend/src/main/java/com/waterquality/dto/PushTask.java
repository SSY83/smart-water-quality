package com.waterquality.dto;

import java.time.LocalDateTime;
import java.util.List;

public class PushTask implements Comparable<PushTask> {
    private String taskId;
    private Long alertId;
    private List<String> channels;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;

    public PushTask() {}

    public PushTask(String taskId, Long alertId, List<String> channels) {
        this.taskId = taskId;
        this.alertId = alertId;
        this.channels = channels;
        this.retryCount = 0;
        this.nextRetryTime = LocalDateTime.now();
    }

    @Override
    public int compareTo(PushTask other) {
        if (this.nextRetryTime == null) return 1;
        if (other.nextRetryTime == null) return -1;
        return this.nextRetryTime.compareTo(other.nextRetryTime);
    }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public Long getAlertId() { return alertId; }
    public void setAlertId(Long alertId) { this.alertId = alertId; }
    public List<String> getChannels() { return channels; }
    public void setChannels(List<String> channels) { this.channels = channels; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public LocalDateTime getNextRetryTime() { return nextRetryTime; }
    public void setNextRetryTime(LocalDateTime nextRetryTime) { this.nextRetryTime = nextRetryTime; }
}

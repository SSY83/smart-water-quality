package com.waterquality.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.time.LocalDateTime;
import java.util.List;

public class QueryParams {
    private List<Long> pointIds;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String dataType;

    @Min(1)
    private Integer page = 1;

    @Min(1)
    @Max(100)
    private Integer pageSize = 20;

    public boolean validate() {
        if (startTime != null && endTime != null) {
            long days = java.time.Duration.between(startTime, endTime).toDays();
            if (days > 30) return false;
        }
        if (pageSize > 100) return false;
        return true;
    }

    public List<Long> getPointIds() { return pointIds; }
    public void setPointIds(List<Long> pointIds) { this.pointIds = pointIds; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
}

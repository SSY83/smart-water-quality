package com.waterquality.controller;

import com.waterquality.constant.ErrorCode;
import com.waterquality.dto.AnalysisResult;
import com.waterquality.dto.AlertResult;
import com.waterquality.dto.Result;
import com.waterquality.exception.BusinessException;
import com.waterquality.service.IntelligentAnalysisService;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@RestController
@RequestMapping("/api/data")
public class DataController {

    private final IntelligentAnalysisService intelligentAnalysisService;

    public DataController(IntelligentAnalysisService intelligentAnalysisService) {
        this.intelligentAnalysisService = intelligentAnalysisService;
    }

    /**
     * 边缘端上报水质分析数据
     */
    @PostMapping("/report")
    public Result<AlertResult> reportData(@Valid @RequestBody AnalysisResult result,
                                           HttpServletRequest request) {
        AlertResult alertResult = intelligentAnalysisService.processAnalysisResult(result);
        return Result.success(alertResult);
    }

    /**
     * 边缘端心跳上报
     */
    @PostMapping("/heartbeat")
    public Result<Void> heartbeat(@RequestParam String deviceId,
                                   HttpServletRequest request) {
        // 更新设备心跳时间 (通过Service)
        return Result.success(null);
    }
}

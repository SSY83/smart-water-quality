package com.waterquality.controller;

import com.waterquality.dto.Result;
import com.waterquality.security.JwtBlacklist;
import com.waterquality.websocket.AlertWebSocketHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.*;
import java.util.*;

@RestController
@RequestMapping("/api/system")
public class SystemMonitorController {

    private final JwtBlacklist jwtBlacklist;
    private final AlertWebSocketHandler webSocketHandler;

    public SystemMonitorController(JwtBlacklist jwtBlacklist,
                                   AlertWebSocketHandler webSocketHandler) {
        this.jwtBlacklist = jwtBlacklist;
        this.webSocketHandler = webSocketHandler;
    }

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        Map<String, Object> data = new LinkedHashMap<>();

        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memory.getHeapMemoryUsage();
        Map<String, Object> memInfo = new LinkedHashMap<>();
        memInfo.put("heapUsedMB", heap.getUsed() / 1024 / 1024);
        memInfo.put("heapMaxMB", heap.getMax() / 1024 / 1024);
        memInfo.put("heapUsagePercent",
            Math.round(heap.getUsed() * 10000.0 / heap.getMax()) / 100.0);
        data.put("memory", memInfo);

        ThreadMXBean threadMX = ManagementFactory.getThreadMXBean();
        Map<String, Object> threadInfo = new LinkedHashMap<>();
        threadInfo.put("totalThreads", threadMX.getThreadCount());
        threadInfo.put("peakThreads", threadMX.getPeakThreadCount());
        threadInfo.put("daemonThreads", threadMX.getDaemonThreadCount());
        data.put("threads", threadInfo);

        List<Map<String, Object>> gcInfo = new ArrayList<>();
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            Map<String, Object> gcEntry = new LinkedHashMap<>();
            gcEntry.put("name", gc.getName());
            gcEntry.put("count", gc.getCollectionCount());
            gcEntry.put("timeMs", gc.getCollectionTime());
            gcInfo.add(gcEntry);
        }
        data.put("gc", gcInfo);

        data.put("blacklistedTokens", jwtBlacklist.size());
        data.put("activeWebSockets", webSocketHandler.getActiveConnectionCount());

        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        data.put("uptimeMinutes", runtime.getUptime() / 60000);

        return Result.success(data);
    }
}

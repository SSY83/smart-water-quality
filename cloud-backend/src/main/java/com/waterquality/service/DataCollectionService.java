package com.waterquality.service;

import com.waterquality.dto.AnalysisResult;
import com.waterquality.entity.WaterQualityData;
import com.waterquality.mapper.WaterQualityDataMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;

@Service
public class DataCollectionService {

    private static final Logger log = LoggerFactory.getLogger(DataCollectionService.class);

    private final WaterQualityDataMapper waterQualityDataMapper;
    private final BlockingQueue<WaterQualityData> writeQueue;
    private final Executor dbWriteExecutor;

    public DataCollectionService(WaterQualityDataMapper waterQualityDataMapper,
                                  @Qualifier("dbWriteExecutor") Executor dbWriteExecutor) {
        this.waterQualityDataMapper = waterQualityDataMapper;
        this.dbWriteExecutor = dbWriteExecutor;
        this.writeQueue = new java.util.concurrent.LinkedBlockingQueue<>(1000);
        startWriteWorker();
    }

    public void receiveData(AnalysisResult result) {
        WaterQualityData data = new WaterQualityData();
        data.setPointId(Long.parseLong(result.getPointId()));
        data.setTimestamp(result.getTimestamp());
        data.setAlertLevel(result.getAlertLevel());
        if (result.getDetails() != null) {
            Object turbidityValue = result.getDetails().get("turbidity");
            if (turbidityValue != null) {
                data.setTurbidityNtu(new BigDecimal(turbidityValue.toString()));
            }
            Object codValue = result.getDetails().get("cod");
            if (codValue != null) {
                data.setCodValue(new BigDecimal(codValue.toString()));
            }
            Object phValue = result.getDetails().get("ph");
            if (phValue != null) {
                data.setPhValue(new BigDecimal(phValue.toString()));
            }
        }
        data.setTurbidityLevel(result.getTurbidityLevel());
        data.setPollutionTypes(result.getPollutionTypes());
        if (result.getConfidence() != null) {
            data.setConfidence(BigDecimal.valueOf(result.getConfidence()));
        }
        if (result.getFinalScore() != null) {
            data.setFinalScore(BigDecimal.valueOf(result.getFinalScore()));
        }
        if (result.getImageScore() != null) {
            data.setImageScore(BigDecimal.valueOf(result.getImageScore()));
        }
        if (result.getSensorScore() != null) {
            data.setSensorScore(BigDecimal.valueOf(result.getSensorScore()));
        }
        data.setCreatedAt(LocalDateTime.now());

        // 异步写入
        if (!writeQueue.offer(data)) {
            log.warn("写入队列已满，丢弃数据: pointId={}", result.getPointId());
        }
    }

    public void batchInsert(List<WaterQualityData> dataList) {
        for (WaterQualityData data : dataList) {
            waterQualityDataMapper.insert(data);
        }
    }

    private void startWriteWorker() {
        dbWriteExecutor.execute(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    WaterQualityData data = writeQueue.take();
                    waterQualityDataMapper.insert(data);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("数据写入失败", e);
                }
            }
        });
    }
}

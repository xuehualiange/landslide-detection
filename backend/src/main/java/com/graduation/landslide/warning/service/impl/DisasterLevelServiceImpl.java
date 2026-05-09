package com.graduation.landslide.warning.service.impl;

import com.graduation.landslide.ai.YoloDetector;
import com.graduation.landslide.entity.LandslideDetectRecord;
import com.graduation.landslide.entity.MonitorData;
import com.graduation.landslide.service.LandslideDetectRecordService;
import com.graduation.landslide.service.MonitorDataService;
import com.graduation.landslide.warning.dto.DisasterAssessmentResult;
import com.graduation.landslide.warning.service.DisasterLevelService;
import com.graduation.landslide.warning.service.WarningPushService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Service
public class DisasterLevelServiceImpl implements DisasterLevelService {

    private final YoloDetector yoloDetector;
    private final MonitorDataService monitorDataService;
    private final LandslideDetectRecordService detectRecordService;
    private final WarningPushService warningPushService;

    public DisasterLevelServiceImpl(YoloDetector yoloDetector,
                                    MonitorDataService monitorDataService,
                                    LandslideDetectRecordService detectRecordService,
                                    WarningPushService warningPushService) {
        this.yoloDetector = yoloDetector;
        this.monitorDataService = monitorDataService;
        this.detectRecordService = detectRecordService;
        this.warningPushService = warningPushService;
    }

    @Override
    public DisasterAssessmentResult assessAndWarn(Long userId, String imagePath, byte[] imageBytes) {
        List<YoloDetector.DetectionBox> boxes = yoloDetector.detect(imageBytes);

        BigDecimal area = calculateArea(boxes);
        BigDecimal maxConfidence = calculateMaxConfidence(boxes);
        String level = classifyDisasterLevel(area, maxConfidence);

        MonitorData latestMonitor = monitorDataService.lambdaQuery()
                .orderByDesc(MonitorData::getCollectTime)
                .last("limit 1")
                .one();
        BigDecimal latestRate = latestMonitor == null || latestMonitor.getDeformationRate() == null
                ? BigDecimal.ZERO
                : latestMonitor.getDeformationRate();

        boolean warningTriggered = shouldTriggerWarning(level, latestRate);

        LandslideDetectRecord record = new LandslideDetectRecord();
        record.setUserId(userId);
        record.setImagePath(imagePath);
        record.setLandslideArea(area);
        record.setMaxConfidence(maxConfidence);
        record.setDisasterLevel(level);
        record.setWarningTriggered(warningTriggered ? 1 : 0);
        detectRecordService.save(record);

        Map<String, Object> debugInfo = yoloDetector.getLastDebugInfo();
        DisasterAssessmentResult result = new DisasterAssessmentResult(
                area,
                maxConfidence,
                level,
                warningTriggered,
                latestRate,
                boxes,
                debugInfo
        );

        if (warningTriggered) {
            String warningMsg = String.format(
                    "滑坡预警：灾情%s，面积%s，最大置信度%s，变形速率%smm/天，请附近工作人员尽快核查。",
                    level,
                    area.toPlainString(),
                    maxConfidence.toPlainString(),
                    latestRate.toPlainString()
            );
            warningPushService.pushToNearbyWorkers(warningMsg, result);
        }
        return result;
    }

    private BigDecimal calculateArea(List<YoloDetector.DetectionBox> boxes) {
        if (boxes == null || boxes.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (YoloDetector.DetectionBox box : boxes) {
            BigDecimal width = BigDecimal.valueOf(Math.max(0, box.getWidth()));
            BigDecimal height = BigDecimal.valueOf(Math.max(0, box.getHeight()));
            sum = sum.add(width.multiply(height));
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateMaxConfidence(List<YoloDetector.DetectionBox> boxes) {
        if (boxes == null || boxes.isEmpty()) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        float max = 0f;
        for (YoloDetector.DetectionBox box : boxes) {
            if (box.getConfidence() > max) {
                max = box.getConfidence();
            }
        }
        return BigDecimal.valueOf(max).setScale(4, RoundingMode.HALF_UP);
    }

    private String classifyDisasterLevel(BigDecimal area, BigDecimal confidence) {
        if (area.compareTo(BigDecimal.valueOf(50000)) > 0 || confidence.compareTo(BigDecimal.valueOf(0.9)) > 0) {
            return "I级特别重大";
        }
        if (area.compareTo(BigDecimal.valueOf(20000)) > 0 || confidence.compareTo(BigDecimal.valueOf(0.8)) > 0) {
            return "II级重大";
        }
        if (area.compareTo(BigDecimal.valueOf(5000)) > 0 || confidence.compareTo(BigDecimal.valueOf(0.7)) > 0) {
            return "III级较大";
        }
        return "IV级一般";
    }

    private boolean shouldTriggerWarning(String disasterLevel, BigDecimal deformationRate) {
        return "I级特别重大".equals(disasterLevel) || "II级重大".equals(disasterLevel);
    }
}
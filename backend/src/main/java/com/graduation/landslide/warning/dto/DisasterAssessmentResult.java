package com.graduation.landslide.warning.dto;

import com.graduation.landslide.ai.YoloDetector;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DisasterAssessmentResult {
    private BigDecimal landslideArea;
    private BigDecimal maxConfidence;
    private String disasterLevel;
    private boolean warningTriggered;
    private BigDecimal latestDeformationRate;
    private List<YoloDetector.DetectionBox> boxes;
    private Map<String, Object> debugInfo;
}
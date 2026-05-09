package com.graduation.landslide.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DetectResult {
    private Double confidence;
    private Double landslideAreaRatio;
    private String disasterLevel;
    private String suggestion;
}

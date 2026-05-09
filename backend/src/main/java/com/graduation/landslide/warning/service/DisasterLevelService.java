package com.graduation.landslide.warning.service;

import com.graduation.landslide.warning.dto.DisasterAssessmentResult;

public interface DisasterLevelService {

    DisasterAssessmentResult assessAndWarn(Long userId, String imagePath, byte[] imageBytes);
}

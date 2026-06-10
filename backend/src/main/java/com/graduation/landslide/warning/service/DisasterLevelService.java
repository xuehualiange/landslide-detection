package com.graduation.landslide.warning.service;

import com.graduation.landslide.warning.dto.DisasterAssessmentResult;

/** 灾情评估服务接口 */
public interface DisasterLevelService {

    DisasterAssessmentResult assessAndWarn(Long userId, String imagePath, byte[] imageBytes);
}

package com.graduation.landslide.warning.service;

import com.graduation.landslide.warning.dto.DisasterAssessmentResult;

public interface WarningPushService {

    void pushToNearbyWorkers(String message, DisasterAssessmentResult result);
}

package com.graduation.landslide.warning.service;

import com.graduation.landslide.warning.dto.DisasterAssessmentResult;

/**
 * 预警推送服务接口。
 * 当识别结果达到 I/II 级等需告警条件时，由灾情评估模块调用：
 * 将预警写入数据库，并通过 WebSocket 推送给前端「灾情动态」页面。
 */
public interface WarningPushService {

    /**
     * 向附近工作人员推送预警。
     * @param message 预警文案（含等级、面积、置信度等）
     * @param result  本次识别与灾情评估结果，用于落库
     */
    void pushToNearbyWorkers(String message, DisasterAssessmentResult result);
}
package com.graduation.landslide.warning.service.impl;

import com.graduation.landslide.entity.WarningEvent;
import com.graduation.landslide.warning.dto.DisasterAssessmentResult;
import com.graduation.landslide.warning.service.WarningPushService;
import com.graduation.landslide.service.WarningEventService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
/** 预警推送实现：数据库落库 + WebSocket 广播到 /topic/warning/nearby */
public class WarningPushServiceImpl implements WarningPushService {

    /** Spring 消息模板，用于 STOMP 推送 */
    private final SimpMessagingTemplate messagingTemplate;
    private final WarningEventService warningEventService;

    public WarningPushServiceImpl(SimpMessagingTemplate messagingTemplate,
                                  WarningEventService warningEventService) {
        this.messagingTemplate = messagingTemplate;
        this.warningEventService = warningEventService;
    }

    @Override
    public void pushToNearbyWorkers(String message, DisasterAssessmentResult result) {
        // 1. 写入预警表，供灾情动态列表与确认处理
        WarningEvent event = new WarningEvent();
        event.setMessage(message);
        event.setDisasterLevel(result.getDisasterLevel());
        event.setLandslideArea(result.getLandslideArea());
        event.setMaxConfidence(result.getMaxConfidence());
        event.setLatestDeformationRate(result.getLatestDeformationRate());
        event.setStatus("UNREAD");
        warningEventService.save(event);

        // 2. 推送给已订阅 WebSocket 的前端
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", event.getId());
        payload.put("message", event.getMessage());
        payload.put("disasterLevel", event.getDisasterLevel());
        payload.put("landslideArea", event.getLandslideArea());
        payload.put("maxConfidence", event.getMaxConfidence());
        payload.put("latestDeformationRate", event.getLatestDeformationRate());
        payload.put("status", event.getStatus());
        payload.put("time", event.getCreatedTime() == null ? LocalDateTime.now() : event.getCreatedTime());
        messagingTemplate.convertAndSend("/topic/warning/nearby", payload);
    }
}

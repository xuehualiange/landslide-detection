package com.graduation.landslide.assistant.dto;

import javax.validation.constraints.NotBlank;

import lombok.Data;

@Data
public class AssistantChatRequest {

    @NotBlank(message = "消息不能为空")
    private String userInput;

    private String language;

    private String sessionId;
}
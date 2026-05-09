package com.graduation.landslide.chat;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.graduation.landslide.chat.dto.ChatReplyResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ChatApiService {

    private static final String USER_INPUT_KEY = "user_input";
    private static final String SESSION_ID_KEY = "session_id";
    private static final String LANGUAGE_KEY = "language";
    private static final String RECORD_CONTEXT_KEY = "record_context";

    private final RestTemplate restTemplate;
    private final String chatUrl;

    public ChatApiService(
            RestTemplate restTemplate,
            @Value("${chat.api.chat-url:http://localhost:8000/chat}") String chatUrl) {
        this.restTemplate = restTemplate;
        this.chatUrl = chatUrl;
    }

    public String getAiResponse(String userInput) {
        return getAiResponse(userInput, null, null);
    }

    public String getAiResponse(String userInput, String sessionId) {
        return getAiResponse(userInput, sessionId, null);
    }

    public String getAiResponse(String userInput, String sessionId, String language) {
        return getAiResponse(userInput, sessionId, language, null);
    }

    public String getAiResponse(String userInput, String sessionId, String language, String recordContext) {
        if (!StringUtils.hasText(userInput)) {
            throw new IllegalArgumentException("userInput must not be null or blank");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> payload = new HashMap<>(12);
        payload.put(USER_INPUT_KEY, userInput.trim());
        if (StringUtils.hasText(sessionId)) {
            payload.put(SESSION_ID_KEY, sessionId.trim());
        }
        if (StringUtils.hasText(language)) {
            payload.put(LANGUAGE_KEY, language.trim());
        }
        if (StringUtils.hasText(recordContext)) {
            payload.put(RECORD_CONTEXT_KEY, recordContext);
        }

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<ChatReplyResponse> response =
                    restTemplate.postForEntity(chatUrl, entity, ChatReplyResponse.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("Chat API HTTP status: " + response.getStatusCode());
            }
            ChatReplyResponse body = response.getBody();
            if (body == null) {
                throw new IllegalStateException("Chat API returned empty body");
            }
            String reply = body.getReply();
            return reply == null ? "" : reply;
        } catch (RestClientException ex) {
            log.error("Chat API request failed, url={}", chatUrl, ex);
            throw new IllegalStateException("Chat API call failed: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            log.error("Unexpected error calling Chat API, url={}", chatUrl, ex);
            throw new IllegalStateException("Chat API error: " + ex.getMessage(), ex);
        }
    }

    public void resetChatSession(String sessionId) {
        String trimmed = chatUrl.trim();
        String resetUrl = trimmed.endsWith("/chat") ? trimmed + "/reset" : trimmed.replaceAll("/?$", "") + "/chat/reset";
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(resetUrl);
        if (StringUtils.hasText(sessionId)) {
            builder.queryParam("session_id", sessionId.trim());
        }
        try {
            ResponseEntity<Object> resetResponse =
                    restTemplate.postForEntity(builder.toUriString(), HttpEntity.EMPTY, Object.class);
            if (!resetResponse.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("Chat API reset HTTP status: " + resetResponse.getStatusCode());
            }
        } catch (RestClientException ex) {
            log.error("Chat API reset failed, url={}", builder.toUriString(), ex);
            throw new IllegalStateException("Chat API reset failed: " + ex.getMessage(), ex);
        }
    }
}
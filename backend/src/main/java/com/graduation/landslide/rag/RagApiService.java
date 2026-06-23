package com.graduation.landslide.rag;

import com.graduation.landslide.rag.dto.RagAskResponse;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
/** 调用 Python RAG 服务 POST /ask */
public class RagApiService {

    private final RestTemplate restTemplate;
    private final String askUrl;

    public RagApiService(
            RestTemplate restTemplate,
            @Value("${rag.api.ask-url:http://localhost:8000/ask}") String askUrl) {
        this.restTemplate = restTemplate;
        this.askUrl = askUrl;
    }

    public RagAskResponse ask(String question) {
        if (!StringUtils.hasText(question)) {
            throw new IllegalArgumentException("问题不能为空");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> payload = new HashMap<>(2);
        payload.put("question", question.trim());

        try {
            ResponseEntity<RagAskResponse> response = restTemplate.postForEntity(
                    askUrl, new HttpEntity<>(payload, headers), RagAskResponse.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new IllegalStateException("RAG API HTTP status: " + response.getStatusCode());
            }
            return response.getBody();
        } catch (RestClientException ex) {
            log.error("RAG API request failed, url={}", askUrl, ex);
            throw new IllegalStateException("RAG service unavailable: " + ex.getMessage(), ex);
        }
    }
}
package com.graduation.landslide.rag;

import com.graduation.landslide.common.ApiResponse;
import com.graduation.landslide.rag.dto.RagAskRequest;
import com.graduation.landslide.rag.dto.RagAskResponse;
import javax.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/rag")
/** 知识库 RAG 问答：转发到 rag-service */
public class RagController {

    private final RagApiService ragApiService;

    public RagController(RagApiService ragApiService) {
        this.ragApiService = ragApiService;
    }

    @PostMapping("/ask")
    public ApiResponse<RagAskResponse> ask(@Valid @RequestBody RagAskRequest body) {
        try {
            RagAskResponse result = ragApiService.ask(body.getQuestion());
            return ApiResponse.success(result);
        } catch (IllegalArgumentException ex) {
            return ApiResponse.fail(ex.getMessage());
        } catch (IllegalStateException ex) {
            log.warn("RAG upstream error: {}", ex.getMessage());
            return ApiResponse.fail("RAG 服务暂时不可用，请确认已启动 rag-service（默认 http://localhost:8000/ask）");
        } catch (Exception ex) {
            log.error("RAG ask failed", ex);
            return ApiResponse.fail("问答失败：" + ex.getMessage());
        }
    }
}

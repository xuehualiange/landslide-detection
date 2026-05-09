package com.graduation.landslide.assistant;

import javax.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.graduation.landslide.assistant.dto.AssistantChatReply;
import com.graduation.landslide.assistant.dto.AssistantChatRequest;
import com.graduation.landslide.chat.ChatApiService;
import com.graduation.landslide.common.ApiResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/assistant")
public class AssistantChatController {

    private final ChatApiService chatApiService;
    private final AssistantHistoryContextService historyContextService;

    public AssistantChatController(ChatApiService chatApiService,
            AssistantHistoryContextService historyContextService) {
        this.chatApiService = chatApiService;
        this.historyContextService = historyContextService;
    }

    @PostMapping("/chat")
    public ApiResponse<AssistantChatReply> chat(@Valid @RequestBody AssistantChatRequest body) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication != null ? authentication.getName() : "anonymous";

            String sessionId = body.getSessionId();
            if (!StringUtils.hasText(sessionId)) {
                sessionId = "landslide-user-" + username;
            }

            String recordSummary = historyContextService.buildSummaryForAssistant();
            String reply = chatApiService.getAiResponse(
                    body.getUserInput(),
                    sessionId,
                    body.getLanguage(),
                    recordSummary);
            return ApiResponse.success(new AssistantChatReply(reply));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.fail(ex.getMessage());
        } catch (IllegalStateException ex) {
            log.warn("Assistant chat upstream error: {}", ex.getMessage());
            return ApiResponse.fail("对话服务暂时不可用，请确认已启动 Python 对话服务（默认 http://localhost:8000 ，配置项 chat.api.chat-url）");
        } catch (Exception ex) {
            log.error("Assistant chat failed", ex);
            return ApiResponse.fail("对话失败：" + ex.getMessage());
        }
    }

    @PostMapping("/chat/reset")
    public ApiResponse<String> reset(@RequestParam(value = "sessionId", required = false) String sessionId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication != null ? authentication.getName() : "anonymous";

        String sid = sessionId;
        if (!StringUtils.hasText(sid)) {
            sid = "landslide-user-" + username;
        }

        try {
            chatApiService.resetChatSession(sid);
            return ApiResponse.success("已清空本会话上下文");
        } catch (IllegalStateException ex) {
            log.warn("Assistant reset failed: {}", ex.getMessage());
            return ApiResponse.fail("清空会话失败，请确认对话服务已启动");
        } catch (Exception ex) {
            log.error("Assistant reset error", ex);
            return ApiResponse.fail("清空失败：" + ex.getMessage());
        }
    }
}
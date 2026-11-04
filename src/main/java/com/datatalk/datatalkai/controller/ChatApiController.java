package com.datatalk.datatalkai.controller;

import com.datatalk.datatalkai.chat.ChatRecord;
import com.datatalk.datatalkai.chat.ChatResult;
import com.datatalk.datatalkai.chat.ChatService;
import com.datatalk.datatalkai.chat.UserAiContextService;
import com.datatalk.datatalkai.model.dto.ApiResponse;
import com.datatalk.datatalkai.reporting.AccessPolicyService;
import com.datatalk.datatalkai.reporting.ReportFilters;
import com.datatalk.datatalkai.reporting.ReportPage;
import com.datatalk.datatalkai.reporting.UserScope;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI chat endpoints. The user identity and data scope always come from the server-side session.
 */
@RestController
@RequestMapping("/api/chat")
public class ChatApiController {

    private final AccessPolicyService accessPolicy;
    private final ChatService chatService;
    private final UserAiContextService contextService;

    public ChatApiController(AccessPolicyService accessPolicy, ChatService chatService,
                             UserAiContextService contextService) {
        this.accessPolicy = accessPolicy;
        this.chatService = chatService;
        this.contextService = contextService;
    }

    /** Request body for /api/chat/ask. */
    public record AskRequest(String question, String dateFrom, String dateTo, String status, Integer pageSize) {
    }

    @PostMapping("/ask")
    public ApiResponse<ChatResult> ask(@RequestBody AskRequest request, HttpSession session) {
        UserScope scope = accessPolicy.resolve(session);
        ReportFilters filters = ReportFilters.of(request.dateFrom(), request.dateTo(), request.status());
        ChatResult result = chatService.ask(scope, request.question(), filters, request.pageSize());
        return ApiResponse.success(result, result.status().name());
    }

    @GetMapping("/{chatId}/results")
    public ApiResponse<ReportPage> results(@PathVariable int chatId,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(required = false) Integer pageSize,
                                           @RequestParam(required = false) String dateFrom,
                                           @RequestParam(required = false) String dateTo,
                                           @RequestParam(required = false) String status,
                                           HttpSession session) {
        UserScope scope = accessPolicy.resolve(session);
        ReportFilters filters = ReportFilters.of(dateFrom, dateTo, status);
        ReportPage result = chatService.results(scope, chatId, page, pageSize, filters);
        contextService.saveFilters(scope.userId(), blankToNull(dateFrom), blankToNull(dateTo), blankToNull(status));
        return ApiResponse.success(result);
    }

    @GetMapping("/history")
    public ApiResponse<List<ChatRecord>> history(@RequestParam(required = false) String conversationId,
                                                 @RequestParam(defaultValue = "50") int limit,
                                                 HttpSession session) {
        UserScope scope = accessPolicy.resolve(session);
        String conv = "current".equals(conversationId) ? contextService.currentConversationId(scope.userId()) : conversationId;
        return ApiResponse.success(chatService.history(scope, conv, Math.min(Math.max(limit, 1), 200)));
    }

    @PostMapping("/conversation/new")
    public ApiResponse<Map<String, String>> newConversation(HttpSession session) {
        UserScope scope = accessPolicy.resolve(session);
        return ApiResponse.success(Map.of("conversationId", contextService.startNewConversation(scope.userId())),
                "New conversation started");
    }

    @GetMapping("/ai-info")
    public ApiResponse<List<Map<String, Object>>> aiInfo(HttpSession session) {
        accessPolicy.resolve(session);
        return ApiResponse.success(chatService.aiProviders());
    }

    @GetMapping("/context")
    public ApiResponse<JsonNode> context(HttpSession session) {
        UserScope scope = accessPolicy.resolve(session);
        return ApiResponse.success(contextService.get(scope.userId()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}

package com.datatalk.datatalkai.controller;

import com.datatalk.datatalkai.chat.ChatResult;
import com.datatalk.datatalkai.chat.ChatService;
import com.datatalk.datatalkai.model.dto.ApiResponse;
import com.datatalk.datatalkai.reporting.AccessPolicyService;
import com.datatalk.datatalkai.reporting.ReportFilters;
import com.datatalk.datatalkai.reporting.UserScope;
import com.datatalk.datatalkai.reports.PredefinedReport;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Predefined reports the logged-in user is authorized to run.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportApiController {

    private final AccessPolicyService accessPolicy;
    private final ChatService chatService;

    public ReportApiController(AccessPolicyService accessPolicy, ChatService chatService) {
        this.accessPolicy = accessPolicy;
        this.chatService = chatService;
    }

    /** Report summary without SQL template. */
    public record ReportInfo(String id, String category, String name, String description,
                             List<PredefinedReport.Parameter> parameters, List<String> examples, String chartType) {
    }

    public record RunRequest(Map<String, String> params, String dateFrom, String dateTo, String status, Integer pageSize) {
    }

    @GetMapping
    public ApiResponse<List<ReportInfo>> list(HttpSession session) {
        UserScope scope = accessPolicy.resolve(session);
        return ApiResponse.success(chatService.availableReports(scope).stream()
                .map(r -> new ReportInfo(r.id(), r.category(), r.name(), r.description(), r.parameters(),
                        r.examples(), r.chartType()))
                .toList());
    }

    @PostMapping("/{reportId}/run")
    public ApiResponse<ChatResult> run(@PathVariable String reportId,
                                       @RequestBody(required = false) RunRequest request,
                                       HttpSession session) {
        UserScope scope = accessPolicy.resolve(session);
        RunRequest r = request == null ? new RunRequest(Map.of(), null, null, null, null) : request;
        ReportFilters filters = ReportFilters.of(r.dateFrom(), r.dateTo(), r.status());
        ChatResult result = chatService.runReport(scope, reportId, r.params(), filters, r.pageSize());
        return ApiResponse.success(result, result.status().name());
    }
}

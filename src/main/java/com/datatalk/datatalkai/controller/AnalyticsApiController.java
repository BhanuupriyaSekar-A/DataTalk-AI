package com.datatalk.datatalkai.controller;

import com.datatalk.datatalkai.analytics.QueryAnalyticsService;
import com.datatalk.datatalkai.model.dto.ApiResponse;
import com.datatalk.datatalkai.reporting.AccessPolicyService;
import com.datatalk.datatalkai.reporting.ReportAccessException;
import com.datatalk.datatalkai.reporting.UserScope;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * ADMIN-only Query Analytics API. Also protected by SecurityConfig (/api/admin/** requires ROLE_ADMIN);
 * the role is re-checked here from the database.
 */
@RestController
@RequestMapping("/api/admin/analytics")
public class AnalyticsApiController {

    private final AccessPolicyService accessPolicy;
    private final QueryAnalyticsService analyticsService;

    public AnalyticsApiController(AccessPolicyService accessPolicy, QueryAnalyticsService analyticsService) {
        this.accessPolicy = accessPolicy;
        this.analyticsService = analyticsService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> summary(@RequestParam(defaultValue = "30") int days, HttpSession session) {
        UserScope scope = accessPolicy.resolve(session);
        if (!scope.isAdmin()) {
            throw new ReportAccessException(HttpStatus.FORBIDDEN, "ADMIN_ONLY", "Query Analytics is available to administrators only.");
        }
        return ApiResponse.success(analyticsService.summary(days));
    }
}

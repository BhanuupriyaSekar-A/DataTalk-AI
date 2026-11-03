package com.datatalk.datatalkai.controller;

import com.datatalk.datatalkai.model.dto.UserInfoResponse;
import com.datatalk.datatalkai.model.dto.ApiResponse;
import com.datatalk.datatalkai.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpSession;
import java.util.Map;

/**
 * REST API Controller for dashboard data
 */
@RestController
@RequestMapping("/datatalk/api/dashboard")
public class DashboardApiController {

    @Autowired
    private DashboardService dashboardService;

    /**
     * Get dashboard summary for current user
     */
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDashboardSummary(HttpSession session) {
        Integer customerId = (Integer) session.getAttribute("customerId");
        String role = (String) session.getAttribute("role");
        Integer userId = (Integer) session.getAttribute("userId");

        if (userId == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("NOT_AUTHENTICATED", "User not authenticated"));
        }

        Map<String, Object> summary;

        if ("ADMIN".equals(role)) {
            // ADMIN users see system-wide summary
            summary = dashboardService.getSystemDashboard();
        } else if (customerId != null) {
            // Non-admin users see their customer summary
            summary = dashboardService.getCustomerDashboard(customerId);
        } else {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("NO_CUSTOMER", "User has no associated customer"));
        }

        return ResponseEntity.ok(ApiResponse.success(summary, "Dashboard summary retrieved"));
    }
}

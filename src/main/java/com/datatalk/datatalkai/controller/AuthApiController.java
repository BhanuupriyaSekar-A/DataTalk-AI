package com.datatalk.datatalkai.controller;

import com.datatalk.datatalkai.model.dto.LoginRequest;
import com.datatalk.datatalkai.model.dto.UserInfoResponse;
import com.datatalk.datatalkai.model.dto.ApiResponse;
import com.datatalk.datatalkai.service.AuthenticationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import java.util.List;
import java.util.Optional;

/**
 * REST API Controller for authentication
 * Provides login, logout, and user info endpoints
 */
@RestController
@RequestMapping("/api")
public class AuthApiController {

    @Autowired
    private AuthenticationService authenticationService;

    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    /**
     * Login API endpoint
     * Accepts username and password, returns user info on success
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<UserInfoResponse>> login(
            @RequestBody LoginRequest loginRequest,
            HttpServletRequest request,
            HttpServletResponse response,
            HttpSession session) {

        // Validate input
        if (loginRequest.getUsername() == null || loginRequest.getUsername().trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("INVALID_USERNAME", "Username is required"));
        }

        if (loginRequest.getPassword() == null || loginRequest.getPassword().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("INVALID_PASSWORD", "Password is required"));
        }

        // Get client IP address
        String clientIp = getClientIp(request);

        // Authenticate user
        Optional<UserInfoResponse> userOpt = authenticationService.authenticate(
                loginRequest.getUsername(),
                loginRequest.getPassword(),
                clientIp
        );

        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("INVALID_CREDENTIALS", "Invalid username or password"));
        }

        UserInfoResponse userInfo = userOpt.get();

        // Prevent session fixation, then register the login with Spring Security so that
        // protected pages and APIs (anyRequest().authenticated()) accept this session.
        request.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                userInfo.getUsername(), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + userInfo.getRole()))));
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        // Store user info in session
        session.setAttribute("userId", userInfo.getUserId());
        session.setAttribute("username", userInfo.getUsername());
        session.setAttribute("role", userInfo.getRole());
        session.setAttribute("customerId", userInfo.getCustomerId());
        session.setAttribute("userInfo", userInfo);

        return ResponseEntity.ok(ApiResponse.success(userInfo, "Login successful"));
    }

    /**
     * Get current user info endpoint
     */
    @GetMapping("/user-info")
    public ResponseEntity<ApiResponse<UserInfoResponse>> getUserInfo(HttpSession session) {
        Integer userId = (Integer) session.getAttribute("userId");

        if (userId == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("NOT_AUTHENTICATED", "User not authenticated"));
        }

        Optional<UserInfoResponse> userOpt = authenticationService.getUserInfo(userId);

        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("USER_NOT_FOUND", "User not found"));
        }

        return ResponseEntity.ok(ApiResponse.success(userOpt.get()));
    }

    /**
     * Logout API endpoint
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout(HttpSession session) {
        Integer userId = (Integer) session.getAttribute("userId");

        if (userId != null) {
            authenticationService.logout(userId);
        }

        SecurityContextHolder.clearContext();
        session.invalidate();
        return ResponseEntity.ok(ApiResponse.success("", "Logout successful"));
    }

    /**
     * Helper method to get client IP address
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip.split(",")[0].trim();
    }
}

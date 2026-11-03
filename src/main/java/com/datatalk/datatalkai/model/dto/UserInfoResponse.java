package com.datatalk.datatalkai.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * User Info Response DTO
 * Returns essential user information after login
 * Does NOT include password or password hash
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserInfoResponse {
    private Integer userId;
    private String username;
    private String fullName;
    private String email;
    private String role;
    private Integer customerId;
    private String customerName;
    private String status;
}

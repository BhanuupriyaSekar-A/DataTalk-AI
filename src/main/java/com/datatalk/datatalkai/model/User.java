package com.datatalk.datatalkai.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * User entity representing an application user
 * Passwords are stored as BCrypt hashes, never plain text
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private Integer userId;
    private String username;
    private String passwordHash;  // BCrypt hashed, never plain text
    private String fullName;
    private String email;
    private String role;  // ADMIN, BUSINESS, MANAGER, CUSTOMER
    private Integer customerId;  // NULL for ADMIN users
    private String status;  // ACTIVE, INACTIVE, LOCKED, PENDING
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;

    // Helper method for security
    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    public boolean isActiveUser() {
        return "ACTIVE".equals(status);
    }
}

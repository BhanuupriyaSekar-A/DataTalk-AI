package com.datatalk.datatalkai.service;

import com.datatalk.datatalkai.model.User;
import com.datatalk.datatalkai.model.dto.UserInfoResponse;
import com.datatalk.datatalkai.repository.UserRepository;
import com.datatalk.datatalkai.repository.CustomerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.Optional;

/**
 * Authentication Service
 * Handles user login, password verification, and user lookup
 */
@Service
public class AuthenticationService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Authenticate user by username and password
     * Returns UserInfoResponse if successful, Optional.empty() if failed
     */
    public Optional<UserInfoResponse> authenticate(String username, String password, String ipAddress) {
        // Find user by username
        Optional<User> userOpt = userRepository.findByUsername(username);

        if (userOpt.isEmpty()) {
            // Log failed login attempt
            userRepository.logLoginAttempt(0, ipAddress, "FAILED", "User not found or inactive");
            return Optional.empty();
        }

        User user = userOpt.get();

        // Verify password
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            // Log failed login attempt
            userRepository.logLoginAttempt(user.getUserId(), ipAddress, "FAILED", "Invalid password");
            return Optional.empty();
        }

        // Check if user is active
        if (!"ACTIVE".equals(user.getStatus())) {
            userRepository.logLoginAttempt(user.getUserId(), ipAddress, "INACTIVE_USER", "User is not active");
            return Optional.empty();
        }

        // Log successful login
        userRepository.logLoginAttempt(user.getUserId(), ipAddress, "SUCCESS", null);

        // Build response
        UserInfoResponse response = UserInfoResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .customerId(user.getCustomerId())
                .status(user.getStatus())
                .build();

        // Add customer name if user is associated with a customer
        if (user.getCustomerId() != null) {
            customerRepository.findById(user.getCustomerId())
                    .ifPresent(customer -> response.setCustomerName(customer.getCustomerName()));
        }

        return Optional.of(response);
    }

    /**
     * Get user info by user ID
     */
    public Optional<UserInfoResponse> getUserInfo(Integer userId) {
        Optional<User> userOpt = userRepository.findById(userId);

        if (userOpt.isEmpty()) {
            return Optional.empty();
        }

        User user = userOpt.get();
        UserInfoResponse response = UserInfoResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .customerId(user.getCustomerId())
                .status(user.getStatus())
                .build();

        // Add customer name if user is associated with a customer
        if (user.getCustomerId() != null) {
            customerRepository.findById(user.getCustomerId())
                    .ifPresent(customer -> response.setCustomerName(customer.getCustomerName()));
        }

        return Optional.of(response);
    }

    /**
     * Log user logout
     */
    public void logout(Integer userId) {
        userRepository.logLogout(userId);
    }
}

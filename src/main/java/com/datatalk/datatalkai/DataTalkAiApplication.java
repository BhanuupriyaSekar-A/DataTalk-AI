package com.datatalk.datatalkai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * SL Talk AI - Natural Language Business Data Analytics
 * POC Phase 1: Database Setup, Login, and Basic Dashboard
 */
@SpringBootApplication
public class DataTalkAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataTalkAiApplication.class, args);
    }

    /**
     * BCrypt Password Encoder Bean
     * Provides secure password hashing for user authentication
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

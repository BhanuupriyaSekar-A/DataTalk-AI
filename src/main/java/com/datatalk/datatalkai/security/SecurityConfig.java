package com.datatalk.datatalkai.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * Spring Security Configuration
 * Configures URL access, CSRF protection, session management, and logout
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Authorize requests
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers("/datatalk/login", "/datatalk/api/login", "/datatalk/static/**").permitAll()
                        .requestMatchers("/datatalk/health", "/datatalk/info").permitAll()
                        .anyRequest().authenticated()
                )
                // Login form
                .formLogin(form -> form
                        .loginPage("/datatalk/login")
                        .loginProcessingUrl("/datatalk/login")
                        .defaultSuccessUrl("/datatalk/dashboard", true)
                        .failureUrl("/datatalk/login?error=true")
                        .permitAll()
                )
                // Logout
                .logout(logout -> logout
                        .logoutUrl("/datatalk/logout")
                        .logoutSuccessUrl("/datatalk/login?logout=true")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )
                // CSRF Protection
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                )
                // Session Configuration
                .sessionManagement(session -> session
                        .sessionFixationProtection(org.springframework.security.config.annotation.web.configurers.SessionManagementConfigurer.SessionFixationProtectionStrategy.newSession)
                        .sessionConcurrency(concurrency -> concurrency
                                .maximumSessions(1)
                                .expiredUrl("/datatalk/login?expired=true")
                        )
                );

        return http.build();
    }
}

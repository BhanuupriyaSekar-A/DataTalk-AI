package com.datatalk.datatalkai.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import javax.servlet.http.HttpSession;

/**
 * Web Page Controller
 * Handles navigation to HTML pages
 */
@Controller
@RequestMapping("/datatalk")
public class PageController {

    /**
     * Login page
     */
    @GetMapping("/login")
    public String login() {
        return "login";
    }

    /**
     * Dashboard page
     */
    @GetMapping("/dashboard")
    public String dashboard(HttpSession session) {
        Integer userId = (Integer) session.getAttribute("userId");
        if (userId == null) {
            return "redirect:/datatalk/login";
        }
        return "dashboard";
    }

    /**
     * AI Chat page
     */
    @GetMapping("/chat")
    public String chat(HttpSession session) {
        Integer userId = (Integer) session.getAttribute("userId");
        if (userId == null) {
            return "redirect:/datatalk/login";
        }
        return "chat";
    }

    /**
     * POC Information page
     */
    @GetMapping("/poc-info")
    public String pocInfo() {
        return "poc-info";
    }

    /**
     * Root redirect
     */
    @GetMapping("")
    public String root() {
        return "redirect:/datatalk/login";
    }
}

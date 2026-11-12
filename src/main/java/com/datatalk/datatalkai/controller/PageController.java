package com.datatalk.datatalkai.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import jakarta.servlet.http.HttpSession;

/**
 * Web Page Controller
 * Handles navigation to HTML pages
 */
@Controller
@RequestMapping("")
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
            return "redirect:/login";
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
            return "redirect:/login";
        }
        return "chat";
    }

    /**
     * Query Analytics page (ADMIN only; also enforced by SecurityConfig and the analytics API)
     */
    @GetMapping("/analytics")
    public String analytics(HttpSession session) {
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }
        if (!"ADMIN".equals(session.getAttribute("role"))) {
            return "redirect:/dashboard";
        }
        return "analytics";
    }

    /**
     * Customer management page (ADMIN only; also enforced by SecurityConfig and the customer API)
     */
    @GetMapping("/customers")
    public String customers(HttpSession session, Model model) {
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }
        if (!"ADMIN".equals(session.getAttribute("role"))) {
            return "redirect:/dashboard";
        }
        return recordPage(model, "customers", "Customers");
    }

    /**
     * Devices, locations and transactions pages (data access is enforced by their APIs)
     */
    @GetMapping("/devices")
    public String devices(HttpSession session, Model model) {
        return session.getAttribute("userId") == null ? "redirect:/login" : recordPage(model, "devices", "Devices");
    }

    @GetMapping("/locations")
    public String locations(HttpSession session, Model model) {
        return session.getAttribute("userId") == null ? "redirect:/login" : recordPage(model, "locations", "Locations");
    }

    @GetMapping("/transactions")
    public String transactions(HttpSession session, Model model) {
        return session.getAttribute("userId") == null ? "redirect:/login" : recordPage(model, "transactions", "Transactions");
    }

    private static String recordPage(Model model, String page, String title) {
        model.addAttribute("page", page);
        model.addAttribute("title", title);
        return "records";
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
        return "redirect:/login";
    }
}

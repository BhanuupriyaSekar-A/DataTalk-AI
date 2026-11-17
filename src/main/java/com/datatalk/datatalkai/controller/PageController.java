package com.datatalk.datatalkai.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;
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
     * Home page: central Application / Report search (the landing page after login).
     * ?mode=app|report&q=... shows the search results view of the same page.
     */
    @GetMapping("/home")
    public String home(HttpSession session) {
        return session.getAttribute("userId") == null ? "redirect:/login" : "home";
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
     * The former AI Chat page: AI chat now lives in the Home page's Report Search mode.
     * Old links keep working (?report=id runs an instant report, ?ask=text prefills a question).
     */
    @GetMapping("/chat")
    public String chat(@RequestParam(required = false) String report,
                       @RequestParam(required = false) String name,
                       @RequestParam(required = false) String ask) {
        UriComponentsBuilder target = UriComponentsBuilder.fromPath("/home").queryParam("mode", "report");
        if (report != null) target.queryParam("report", report);
        if (name != null) target.queryParam("name", name);
        if (ask != null) target.queryParam("ask", ask);
        return "redirect:" + target.encode().build().toUriString();
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
            return "redirect:/home";
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
            return "redirect:/home";
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
     * Root redirect: signed-in users go to Home, others to the login page
     */
    @GetMapping({"", "/"})
    public String root(HttpSession session) {
        return session.getAttribute("userId") == null ? "redirect:/login" : "redirect:/home";
    }
}

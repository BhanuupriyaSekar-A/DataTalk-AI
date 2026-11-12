package com.datatalk.datatalkai.controller;

import com.datatalk.datatalkai.model.Customer;
import com.datatalk.datatalkai.model.dto.ApiResponse;
import com.datatalk.datatalkai.reporting.AccessPolicyService;
import com.datatalk.datatalkai.reporting.ReportAccessException;
import com.datatalk.datatalkai.repository.CustomerRepository;
import com.datatalk.datatalkai.repository.SearchTerm;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Customer list / create / edit for administrators (also restricted to ADMIN by SecurityConfig: /api/admin/**).
 */
@RestController
@RequestMapping("/api/admin/customers")
public class CustomerAdminApiController {

    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE", "SUSPENDED", "ARCHIVED");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MAX_RESULTS = 200;

    private final AccessPolicyService accessPolicy;
    private final CustomerRepository customerRepository;

    public CustomerAdminApiController(AccessPolicyService accessPolicy, CustomerRepository customerRepository) {
        this.accessPolicy = accessPolicy;
        this.customerRepository = customerRepository;
    }

    public record CustomerRequest(String customerName, String email, String phone, String status) {
    }

    @GetMapping
    public ApiResponse<List<Customer>> list(@RequestParam(defaultValue = "") String q, HttpSession session) {
        requireAdmin(session);
        return ApiResponse.success(customerRepository.search(SearchTerm.of(q), MAX_RESULTS));
    }

    /** Read-only customer view: customer plus device/location/transaction summary. */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> get(@PathVariable int id, HttpSession session) {
        requireAdmin(session);
        return ApiResponse.success(Map.of("customer", find(id), "summary", customerRepository.getDatabaseSummary(id)));
    }

    @PostMapping
    public ApiResponse<Customer> create(@RequestBody CustomerRequest request, HttpSession session) {
        requireAdmin(session);
        Customer customer = validated(request);
        try {
            return ApiResponse.success(find(customerRepository.insert(customer)), "Customer created");
        } catch (DuplicateKeyException e) {
            throw duplicateEmail();
        }
    }

    @PutMapping("/{id}")
    public ApiResponse<Customer> update(@PathVariable int id, @RequestBody CustomerRequest request,
                                        HttpSession session) {
        requireAdmin(session);
        find(id);
        Customer customer = validated(request);
        customer.setCustomerId(id);
        try {
            customerRepository.update(customer);
        } catch (DuplicateKeyException e) {
            throw duplicateEmail();
        }
        return ApiResponse.success(find(id), "Customer updated");
    }

    private void requireAdmin(HttpSession session) {
        if (!accessPolicy.resolve(session).isAdmin()) {
            throw new ReportAccessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only administrators can manage customers");
        }
    }

    private Customer find(int id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ReportAccessException(HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND", "Customer not found"));
    }

    private static ReportAccessException duplicateEmail() {
        return new ReportAccessException(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "Another customer already uses this email");
    }

    private static Customer validated(CustomerRequest r) {
        if (r == null) {
            throw new IllegalArgumentException("Customer details are required");
        }
        String name = r.customerName() == null ? "" : r.customerName().trim();
        String email = r.email() == null ? "" : r.email().trim().toLowerCase(Locale.ROOT);
        String phone = r.phone() == null || r.phone().isBlank() ? null : r.phone().trim();
        String status = r.status() == null || r.status().isBlank() ? "ACTIVE" : r.status().trim().toUpperCase(Locale.ROOT);
        if (name.isEmpty() || name.length() > 255) {
            throw new IllegalArgumentException("Customer name is required (max 255 characters)");
        }
        if (email.length() > 255 || !EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("A valid email is required");
        }
        if (phone != null && (phone.length() > 20 || !phone.matches("[0-9+()\\-. A-Za-z]+"))) {
            throw new IllegalArgumentException("Phone must be at most 20 characters (digits, spaces, + - ( ) .)");
        }
        if (!STATUSES.contains(status)) {
            throw new IllegalArgumentException("Status must be one of " + STATUSES);
        }
        return new Customer(null, name, email, phone, status, null);
    }
}

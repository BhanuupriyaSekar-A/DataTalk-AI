package com.datatalk.datatalkai.controller;

import com.datatalk.datatalkai.model.Customer;
import com.datatalk.datatalkai.model.dto.ApiResponse;
import com.datatalk.datatalkai.reporting.AccessPolicyService;
import com.datatalk.datatalkai.repository.CustomerRepository;
import com.datatalk.datatalkai.repository.RecordRepository;
import com.datatalk.datatalkai.repository.RecordScope;
import com.datatalk.datatalkai.repository.SearchTerm;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Global search over customers, devices, transactions and locations, limited to what the user may open
 * (same scope as the record pages). Menus, actions and reports are matched in the browser. No AI is involved.
 */
@RestController
@RequestMapping("/api/search")
public class SearchApiController {

    private static final int LIMIT = 6;

    private final AccessPolicyService accessPolicy;
    private final CustomerRepository customerRepository;
    private final RecordRepository records;

    public SearchApiController(AccessPolicyService accessPolicy, CustomerRepository customerRepository,
                               RecordRepository records) {
        this.accessPolicy = accessPolicy;
        this.customerRepository = customerRepository;
        this.records = records;
    }

    public record SearchResults(List<Customer> customers, List<Map<String, Object>> devices,
                                List<Map<String, Object>> transactions, List<Map<String, Object>> locations) {
    }

    @GetMapping
    public ApiResponse<SearchResults> search(@RequestParam(defaultValue = "") String q, HttpSession session) {
        RecordScope scope = RecordScope.of(accessPolicy.resolve(session));
        SearchTerm term = SearchTerm.of(q);
        if (term.isEmpty() || (!term.isId() && term.text().length() < 2)) {
            return ApiResponse.success(new SearchResults(List.of(), List.of(), List.of(), List.of()));
        }
        // The customer pages are ADMIN only, so only ADMIN gets customer results.
        List<Customer> customers = scope.admin() ? customerRepository.search(term, LIMIT) : List.of();
        List<Map<String, Object>> devices = scope.canRead("device.device")
                ? records.devices(term, null, scope, LIMIT) : List.of();
        List<Map<String, Object>> transactions = term.isId() && scope.canRead("txn.payment_transaction")
                ? records.transaction(term.id(), scope).stream().toList() : List.of();
        List<Map<String, Object>> locations = scope.canRead("location.location")
                ? records.locations(term, null, scope, LIMIT) : List.of();
        return ApiResponse.success(new SearchResults(customers, devices, transactions, locations));
    }
}

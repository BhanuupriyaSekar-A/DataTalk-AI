package com.datatalk.datatalkai.controller;

import com.datatalk.datatalkai.model.dto.ApiResponse;
import com.datatalk.datatalkai.reporting.AccessPolicyService;
import com.datatalk.datatalkai.reporting.ReportAccessException;
import com.datatalk.datatalkai.reporting.UserScope;
import com.datatalk.datatalkai.repository.CustomerRepository;
import com.datatalk.datatalkai.repository.RecordRepository;
import com.datatalk.datatalkai.repository.RecordScope;
import com.datatalk.datatalkai.repository.SearchTerm;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Devices, locations and transactions pages. Reads use the same scope as reporting (customer, assigned devices,
 * role tables); creating devices/locations is ADMIN only (/api/admin/** is also restricted by SecurityConfig).
 */
@RestController
@RequestMapping("/api")
public class RecordApiController {

    private static final int LIST_LIMIT = 200;
    private static final int DETAIL_LIMIT = 20;
    private static final Set<String> DEVICE_TYPES = Set.of("POS_TERMINAL", "MOBILE_READER", "PAYMENT_GATEWAY", "ATM");
    private static final Set<String> DEVICE_STATUSES = Set.of("ACTIVE", "INACTIVE", "MAINTENANCE", "DECOMMISSIONED", "ERROR");

    private final AccessPolicyService accessPolicy;
    private final RecordRepository records;
    private final CustomerRepository customers;

    public RecordApiController(AccessPolicyService accessPolicy, RecordRepository records, CustomerRepository customers) {
        this.accessPolicy = accessPolicy;
        this.records = records;
        this.customers = customers;
    }

    public record DeviceRequest(String serialNumber, Integer customerId, Integer locationId, String deviceType,
                                String firmwareVersion, String status) {
    }

    public record LocationRequest(Integer customerId, String locationName, String city, String state, String timezone) {
    }

    // ---------- devices ----------

    @GetMapping("/devices")
    public ApiResponse<List<Map<String, Object>>> devices(@RequestParam(defaultValue = "") String q,
                                                          @RequestParam(required = false) Integer locationId,
                                                          HttpSession session) {
        RecordScope scope = scope(session, "device.device");
        return ApiResponse.success(records.devices(SearchTerm.of(q), locationId, scope, LIST_LIMIT));
    }

    @GetMapping("/devices/{id}")
    public ApiResponse<Map<String, Object>> device(@PathVariable int id, HttpSession session) {
        RecordScope scope = scope(session, "device.device");
        Map<String, Object> device = records.device(id, scope).orElseThrow(() -> notFound("Device"));
        Map<String, Object> body = new LinkedHashMap<>(Map.of("device", device));
        if (scope.canRead("device.device_status_log")) {
            body.put("events", records.deviceEvents(id, DETAIL_LIMIT));
        }
        if (scope.canRead("txn.payment_transaction")) {
            body.put("transactions", records.transactions(SearchTerm.of(""), id, null, scope, DETAIL_LIMIT));
        }
        return ApiResponse.success(body);
    }

    @PostMapping("/admin/devices")
    public ApiResponse<Map<String, Object>> createDevice(@RequestBody DeviceRequest r, HttpSession session) {
        RecordScope scope = adminScope(session);
        String serial = required(r.serialNumber(), "Serial number", 100);
        String type = upper(r.deviceType());
        String status = r.status() == null || r.status().isBlank() ? "INACTIVE" : upper(r.status());
        String firmware = optional(r.firmwareVersion(), "Firmware version", 50);
        if (r.customerId() == null || customers.findById(r.customerId()).isEmpty()) {
            throw new IllegalArgumentException("Select an existing customer");
        }
        if (r.locationId() == null || !records.locationBelongsTo(r.locationId(), r.customerId())) {
            throw new IllegalArgumentException("Select a location of the chosen customer");
        }
        if (!DEVICE_TYPES.contains(type)) {
            throw new IllegalArgumentException("Device type must be one of " + DEVICE_TYPES);
        }
        if (!DEVICE_STATUSES.contains(status)) {
            throw new IllegalArgumentException("Status must be one of " + DEVICE_STATUSES);
        }
        try {
            int id = records.insertDevice(serial, r.customerId(), r.locationId(), type, firmware, status);
            return ApiResponse.success(records.device(id, scope).orElseThrow(), "Device created");
        } catch (DuplicateKeyException e) {
            throw new ReportAccessException(HttpStatus.CONFLICT, "DUPLICATE_SERIAL", "A device with this serial number already exists");
        }
    }

    // ---------- locations ----------

    @GetMapping("/locations")
    public ApiResponse<List<Map<String, Object>>> locations(@RequestParam(defaultValue = "") String q,
                                                            @RequestParam(required = false) Integer customerId,
                                                            HttpSession session) {
        RecordScope scope = scope(session, "location.location");
        return ApiResponse.success(records.locations(SearchTerm.of(q), customerId, scope, LIST_LIMIT));
    }

    @GetMapping("/locations/{id}")
    public ApiResponse<Map<String, Object>> location(@PathVariable int id, HttpSession session) {
        RecordScope scope = scope(session, "location.location");
        Map<String, Object> location = records.location(id, scope).orElseThrow(() -> notFound("Location"));
        Map<String, Object> body = new LinkedHashMap<>(Map.of("location", location));
        if (scope.canRead("device.device")) {
            body.put("devices", records.devices(SearchTerm.of(""), id, scope, LIST_LIMIT));
        }
        if (scope.canRead("txn.payment_transaction")) {
            body.put("transactions", records.transactions(SearchTerm.of(""), null, id, scope, DETAIL_LIMIT));
        }
        return ApiResponse.success(body);
    }

    @PostMapping("/admin/locations")
    public ApiResponse<Map<String, Object>> createLocation(@RequestBody LocationRequest r, HttpSession session) {
        RecordScope scope = adminScope(session);
        if (r.customerId() == null || customers.findById(r.customerId()).isEmpty()) {
            throw new IllegalArgumentException("Select an existing customer");
        }
        String name = required(r.locationName(), "Location name", 255);
        String city = optional(r.city(), "City", 100);
        String state = optional(r.state(), "State", 100);
        String timezone = r.timezone() == null || r.timezone().isBlank() ? "UTC" : r.timezone().trim();
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Unknown timezone (use e.g. America/New_York or UTC)");
        }
        int id = records.insertLocation(r.customerId(), name, city, state, timezone);
        return ApiResponse.success(records.location(id, scope).orElseThrow(), "Location created");
    }

    // ---------- transactions ----------

    @GetMapping("/transactions")
    public ApiResponse<List<Map<String, Object>>> transactions(@RequestParam(defaultValue = "") String q,
                                                               HttpSession session) {
        RecordScope scope = scope(session, "txn.payment_transaction");
        return ApiResponse.success(records.transactions(SearchTerm.of(q), null, null, scope, LIST_LIMIT));
    }

    @GetMapping("/transactions/{id}")
    public ApiResponse<Map<String, Object>> transaction(@PathVariable int id, HttpSession session) {
        RecordScope scope = scope(session, "txn.payment_transaction");
        return ApiResponse.success(records.transaction(id, scope).orElseThrow(() -> notFound("Transaction")));
    }

    // ---------- helpers ----------

    private RecordScope scope(HttpSession session, String table) {
        RecordScope scope = RecordScope.of(accessPolicy.resolve(session));
        if (!scope.canRead(table)) {
            throw new ReportAccessException(HttpStatus.FORBIDDEN, "UNAUTHORIZED_TABLE", "Your role cannot view this data");
        }
        return scope;
    }

    private RecordScope adminScope(HttpSession session) {
        UserScope scope = accessPolicy.resolve(session);
        if (!scope.isAdmin()) {
            throw new ReportAccessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only administrators can do this");
        }
        return RecordScope.of(scope);
    }

    private static ReportAccessException notFound(String what) {
        return new ReportAccessException(HttpStatus.NOT_FOUND, "NOT_FOUND", what + " not found");
    }

    private static String required(String value, String field, int max) {
        String v = optional(value, field, max);
        if (v == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return v;
    }

    private static String optional(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        if (v.length() > max) {
            throw new IllegalArgumentException(field + " must be at most " + max + " characters");
        }
        return v;
    }

    private static String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}

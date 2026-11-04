package com.datatalk.datatalkai.reporting;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Set;

/**
 * Optional UI filters (date range and payment transaction status).
 * Applied by Java inside the security scope of each table, never by the AI.
 */
public record ReportFilters(LocalDate dateFrom, LocalDate dateTo, String status) {

    public static final Set<String> ALLOWED_STATUSES = Set.of("APPROVED", "DECLINED", "FAILED", "REFUNDED");

    public static ReportFilters none() {
        return new ReportFilters(null, null, null);
    }

    /** Parses raw request values; invalid values raise SqlValidationException (code INVALID_FILTER). */
    public static ReportFilters of(String dateFrom, String dateTo, String status) {
        LocalDate from = parseDate(dateFrom, "dateFrom");
        LocalDate to = parseDate(dateTo, "dateTo");
        if (from != null && to != null && from.isAfter(to)) {
            throw new SqlValidationException("INVALID_FILTER", "'From' date must be before 'To' date");
        }
        String st = null;
        if (status != null && !status.isBlank()) {
            st = status.trim().toUpperCase(Locale.ROOT);
            if (!ALLOWED_STATUSES.contains(st)) {
                throw new SqlValidationException("INVALID_FILTER", "Unknown status filter: " + status);
            }
        }
        return new ReportFilters(from, to, st);
    }

    public boolean isEmpty() {
        return dateFrom == null && dateTo == null && status == null;
    }

    private static LocalDate parseDate(String value, String name) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new SqlValidationException("INVALID_FILTER", "Invalid " + name + " (expected yyyy-MM-dd)");
        }
    }
}

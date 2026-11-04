package com.datatalk.datatalkai.export;

import java.util.Locale;

/** Supported export formats. PDF is the default. */
public enum ExportFormat {
    PDF("pdf", "application/pdf"),
    EXCEL("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final String extension;
    private final String contentType;

    ExportFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }

    public static ExportFormat from(String value) {
        if (value == null || value.isBlank()) {
            return PDF;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "pdf" -> PDF;
            case "excel", "xlsx" -> EXCEL;
            default -> throw new IllegalArgumentException("Unsupported export format: " + value + " (use pdf or excel)");
        };
    }
}

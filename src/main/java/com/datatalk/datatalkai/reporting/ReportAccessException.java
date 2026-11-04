package com.datatalk.datatalkai.reporting;

import org.springframework.http.HttpStatus;

/**
 * Raised for authentication/authorization/not-found problems in the reporting APIs.
 * Converted to an ApiResponse error by ApiExceptionHandler.
 */
public class ReportAccessException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ReportAccessException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}

package com.datatalk.datatalkai.reporting;

/**
 * Raised when a validated query fails at execution time (timeout, database error, role not configured).
 */
public class ReportExecutionException extends RuntimeException {

    public ReportExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}

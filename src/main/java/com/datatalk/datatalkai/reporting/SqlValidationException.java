package com.datatalk.datatalkai.reporting;

/**
 * Raised when AI-generated (or predefined) SQL fails the safety / permission checks.
 * The query is never executed when this is thrown.
 */
public class SqlValidationException extends RuntimeException {

    private final String code;

    public SqlValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}

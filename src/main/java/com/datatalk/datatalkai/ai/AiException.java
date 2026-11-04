package com.datatalk.datatalkai.ai;

/**
 * Raised when the AI provider cannot produce a usable answer (network, timeout, HTTP error, bad JSON).
 */
public class AiException extends RuntimeException {

    public AiException(String message) {
        super(message);
    }

    public AiException(String message, Throwable cause) {
        super(message, cause);
    }
}

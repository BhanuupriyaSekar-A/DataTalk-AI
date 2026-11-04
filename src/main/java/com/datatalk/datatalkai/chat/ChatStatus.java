package com.datatalk.datatalkai.chat;

/**
 * Outcome of a chat question. The database column app.chat_history.query_status only allows
 * PENDING / EXECUTED / FAILED / INVALID_SQL (Phase 1 constraint), so each outcome maps to one of those
 * and the detailed code is kept in error_message as "[CODE] message".
 */
public enum ChatStatus {
    SUCCESS("EXECUTED"),
    REJECTED("INVALID_SQL"),
    NOT_ANSWERABLE("FAILED"),
    AI_ERROR("FAILED"),
    EXECUTION_ERROR("FAILED");

    private final String dbValue;

    ChatStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }
}

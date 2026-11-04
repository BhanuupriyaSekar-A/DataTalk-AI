package com.datatalk.datatalkai.chat;

import com.datatalk.datatalkai.reporting.ReportPage;

/**
 * Response of a chat question / predefined report run.
 *
 * @param status       SUCCESS, REJECTED, NOT_ANSWERABLE, AI_ERROR or EXECUTION_ERROR
 * @param source       AI, CACHE or PREDEFINED
 * @param aiProvider   provider:model that produced the SQL (CACHE / PREDEFINED when no AI call was made)
 * @param sql          the validated SQL (shown in the optional "SQL details" section)
 * @param message      clarification or error message for non-successful answers
 * @param page         first page of real results (null when not successful)
 * @param aiFailures   providers that failed before the answering one (non-empty when a fallback was used)
 */
public record ChatResult(Integer chatId,
                         String conversationId,
                         String question,
                         ChatStatus status,
                         String source,
                         String aiProvider,
                         String understanding,
                         String title,
                         String explanation,
                         String chartType,
                         String sql,
                         String errorCode,
                         String message,
                         ReportPage page,
                         long durationMs,
                         java.util.List<String> aiFailures) {
}

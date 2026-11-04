package com.datatalk.datatalkai.chat;

import java.time.LocalDateTime;

/**
 * One row of app.chat_history (never contains report data, only the question, SQL and metadata).
 */
public record ChatRecord(int chatId,
                         int userId,
                         String conversationId,
                         String question,
                         String understanding,
                         String sql,
                         String queryStatus,
                         String title,
                         String explanation,
                         String chartType,
                         Integer rowCount,
                         String errorMessage,
                         String aiProvider,
                         String source,
                         Integer durationMs,
                         LocalDateTime createdAt) {

    public boolean isSuccess() {
        return ChatStatus.SUCCESS.dbValue().equals(queryStatus);
    }
}

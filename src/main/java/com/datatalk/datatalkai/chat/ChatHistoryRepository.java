package com.datatalk.datatalkai.chat;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

/**
 * Data access for app.chat_history. Every read is filtered by user_id so users only see their own chats.
 */
@Repository
public class ChatHistoryRepository {

    private static final String COLUMNS = "chat_id, user_id, conversation_id, user_question, ai_interpretation, "
            + "generated_sql, query_status, report_title, explanation, chart_type, row_count, error_message, "
            + "ai_provider, source, duration_ms, created_at";

    private static final RowMapper<ChatRecord> MAPPER = (rs, i) -> {
        Timestamp created = rs.getTimestamp("created_at");
        return new ChatRecord(
                rs.getInt("chat_id"),
                rs.getInt("user_id"),
                rs.getString("conversation_id"),
                rs.getString("user_question"),
                rs.getString("ai_interpretation"),
                rs.getString("generated_sql"),
                rs.getString("query_status"),
                rs.getString("report_title"),
                rs.getString("explanation"),
                rs.getString("chart_type"),
                (Integer) rs.getObject("row_count"),
                rs.getString("error_message"),
                rs.getString("ai_provider"),
                rs.getString("source"),
                (Integer) rs.getObject("duration_ms"),
                created == null ? null : created.toLocalDateTime());
    };

    private final JdbcTemplate jdbc;

    public ChatHistoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int insert(ChatRecord r) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO app.chat_history (user_id, conversation_id, "
                    + "user_question, ai_interpretation, generated_sql, query_status, report_title, explanation, "
                    + "chart_type, row_count, error_message, ai_provider, source, duration_ms) "
                    + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)", new String[]{"chat_id"});
            ps.setInt(1, r.userId());
            ps.setString(2, r.conversationId());
            ps.setString(3, r.question());
            ps.setString(4, r.understanding());
            ps.setString(5, r.sql());
            ps.setString(6, r.queryStatus());
            ps.setString(7, r.title());
            ps.setString(8, r.explanation());
            ps.setString(9, r.chartType());
            ps.setObject(10, r.rowCount(), Types.INTEGER);
            ps.setString(11, r.errorMessage());
            ps.setString(12, r.aiProvider());
            ps.setString(13, r.source());
            ps.setObject(14, r.durationMs(), Types.INTEGER);
            return ps;
        }, keys);
        return keys.getKey().intValue();
    }

    public Optional<ChatRecord> findForUser(int chatId, int userId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM app.chat_history WHERE chat_id = ? AND user_id = ?",
                MAPPER, chatId, userId).stream().findFirst();
    }

    public List<ChatRecord> listForUser(int userId, String conversationId, int limit) {
        if (conversationId != null && !conversationId.isBlank()) {
            return jdbc.query("SELECT " + COLUMNS + " FROM app.chat_history WHERE user_id = ? AND conversation_id = ? "
                    + "ORDER BY created_at, chat_id LIMIT ?", MAPPER, userId, conversationId, limit);
        }
        return jdbc.query("SELECT " + COLUMNS + " FROM app.chat_history WHERE user_id = ? "
                + "ORDER BY created_at DESC, chat_id DESC LIMIT ?", MAPPER, userId, limit);
    }

    /** Most recent successful AI answer to the same question by the same user (for SQL reuse). */
    public Optional<ChatRecord> findReusable(int userId, String normalizedQuestion, int maxAgeDays) {
        return jdbc.query("SELECT " + COLUMNS + " FROM app.chat_history WHERE user_id = ? "
                        + "AND lower(regexp_replace(trim(user_question), '\\s+', ' ', 'g')) = ? "
                        + "AND query_status = 'EXECUTED' AND source IN ('AI', 'CACHE') AND generated_sql IS NOT NULL "
                        + "AND created_at > NOW() - (? * INTERVAL '1 day') ORDER BY created_at DESC LIMIT 1",
                MAPPER, userId, normalizedQuestion, maxAgeDays).stream().findFirst();
    }
}

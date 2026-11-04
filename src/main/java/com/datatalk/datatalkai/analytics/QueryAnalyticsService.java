package com.datatalk.datatalkai.analytics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Basic Query Analytics for administrators, calculated from app.chat_history (and app.export_job when present).
 * Timing notes (no separate timing columns exist):
 * - "AI answer time" = average end-to-end duration of successful AI-generated answers (AI call + validation + SQL).
 * - "SQL execution time" = average duration of cached-SQL and predefined-report answers, which make no AI call
 *   (validation + SQL execution only).
 */
@Slf4j
@Service
public class QueryAnalyticsService {

    /** WHERE clause limiting rows to the last N days (0 = all time). Values are bound as parameters. */
    private static final String PERIOD = " WHERE (? = 0 OR created_at >= NOW() - (? * INTERVAL '1 day')) ";

    private final JdbcTemplate jdbc;

    public QueryAnalyticsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> summary(int days) {
        int d = Math.max(0, Math.min(days, 3650));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("periodDays", d);
        result.put("totals", jdbc.queryForMap("""
                SELECT COUNT(*) AS total_questions,
                       COUNT(*) FILTER (WHERE query_status = 'EXECUTED') AS successful,
                       COUNT(*) FILTER (WHERE query_status <> 'EXECUTED') AS failed,
                       COUNT(DISTINCT user_id) AS active_users,
                       ROUND(AVG(duration_ms) FILTER (WHERE source = 'AI' AND query_status = 'EXECUTED')) AS avg_ai_answer_ms,
                       ROUND(AVG(duration_ms) FILTER (WHERE source IN ('CACHE', 'PREDEFINED') AND query_status = 'EXECUTED')) AS avg_sql_execution_ms
                FROM app.chat_history""" + PERIOD, d, d));
        result.put("bySource", jdbc.queryForList("""
                SELECT COALESCE(source, 'AI') AS source, COUNT(*) AS questions,
                       COUNT(*) FILTER (WHERE query_status = 'EXECUTED') AS successful,
                       ROUND(AVG(duration_ms)) AS avg_ms
                FROM app.chat_history""" + PERIOD + " GROUP BY 1 ORDER BY 2 DESC", d, d));
        result.put("failureReasons", jdbc.queryForList("""
                SELECT COALESCE(substring(error_message FROM '^\\[([A-Z_]+)\\]'), query_status) AS reason, COUNT(*) AS count
                FROM app.chat_history""" + PERIOD + " AND query_status <> 'EXECUTED' GROUP BY 1 ORDER BY 2 DESC", d, d));
        result.put("popularReports", jdbc.queryForList("""
                SELECT report_title, COALESCE(source, 'AI') AS source, COUNT(*) AS runs, MAX(created_at) AS last_run
                FROM app.chat_history""" + PERIOD + """
                 AND query_status = 'EXECUTED' AND report_title IS NOT NULL
                GROUP BY 1, 2 ORDER BY 3 DESC, 4 DESC LIMIT 10""", d, d));
        result.put("providerUsage", jdbc.queryForList("""
                SELECT COALESCE(ai_provider, 'unknown') AS provider, COUNT(*) AS questions,
                       COUNT(*) FILTER (WHERE query_status = 'EXECUTED') AS successful,
                       COUNT(*) FILTER (WHERE query_status <> 'EXECUTED') AS failed,
                       ROUND(AVG(duration_ms)) AS avg_ms
                FROM app.chat_history""" + PERIOD + " AND COALESCE(source, 'AI') = 'AI' GROUP BY 1 ORDER BY 2 DESC", d, d));
        result.put("exports", exportStats(d));
        return result;
    }

    private Object exportStats(int d) {
        try {
            return jdbc.queryForList("""
                    SELECT export_format, job_status, COUNT(*) AS count
                    FROM app.export_job
                    WHERE (? = 0 OR created_date >= NOW() - (? * INTERVAL '1 day'))
                    GROUP BY 1, 2 ORDER BY 1, 2""", d, d);
        } catch (DataAccessException e) {
            log.debug("Export statistics unavailable: {}", e.getMostSpecificCause().getMessage());
            return List.of();
        }
    }
}

package com.datatalk.datatalkai.export;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.Map;
import java.util.Optional;

/**
 * Persists export job information in app.export_job (database/phase3-migration.sql).
 * Persistence is best effort: if the table is missing or the database call fails, exports still work
 * and a warning is logged.
 */
@Slf4j
@Repository
public class ExportJobRepository {

    private final JdbcTemplate jdbc;

    public ExportJobRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(String jobId, int userId, Integer chatId, String reportName, ExportFormat format,
                       String status, String delivery) {
        run("insert", () -> jdbc.update("INSERT INTO app.export_job (job_id, user_id, chat_id, report_name, "
                        + "export_format, job_status, delivery) VALUES (?,?,?,?,?,?,?)",
                ps -> {
                    ps.setString(1, jobId);
                    ps.setInt(2, userId);
                    ps.setObject(3, chatId, Types.INTEGER);
                    ps.setString(4, reportName.length() > 255 ? reportName.substring(0, 255) : reportName);
                    ps.setString(5, format.name());
                    ps.setString(6, status);
                    ps.setString(7, delivery);
                }));
    }

    public void complete(String jobId, String status, Long rows, String fileLocation, String error) {
        run("update", () -> jdbc.update("UPDATE app.export_job SET job_status = ?, row_count = ?, file_location = ?, "
                        + "error_message = ?, completed_date = CURRENT_TIMESTAMP WHERE job_id = ?",
                status, rows == null ? null : (int) Math.min(Integer.MAX_VALUE, rows), fileLocation, error, jobId));
    }

    public void markExpired(String jobId) {
        run("expire", () -> jdbc.update("UPDATE app.export_job SET job_status = 'EXPIRED' "
                + "WHERE job_id = ? AND job_status = 'COMPLETED'", jobId));
    }

    /** Stored job owned by the user (used after an application restart). */
    public Optional<Map<String, Object>> findForUser(String jobId, int userId) {
        try {
            return jdbc.queryForList("SELECT job_id, report_name, export_format, job_status, row_count, created_date, "
                    + "file_location, error_message FROM app.export_job WHERE job_id = ? AND user_id = ?",
                    jobId, userId).stream().findFirst();
        } catch (DataAccessException e) {
            log.warn("Could not read export job {}: {}", jobId, e.getMostSpecificCause().getMessage());
            return Optional.empty();
        }
    }

    private void run(String action, Runnable r) {
        try {
            r.run();
        } catch (DataAccessException e) {
            log.warn("Export job persistence ({}) failed - run database/phase3-migration.sql? {}", action,
                    e.getMostSpecificCause().getMessage());
        }
    }
}

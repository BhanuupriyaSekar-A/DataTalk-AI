package com.datatalk.datatalkai.reporting;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Executes validated report queries safely:
 * <ul>
 *   <li>inside a READ ONLY transaction,</li>
 *   <li>after SET LOCAL ROLE to the SELECT-only role (datatalk_ro),</li>
 *   <li>with a statement timeout and JDBC query timeout,</li>
 *   <li>with pagination (LIMIT/OFFSET) or a hard row cap for exports.</li>
 * </ul>
 * Only SecureQuery objects produced by SqlSafetyValidator are accepted.
 */
@Service
public class ReportExecutionService {

    /** Receives streamed rows (used by exports). */
    public interface RowSink {
        void start(List<String> columns, List<String> columnTypes);

        void row(List<Object> values);
    }

    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbc;
    private final JdbcTemplate streamingJdbc;
    private final TransactionTemplate transactionTemplate;
    private final String readOnlyRole;
    private final int timeoutSeconds;
    private final int defaultPageSize;
    private final int maxPageSize;

    public ReportExecutionService(DataSource dataSource,
                                  PlatformTransactionManager transactionManager,
                                  @Value("${app.reporting.read-only-role}") String readOnlyRole,
                                  @Value("${app.reporting.statement-timeout-seconds:30}") int timeoutSeconds,
                                  @Value("${app.reporting.default-page-size:25}") int defaultPageSize,
                                  @Value("${app.reporting.max-page-size:100}") int maxPageSize) {
        if (!readOnlyRole.matches("[a-z_][a-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid read-only role name: " + readOnlyRole);
        }
        this.jdbc = new JdbcTemplate(dataSource);
        this.jdbc.setQueryTimeout(timeoutSeconds + 5);
        this.streamingJdbc = new JdbcTemplate(dataSource);
        this.streamingJdbc.setQueryTimeout(timeoutSeconds + 5);
        this.streamingJdbc.setFetchSize(500);
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyRole = readOnlyRole;
        this.timeoutSeconds = timeoutSeconds;
        this.defaultPageSize = defaultPageSize;
        this.maxPageSize = maxPageSize;
    }

    public int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return defaultPageSize;
        }
        return Math.min(pageSize, maxPageSize);
    }

    /** Returns one page of results plus the total row count. Never loads the full result set. */
    public ReportPage executePage(SecureQuery query, int page, Integer pageSize) {
        int size = normalizePageSize(pageSize);
        return runReadOnly(() -> {
            Long total = jdbc.queryForObject("SELECT count(*) FROM (" + query.executableSql() + ") AS dt_count", Long.class);
            long totalRows = total == null ? 0 : total;
            int totalPages = (int) Math.max(1, (totalRows + size - 1) / size);
            int current = Math.min(Math.max(1, page), totalPages);
            long offset = (long) (current - 1) * size;
            String sql = query.executableSql() + " LIMIT " + size + " OFFSET " + offset;
            return jdbc.query(sql, (ResultSetExtractor<ReportPage>) rs -> {
                List<String> columns = new ArrayList<>();
                List<String> types = new ArrayList<>();
                readMetadata(rs, columns, types);
                List<List<Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(readRow(rs, columns.size()));
                }
                return new ReportPage(columns, types, rows, totalRows, current, size, totalPages);
            });
        });
    }

    public long count(SecureQuery query) {
        return runReadOnly(() -> {
            Long total = jdbc.queryForObject("SELECT count(*) FROM (" + query.executableSql() + ") AS dt_count", Long.class);
            return total == null ? 0L : total;
        });
    }

    /** Streams up to maxRows rows to the sink (exports). */
    public void stream(SecureQuery query, int maxRows, RowSink sink) {
        runReadOnly(() -> streamingJdbc.query(query.executableSql() + " LIMIT " + maxRows,
                (ResultSetExtractor<Void>) rs -> {
                    List<String> columns = new ArrayList<>();
                    List<String> types = new ArrayList<>();
                    readMetadata(rs, columns, types);
                    sink.start(columns, types);
                    while (rs.next()) {
                        sink.row(readRow(rs, columns.size()));
                    }
                    return null;
                }));
    }

    private <T> T runReadOnly(Supplier<T> work) {
        try {
            return transactionTemplate.execute(status -> {
                jdbc.execute("SET TRANSACTION READ ONLY");
                jdbc.execute("SET LOCAL statement_timeout = '" + timeoutSeconds + "s'");
                jdbc.execute("SET LOCAL ROLE " + readOnlyRole);
                return work.get();
            });
        } catch (DataAccessException e) {
            throw new ReportExecutionException(friendlyMessage(e), e);
        }
    }

    private String friendlyMessage(DataAccessException e) {
        String msg = e.getMostSpecificCause().getMessage();
        if (msg == null) {
            return "The report query failed";
        }
        if (msg.contains("statement timeout") || msg.contains("canceling statement")) {
            return "The query took longer than " + timeoutSeconds + " seconds and was cancelled. Try a narrower question or date range.";
        }
        if (msg.contains("permission denied to set role") || msg.contains("role \"" + readOnlyRole + "\" does not exist")) {
            return "The read-only reporting role is not configured. Run database/phase2-migration.sql as the database owner.";
        }
        if (msg.contains("permission denied")) {
            return "Access denied by the read-only reporting role";
        }
        String firstLine = msg.split("\\R", 2)[0].replaceFirst("^ERROR:\\s*", "");
        return "Database error: " + firstLine;
    }

    private static void readMetadata(ResultSet rs, List<String> columns, List<String> types) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        for (int i = 1; i <= md.getColumnCount(); i++) {
            columns.add(md.getColumnLabel(i));
            types.add(typeOf(md.getColumnType(i)));
        }
    }

    private static List<Object> readRow(ResultSet rs, int columnCount) throws SQLException {
        List<Object> row = new ArrayList<>(columnCount);
        for (int i = 1; i <= columnCount; i++) {
            row.add(convert(rs.getObject(i)));
        }
        return row;
    }

    private static String typeOf(int sqlType) {
        return switch (sqlType) {
            case Types.NUMERIC, Types.DECIMAL, Types.INTEGER, Types.BIGINT, Types.SMALLINT, Types.TINYINT,
                 Types.DOUBLE, Types.FLOAT, Types.REAL -> "number";
            case Types.DATE, Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE, Types.TIME -> "date";
            default -> "text";
        };
    }

    static Object convert(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp ts) {
            return ts.toLocalDateTime().format(TS_FORMAT);
        }
        if (value instanceof BigDecimal || value instanceof Integer || value instanceof Long
                || value instanceof Short || value instanceof Double || value instanceof Float
                || value instanceof Boolean || value instanceof String) {
            return value;
        }
        return value.toString();
    }
}

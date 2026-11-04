package com.datatalk.datatalkai.reporting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Reads approved schemas, tables, columns and COMMENT descriptions dynamically from
 * PostgreSQL metadata (information_schema + pg_description) and builds the ReportingCatalog.
 * The catalog is cached after the first successful load.
 */
@Service
public class SchemaMetadataService {

    private static final Logger log = LoggerFactory.getLogger(SchemaMetadataService.class);

    /** Low-cardinality, non-sensitive columns whose distinct values help the AI write correct filters. */
    private static final Set<String> CATEGORICAL_COLUMNS = Set.of(
            "status", "transaction_status", "card_type", "entry_mode", "device_type", "event_type");

    private static final String METADATA_SQL = """
            SELECT c.table_schema, c.table_name, c.column_name, c.data_type,
                   obj_description(format('%I.%I', c.table_schema, c.table_name)::regclass, 'pg_class') AS table_comment,
                   col_description(format('%I.%I', c.table_schema, c.table_name)::regclass, c.ordinal_position) AS column_comment
            FROM information_schema.columns c
            JOIN information_schema.tables t
              ON t.table_schema = c.table_schema AND t.table_name = c.table_name AND t.table_type = 'BASE TABLE'
            WHERE c.table_schema = ANY (?)
            ORDER BY c.table_schema, c.table_name, c.ordinal_position
            """;

    private final JdbcTemplate jdbcTemplate;
    private final List<String> approvedSchemas;
    private final Set<String> blockedColumns;
    private volatile ReportingCatalog cached;

    public SchemaMetadataService(JdbcTemplate jdbcTemplate,
                                 @Value("${app.reporting.approved-schemas}") List<String> approvedSchemas,
                                 @Value("${app.reporting.blocked-columns:}") List<String> blockedColumns) {
        this.jdbcTemplate = jdbcTemplate;
        this.approvedSchemas = approvedSchemas.stream().map(String::trim).toList();
        this.blockedColumns = new HashSet<>(blockedColumns.stream().map(String::trim).toList());
    }

    public ReportingCatalog getCatalog() {
        ReportingCatalog c = cached;
        if (c == null) {
            synchronized (this) {
                if (cached == null) {
                    cached = load();
                }
                c = cached;
            }
        }
        return c;
    }

    /** Forces the metadata to be read again (e.g. after a schema change). */
    public synchronized void refresh() {
        cached = null;
    }

    private ReportingCatalog load() {
        Map<String, String> tableComments = new LinkedHashMap<>();
        Map<String, List<ReportingCatalog.ColumnInfo>> columns = new LinkedHashMap<>();

        jdbcTemplate.query(con -> {
            var ps = con.prepareStatement(METADATA_SQL);
            ps.setArray(1, con.createArrayOf("text", approvedSchemas.toArray()));
            return ps;
        }, rs -> {
            String table = rs.getString("table_schema") + "." + rs.getString("table_name");
            String column = rs.getString("column_name");
            if (blockedColumns.contains(table + "." + column)) {
                return;
            }
            tableComments.putIfAbsent(table, rs.getString("table_comment"));
            columns.computeIfAbsent(table, k -> new ArrayList<>())
                    .add(new ReportingCatalog.ColumnInfo(column, rs.getString("data_type"), rs.getString("column_comment")));
        });

        List<ReportingCatalog.TableInfo> tables = new ArrayList<>();
        Map<String, List<String>> categorical = new HashMap<>();
        for (var entry : columns.entrySet()) {
            String[] parts = entry.getKey().split("\\.", 2);
            var info = new ReportingCatalog.TableInfo(parts[0], parts[1], tableComments.get(entry.getKey()), entry.getValue());
            // Fail closed: a table that cannot be scoped to a customer is never reportable.
            if (!info.hasColumn("customer_id") && !info.hasColumn("device_id")) {
                log.info("Skipping table {} (no customer_id/device_id column for access scoping)", entry.getKey());
                continue;
            }
            tables.add(info);
            for (var col : info.columns()) {
                if (CATEGORICAL_COLUMNS.contains(col.name())) {
                    categorical.put(entry.getKey() + "." + col.name(), distinctValues(entry.getKey(), col.name()));
                }
            }
        }
        log.info("Reporting catalog loaded: {} tables", tables.size());
        return new ReportingCatalog(tables, categorical);
    }

    private List<String> distinctValues(String qualifiedTable, String column) {
        // Identifiers come from information_schema of the approved schemas, not from user input.
        String sql = "SELECT DISTINCT " + column + " FROM " + qualifiedTable
                + " WHERE " + column + " IS NOT NULL ORDER BY 1 LIMIT 20";
        try {
            return jdbcTemplate.queryForList(sql, String.class);
        } catch (Exception e) {
            log.warn("Could not read values of {}.{}: {}", qualifiedTable, column, e.getMessage());
            return List.of();
        }
    }
}

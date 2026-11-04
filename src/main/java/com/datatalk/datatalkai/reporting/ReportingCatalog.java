package com.datatalk.datatalkai.reporting;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Approved reporting tables and columns (with their PostgreSQL COMMENT descriptions).
 * Built dynamically from database metadata by SchemaMetadataService; immutable afterwards.
 * Only tables that can be scoped to a customer (customer_id or device_id column) are included.
 */
public class ReportingCatalog {

    public record ColumnInfo(String name, String dataType, String comment) {
    }

    public record TableInfo(String schema, String name, String comment, List<ColumnInfo> columns) {

        public String qualifiedName() {
            return schema + "." + name;
        }

        public boolean hasColumn(String column) {
            return columns.stream().anyMatch(c -> c.name().equals(column));
        }
    }

    private final Map<String, TableInfo> tables = new LinkedHashMap<>();
    private final Map<String, List<String>> categoricalValues;
    private final Set<String> allColumnNames = new HashSet<>();

    public ReportingCatalog(Collection<TableInfo> tableInfos, Map<String, List<String>> categoricalValues) {
        for (TableInfo t : tableInfos) {
            tables.put(t.qualifiedName(), t);
            t.columns().forEach(c -> allColumnNames.add(c.name()));
        }
        this.categoricalValues = categoricalValues == null ? Map.of() : Map.copyOf(categoricalValues);
    }

    public Optional<TableInfo> table(String qualifiedName) {
        return Optional.ofNullable(tables.get(qualifiedName));
    }

    /** Resolves an unqualified table name if exactly one approved table has that name. */
    public Optional<TableInfo> resolveUnqualified(String tableName) {
        List<TableInfo> matches = tables.values().stream().filter(t -> t.name().equals(tableName)).toList();
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }

    public Set<String> tableNames() {
        return Collections.unmodifiableSet(tables.keySet());
    }

    public Set<String> allColumnNames() {
        return Collections.unmodifiableSet(allColumnNames);
    }

    public boolean isEmpty() {
        return tables.isEmpty();
    }

    /** Compact schema description for the AI prompt (only tables the user may query). */
    public String describe(Set<String> allowedTables) {
        StringBuilder sb = new StringBuilder();
        for (TableInfo t : tables.values()) {
            if (!allowedTables.contains(t.qualifiedName())) {
                continue;
            }
            sb.append("TABLE ").append(t.qualifiedName());
            if (t.comment() != null) {
                sb.append(" -- ").append(t.comment());
            }
            sb.append('\n');
            for (ColumnInfo c : t.columns()) {
                sb.append("  ").append(c.name()).append(' ').append(c.dataType());
                if (c.comment() != null) {
                    sb.append(" -- ").append(c.comment());
                }
                List<String> values = categoricalValues.get(t.qualifiedName() + "." + c.name());
                if (values != null && !values.isEmpty()) {
                    sb.append(" [values: ").append(String.join(", ", values)).append(']');
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    public String columnList(String qualifiedName) {
        return tables.get(qualifiedName).columns().stream().map(ColumnInfo::name).collect(Collectors.joining(", "));
    }
}

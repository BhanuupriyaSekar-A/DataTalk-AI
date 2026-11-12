package com.datatalk.datatalkai.reporting;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.util.TablesNamesFinder;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Validates AI-generated SQL with a PostgreSQL-compatible SQL parser (JSqlParser) and rewrites it
 * so that it can only read the data the logged-in user is allowed to see.
 *
 * <p>Checks (any failure = query rejected, never executed):
 * <ol>
 *   <li>Exactly one statement, and it must be a SELECT (CTEs allowed, no RECURSIVE / INTO / FOR UPDATE).</li>
 *   <li>Every table must be an approved reporting table that the user's role may access.</li>
 *   <li>Every column must be an approved (non-sensitive) column or an alias defined in the query.</li>
 *   <li>Every function must be on the allow-list (blocks pg_sleep, pg_read_file, dblink, set_config, ...).</li>
 *   <li>Non-admin users cannot reference another customer_id literal.</li>
 * </ol>
 *
 * <p>Rewrite: each table reference (e.g. {@code txn.payment_transaction}) is replaced by a Java-generated
 * CTE ({@code dt_txn_payment_transaction}) that exposes only approved columns and only the rows of the
 * user's customer / assigned devices, plus the UI date/status filters. Customer identity always comes from
 * the server-side session, never from the AI.
 */
public class SqlSafetyValidator {

    static final Set<String> ALLOWED_FUNCTIONS = Set.of(
            "count", "sum", "avg", "min", "max", "round", "coalesce", "nullif", "greatest", "least",
            "abs", "ceil", "ceiling", "floor", "sign", "trunc", "mod", "power", "sqrt",
            "date_trunc", "date_part", "extract", "to_char", "to_date", "to_timestamp", "now", "age",
            "make_date", "make_interval", "date", "generate_series",
            "lower", "upper", "initcap", "trim", "btrim", "ltrim", "rtrim", "concat", "concat_ws", "length",
            "substring", "substr", "left", "right", "replace", "split_part", "string_agg", "position",
            "stddev", "stddev_pop", "stddev_samp", "variance", "percentile_cont", "percentile_disc", "mode",
            "bool_and", "bool_or", "every", "array_agg",
            "row_number", "rank", "dense_rank", "percent_rank", "cume_dist", "ntile", "lag", "lead",
            "first_value", "last_value", "nth_value");

    /** SQL keywords and type names that may legitimately be followed by "(" in a SELECT. */
    private static final Set<String> PAREN_KEYWORDS = Set.of(
            "in", "exists", "over", "filter", "values", "as", "from", "join", "any", "all", "some", "cast",
            "array", "when", "and", "or", "not", "on", "select", "where", "by", "then", "else", "lateral",
            "using", "distinct", "row", "interval", "between", "like", "ilike", "is", "case", "with", "union",
            "except", "intersect", "group", "order", "partition", "within", "having", "limit", "offset", "end",
            "numeric", "decimal", "varchar", "char", "character", "timestamp", "time", "float", "double",
            "int", "integer", "bigint", "smallint", "text", "real", "precision", "varying", "boolean");

    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
            "insert", "update", "delete", "merge", "drop", "alter", "create", "truncate", "grant", "revoke",
            "copy", "call", "do", "execute", "vacuum", "analyze", "reindex", "cluster", "set", "reset", "lock",
            "listen", "notify", "unlisten", "comment", "security", "into", "prepare", "deallocate", "declare",
            "refresh", "checkpoint", "discard", "load", "import", "savepoint", "rollback", "commit");

    private static final Set<String> SPECIAL_IDENTIFIERS = Set.of(
            "true", "false", "null", "current_date", "current_timestamp", "current_time",
            "localtimestamp", "localtime");

    private static final Set<String> SYSTEM_SCHEMAS = Set.of(
            "pg_catalog", "information_schema", "pg_toast", "public", "app", "app_user");

    /** Date columns used by the UI date filter, and the column used by the status filter. */
    private static final Map<String, String> DATE_FILTER_COLUMNS = Map.of(
            "txn.payment_transaction", "transaction_time",
            "txn.settlement", "settlement_date",
            "device.device_status_log", "event_time",
            "device.device_health_metric", "recorded_time",
            "txn.transaction_fee", "fee_time",
            "txn.refund", "requested_time");
    private static final Map<String, String> STATUS_FILTER_COLUMNS = Map.of(
            "txn.payment_transaction", "transaction_status");

    private static final Pattern IDENT_PAREN = Pattern.compile("\\b([a-z_][a-z0-9_$]*)\\s*\\(");
    private static final Pattern WORD = Pattern.compile("\\b([a-z_][a-z0-9_$]*)\\b");
    private static final Pattern SYSTEM_REF = Pattern.compile("\\b(pg_[a-z0-9_]*)\\b");

    private final int maxSqlLength;

    public SqlSafetyValidator(int maxSqlLength) {
        this.maxSqlLength = maxSqlLength;
    }

    public SecureQuery validate(String sql, UserScope scope, ReportingCatalog catalog, ReportFilters filters) {
        if (sql == null || sql.isBlank()) {
            throw new SqlValidationException("EMPTY_SQL", "No SQL query was generated");
        }
        if (sql.length() > maxSqlLength) {
            throw new SqlValidationException("SQL_TOO_LONG", "Generated SQL is too long");
        }
        if (sql.contains("$$")) {
            throw new SqlValidationException("INVALID_SQL", "Dollar-quoted strings are not allowed");
        }
        if (!scope.isAdmin() && scope.customerId() == null) {
            throw new SqlValidationException("NO_CUSTOMER", "Your account is not linked to a customer");
        }
        String trimmed = sql.trim().replaceAll(";+\\s*$", "");

        Statement statement = parseSingleStatement(trimmed);
        if (!(statement instanceof Select select)) {
            throw new SqlValidationException("NOT_SELECT",
                    "Only SELECT queries are allowed (got " + statement.getClass().getSimpleName().toUpperCase() + ")");
        }

        Inspector inspector = new Inspector(scope);
        try {
            inspector.getTables((Statement) select);
        } catch (SqlValidationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new SqlValidationException("UNSUPPORTED_SQL", "Unsupported SQL construct: " + e.getMessage());
        }

        for (String cte : inspector.cteNames) {
            if (cte.startsWith("dt_")) {
                throw new SqlValidationException("INVALID_SQL", "CTE names starting with 'dt_' are reserved");
            }
        }

        // Tables: approved + allowed for role; rewrite to scoped CTE names
        Set<String> referenced = new LinkedHashSet<>();
        for (Table t : inspector.tableRefs) {
            if (t.getNameParts() != null && t.getNameParts().size() > 2) {
                throw new SqlValidationException("RESTRICTED_TABLE", "Cross-database references are not allowed");
            }
            String schema = clean(t.getSchemaName());
            String name = clean(t.getName());
            if (schema == null && inspector.cteNames.contains(name)) {
                continue;
            }
            String display = schema == null ? name : schema + "." + name;
            Optional<ReportingCatalog.TableInfo> info = schema == null
                    ? catalog.resolveUnqualified(name)
                    : catalog.table(schema + "." + name);
            if (info.isEmpty()) {
                throw new SqlValidationException("RESTRICTED_TABLE",
                        "Table '" + display + "' is not an approved reporting table");
            }
            String qualified = info.get().qualifiedName();
            if (!scope.allowedTables().contains(qualified)) {
                throw new SqlValidationException("UNAUTHORIZED_TABLE",
                        "Your role (" + scope.role() + ") is not allowed to access '" + qualified + "'");
            }
            referenced.add(qualified);
            if (t.getAlias() == null) {
                t.setAlias(new Alias(name, false));
            }
            t.setSchemaName(null);
            t.setName(scopeName(qualified));
        }
        if (referenced.isEmpty()) {
            throw new SqlValidationException("NO_TABLES", "The query does not read any approved reporting table");
        }

        // Columns: approved columns or aliases defined in the query
        Set<String> allowedIdentifiers = new HashSet<>(catalog.allColumnNames());
        allowedIdentifiers.addAll(inspector.aliases);
        allowedIdentifiers.addAll(inspector.cteNames);
        allowedIdentifiers.addAll(SPECIAL_IDENTIFIERS);
        for (String column : inspector.columnNames) {
            if (!allowedIdentifiers.contains(column)) {
                throw new SqlValidationException("RESTRICTED_COLUMN",
                        "Column '" + column + "' is not available for reporting");
            }
        }

        for (String fn : inspector.functionNames) {
            if (!ALLOWED_FUNCTIONS.contains(fn)) {
                throw new SqlValidationException("DISALLOWED_FUNCTION", "Function '" + fn + "' is not allowed");
            }
        }

        String rewritten = select.toString();
        lexicalSafetyNet(rewritten, catalog, inspector);

        String executable = buildScopeCtes(referenced, scope, catalog, filters)
                + " SELECT * FROM (" + rewritten + ") AS dt_result";
        return new SecureQuery(trimmed, executable, referenced);
    }

    private Statement parseSingleStatement(String sql) {
        try {
            Statements statements = CCJSqlParserUtil.parseStatements(sql);
            if (statements.getStatements().size() != 1) {
                throw new SqlValidationException("MULTIPLE_STATEMENTS", "Only one SQL statement is allowed");
            }
            return statements.getStatements().get(0);
        } catch (JSQLParserException e) {
            throw new SqlValidationException("INVALID_SQL", "The generated SQL could not be parsed");
        }
    }

    /**
     * Second, independent check on the parser's own output (string literals removed): blocks
     * write/admin keywords, non-allow-listed functions, system catalogs and any approved table that
     * was not rewritten to its scoped CTE.
     */
    private void lexicalSafetyNet(String deparsed, ReportingCatalog catalog, Inspector inspector) {
        String text = deparsed.replaceAll("'(?:[^']|'')*'", "''").replace("\"", "").toLowerCase(Locale.ROOT);

        Matcher words = WORD.matcher(text);
        while (words.find()) {
            if (FORBIDDEN_KEYWORDS.contains(words.group(1))) {
                throw new SqlValidationException("FORBIDDEN_KEYWORD",
                        "Keyword '" + words.group(1).toUpperCase(Locale.ROOT) + "' is not allowed");
            }
        }
        Matcher system = SYSTEM_REF.matcher(text);
        if (system.find()) {
            throw new SqlValidationException("RESTRICTED_TABLE", "System object '" + system.group(1) + "' is not allowed");
        }
        for (String schema : SYSTEM_SCHEMAS) {
            if (Pattern.compile("\\b" + schema + "\\s*\\.").matcher(text).find()) {
                throw new SqlValidationException("RESTRICTED_TABLE", "Schema '" + schema + "' is not allowed");
            }
        }
        for (String table : catalog.tableNames()) {
            String[] p = table.split("\\.");
            if (Pattern.compile("\\b" + p[0] + "\\s*\\.\\s*" + p[1] + "\\b(?!\\s*\\()").matcher(text).find()) {
                throw new SqlValidationException("UNSUPPORTED_SQL",
                        "Table reference '" + table + "' could not be secured; please rephrase the question");
            }
        }
        Matcher calls = IDENT_PAREN.matcher(text);
        while (calls.find()) {
            String name = calls.group(1);
            if (PAREN_KEYWORDS.contains(name) || inspector.cteNames.contains(name)
                    || inspector.aliases.contains(name) || name.startsWith("dt_")) {
                continue;
            }
            if (!ALLOWED_FUNCTIONS.contains(name)) {
                throw new SqlValidationException("DISALLOWED_FUNCTION", "Function '" + name + "' is not allowed");
            }
        }
    }

    private String buildScopeCtes(Set<String> tables, UserScope scope, ReportingCatalog catalog, ReportFilters filters) {
        List<String> ctes = new ArrayList<>();
        for (String table : tables) {
            ReportingCatalog.TableInfo info = catalog.table(table).orElseThrow();
            List<String> conditions = new ArrayList<>();
            if (!scope.isAdmin()) {
                int customerId = scope.customerId();
                if (info.hasColumn("customer_id")) {
                    conditions.add("customer_id = " + customerId);
                } else {
                    conditions.add("device_id IN (SELECT device_id FROM device.device WHERE customer_id = " + customerId + ")");
                }
            }
            if (scope.allowedDeviceIds() != null && info.hasColumn("device_id")) {
                conditions.add(scope.allowedDeviceIds().isEmpty() ? "FALSE"
                        : "device_id IN (" + scope.allowedDeviceIds().stream().map(String::valueOf)
                        .collect(Collectors.joining(", ")) + ")");
            }
            if (filters != null) {
                String dateCol = DATE_FILTER_COLUMNS.get(table);
                if (dateCol != null && filters.dateFrom() != null) {
                    conditions.add(dateCol + " >= DATE '" + filters.dateFrom().format(DateTimeFormatter.ISO_LOCAL_DATE) + "'");
                }
                if (dateCol != null && filters.dateTo() != null) {
                    conditions.add(dateCol + " < DATE '" + filters.dateTo().plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE) + "'");
                }
                String statusCol = STATUS_FILTER_COLUMNS.get(table);
                if (statusCol != null && filters.status() != null
                        && ReportFilters.ALLOWED_STATUSES.contains(filters.status())) {
                    conditions.add(statusCol + " = '" + filters.status() + "'");
                }
            }
            String where = conditions.isEmpty() ? "TRUE" : String.join(" AND ", conditions);
            ctes.add(scopeName(table) + " AS (SELECT " + catalog.columnList(table) + " FROM " + table + " WHERE " + where + ")");
        }
        return "WITH " + String.join(", ", ctes);
    }

    static String scopeName(String qualifiedTable) {
        return "dt_" + qualifiedTable.replace('.', '_');
    }

    private static String clean(String identifier) {
        if (identifier == null) {
            return null;
        }
        return identifier.replace("\"", "").toLowerCase(Locale.ROOT);
    }

    /** Walks the parsed statement and collects tables, columns, functions and aliases. */
    private static final class Inspector extends TablesNamesFinder {

        private final UserScope scope;
        final Set<Table> tableRefs = Collections.newSetFromMap(new IdentityHashMap<>());
        final Set<String> cteNames = new HashSet<>();
        final Set<String> aliases = new HashSet<>();
        final Set<String> columnNames = new HashSet<>();
        final Set<String> functionNames = new HashSet<>();
        private final Set<Object> visitedSelects = Collections.newSetFromMap(new IdentityHashMap<>());

        Inspector(UserScope scope) {
            this.scope = scope;
        }

        @Override
        public void visit(WithItem withItem) {
            if (withItem.isRecursive()) {
                throw new SqlValidationException("INVALID_SQL", "Recursive queries are not allowed");
            }
            if (withItem.getAlias() != null) {
                cteNames.add(clean(withItem.getAlias().getName()));
            }
            if (withItem.getWithItemList() != null) {
                withItem.getWithItemList().forEach(i -> aliases.add(clean(i.toString())));
            }
            super.visit(withItem);
        }

        @Override
        public void visit(PlainSelect plainSelect) {
            if (!visitedSelects.add(plainSelect)) {
                return;
            }
            if (plainSelect.getIntoTables() != null || plainSelect.getIntoTempTable() != null) {
                throw new SqlValidationException("FORBIDDEN_KEYWORD", "SELECT INTO is not allowed");
            }
            if (plainSelect.getForMode() != null || plainSelect.getForUpdateTable() != null) {
                throw new SqlValidationException("FORBIDDEN_KEYWORD", "Row locking (FOR UPDATE/SHARE) is not allowed");
            }
            if (plainSelect.getWithItemsList() != null) {
                plainSelect.getWithItemsList().forEach(w -> {
                    if (w.getAlias() != null) {
                        cteNames.add(clean(w.getAlias().getName()));
                    }
                });
            }
            if (plainSelect.getFromItem() != null && plainSelect.getFromItem().getAlias() != null) {
                addAlias(plainSelect.getFromItem().getAlias());
            }
            if (plainSelect.getJoins() != null) {
                plainSelect.getJoins().forEach(j -> {
                    if (j.getRightItem() != null && j.getRightItem().getAlias() != null) {
                        addAlias(j.getRightItem().getAlias());
                    }
                });
            }
            super.visit(plainSelect);
            // Make sure clauses that the base finder may skip are inspected as well
            if (plainSelect.getGroupBy() != null && plainSelect.getGroupBy().getGroupByExpressionList() != null) {
                plainSelect.getGroupBy().getGroupByExpressionList().accept(this);
            }
            if (plainSelect.getHaving() != null) {
                plainSelect.getHaving().accept(this);
            }
            if (plainSelect.getOrderByElements() != null) {
                plainSelect.getOrderByElements().forEach(o -> o.getExpression().accept(this));
            }
        }

        @Override
        public void visit(SetOperationList list) {
            if (list.getWithItemsList() != null) {
                list.getWithItemsList().forEach(w -> {
                    if (w.getAlias() != null) {
                        cteNames.add(clean(w.getAlias().getName()));
                    }
                });
            }
            super.visit(list);
            if (list.getOrderByElements() != null) {
                list.getOrderByElements().forEach(o -> o.getExpression().accept(this));
            }
        }

        @Override
        public void visit(ParenthesedSelect select) {
            if (select.getAlias() != null) {
                addAlias(select.getAlias());
            }
            super.visit(select);
        }

        @Override
        public void visit(Table table) {
            tableRefs.add(table);
            super.visit(table);
        }

        @Override
        public void visit(Column column) {
            columnNames.add(clean(column.getColumnName()));
            Table t = column.getTable();
            if (t != null && t.getSchemaName() != null) {
                // schema.table.column -> table.column (the table keeps its name as alias after rewrite)
                column.setTable(new Table(t.getName()));
            }
            super.visit(column);
        }

        @Override
        public void visit(SelectItem selectItem) {
            if (selectItem.getAlias() != null) {
                aliases.add(clean(selectItem.getAlias().getName()));
            }
            super.visit(selectItem);
        }

        @Override
        public void visit(Function function) {
            String name = function.getName() == null ? "" : clean(function.getName());
            if (function.getMultipartName() != null && function.getMultipartName().size() > 1) {
                throw new SqlValidationException("DISALLOWED_FUNCTION", "Schema-qualified functions are not allowed");
            }
            functionNames.add(name);
            super.visit(function);
        }

        @Override
        public void visit(AnalyticExpression expr) {
            functionNames.add(clean(expr.getName()));
            super.visit(expr);
        }

        @Override
        public void visit(TableFunction tableFunction) {
            if (tableFunction.getFunction() != null) {
                visit(tableFunction.getFunction());
            }
            if (tableFunction.getAlias() != null) {
                addAlias(tableFunction.getAlias());
            }
        }

        @Override
        public void visit(EqualsTo equalsTo) {
            checkCustomerLiteral(equalsTo.getLeftExpression(), equalsTo.getRightExpression());
            checkCustomerLiteral(equalsTo.getRightExpression(), equalsTo.getLeftExpression());
            super.visit(equalsTo);
        }

        @Override
        public void visit(InExpression in) {
            if (isCustomerColumn(in.getLeftExpression()) && in.getRightExpression() instanceof ExpressionList<?> list) {
                for (Object e : list) {
                    checkCustomerLiteral(in.getLeftExpression(), (Expression) e);
                }
            }
            super.visit(in);
        }

        private void addAlias(Alias alias) {
            aliases.add(clean(alias.getName()));
            if (alias.getAliasColumns() != null) {
                alias.getAliasColumns().forEach(ac -> aliases.add(clean(ac.name)));
            }
        }

        private void checkCustomerLiteral(Expression columnSide, Expression valueSide) {
            if (scope.isAdmin() || !isCustomerColumn(columnSide)) {
                return;
            }
            Long value = null;
            if (valueSide instanceof LongValue lv) {
                value = lv.getValue();
            } else if (valueSide instanceof StringValue sv && sv.getValue().matches("\\d+")) {
                value = Long.parseLong(sv.getValue());
            }
            if (value != null && !value.equals(scope.customerId().longValue())) {
                throw new SqlValidationException("UNAUTHORIZED_CUSTOMER",
                        "You are not allowed to access data of another customer");
            }
        }

        private static boolean isCustomerColumn(Expression e) {
            return e instanceof Column c && "customer_id".equals(clean(c.getColumnName()));
        }
    }
}

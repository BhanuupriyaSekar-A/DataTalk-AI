package com.datatalk.datatalkai.reporting;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the SQL safety layer (no database or AI needed).
 */
class SqlSafetyValidatorTest {

    private static final ReportingCatalog CATALOG = testCatalog();
    private static final Set<String> ALL_TABLES = CATALOG.tableNames();

    private final SqlSafetyValidator validator = new SqlSafetyValidator(6000);

    private static final UserScope ADMIN = new UserScope(1, "admin_user", "ADMIN", null, ALL_TABLES, null);
    private static final UserScope BUSINESS = new UserScope(2, "techpay_manager", "BUSINESS", 1, ALL_TABLES, null);
    private static final UserScope CUSTOMER = new UserScope(3, "techpay_customer", "CUSTOMER", 1,
            Set.of("txn.payment_transaction", "device.device", "device.device_status_log", "location.location"),
            List.of(1, 2, 3));

    // ---------- successful queries ----------

    @Test
    void validSelectIsScopedToUsersCustomer() {
        SecureQuery q = validator.validate(
                "SELECT l.location_name, COUNT(*) AS failed_count FROM txn.payment_transaction pt "
                        + "JOIN device.device d ON d.device_id = pt.device_id "
                        + "JOIN location.location l ON l.location_id = d.location_id "
                        + "WHERE pt.transaction_status IN ('FAILED','DECLINED') "
                        + "AND pt.transaction_time >= CURRENT_DATE - INTERVAL '30 days' "
                        + "GROUP BY l.location_name ORDER BY failed_count DESC;",
                BUSINESS, CATALOG, ReportFilters.none());

        assertThat(q.tables()).containsExactlyInAnyOrder("txn.payment_transaction", "device.device", "location.location");
        assertThat(q.executableSql())
                .startsWith("WITH dt_")
                .contains("dt_txn_payment_transaction AS (SELECT transaction_id, device_id, customer_id, amount")
                .contains("FROM txn.payment_transaction WHERE customer_id = 1")
                .contains("FROM device.device WHERE customer_id = 1")
                .contains("FROM location.location WHERE customer_id = 1")
                .doesNotContain("card_last_four");
        // the AI query body itself no longer references the raw tables
        String body = q.executableSql().substring(q.executableSql().indexOf("SELECT * FROM ("));
        assertThat(body).doesNotContain("txn.payment_transaction").contains("dt_txn_payment_transaction pt");
    }

    @Test
    void safeCteIsAllowed() {
        SecureQuery q = validator.validate(
                "WITH daily AS (SELECT date_trunc('day', transaction_time) AS day, SUM(amount) AS total "
                        + "FROM txn.payment_transaction GROUP BY 1) SELECT day, total FROM daily ORDER BY day",
                BUSINESS, CATALOG, ReportFilters.none());
        assertThat(q.executableSql()).contains("FROM dt_txn_payment_transaction");
    }

    @Test
    void adminIsNotCustomerFilteredButFiltersApply() {
        SecureQuery q = validator.validate("SELECT COUNT(*) FROM txn.payment_transaction", ADMIN, CATALOG,
                new ReportFilters(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "DECLINED"));
        assertThat(q.executableSql())
                .doesNotContain("customer_id =")
                .contains("transaction_time >= DATE '2026-10-01'")
                .contains("transaction_time < DATE '2026-11-01'")
                .contains("transaction_status = 'DECLINED'");
    }

    @Test
    void customerRoleIsRestrictedToAssignedDevices() {
        SecureQuery q = validator.validate("SELECT device_id, COUNT(*) FROM txn.payment_transaction GROUP BY device_id",
                CUSTOMER, CATALOG, ReportFilters.none());
        assertThat(q.executableSql()).contains("customer_id = 1 AND device_id IN (1, 2, 3)");
    }

    @Test
    void deviceLogWithoutCustomerColumnIsScopedThroughDevices() {
        SecureQuery q = validator.validate("SELECT event_type, COUNT(*) FROM device.device_status_log GROUP BY event_type",
                BUSINESS, CATALOG, ReportFilters.none());
        assertThat(q.executableSql())
                .contains("device_id IN (SELECT device_id FROM device.device WHERE customer_id = 1)");
    }

    @Test
    void unqualifiedTableNamesAreResolved() {
        SecureQuery q = validator.validate("SELECT COUNT(*) FROM settlement", BUSINESS, CATALOG, ReportFilters.none());
        assertThat(q.tables()).containsExactly("txn.settlement");
    }

    // ---------- invalid SQL ----------

    @Test
    void garbageSqlIsRejected() {
        assertCode("SELEC amount FROM", BUSINESS, "INVALID_SQL");
        assertCode("", BUSINESS, "EMPTY_SQL");
    }

    @Test
    void multipleStatementsAreRejected() {
        assertCode("SELECT 1 FROM txn.payment_transaction; DROP TABLE txn.payment_transaction", BUSINESS, "MULTIPLE_STATEMENTS");
    }

    @Test
    void dataModificationAndAdminStatementsAreRejected() {
        assertCode("DELETE FROM txn.payment_transaction", BUSINESS, "NOT_SELECT");
        assertCode("UPDATE txn.payment_transaction SET amount = 0", BUSINESS, "NOT_SELECT");
        assertCode("INSERT INTO txn.settlement (customer_id) VALUES (1)", BUSINESS, "NOT_SELECT");
        assertCode("DROP TABLE txn.payment_transaction", BUSINESS, "NOT_SELECT");
        assertCode("TRUNCATE txn.payment_transaction", BUSINESS, "NOT_SELECT");
        assertCode("ALTER TABLE txn.settlement ADD COLUMN x int", BUSINESS, "NOT_SELECT");
        assertCode("GRANT SELECT ON txn.settlement TO public", BUSINESS, null);
        assertCode("SELECT * INTO new_table FROM txn.payment_transaction", BUSINESS, "FORBIDDEN_KEYWORD");
        assertCode("SELECT * FROM txn.payment_transaction FOR UPDATE", BUSINESS, "FORBIDDEN_KEYWORD");
    }

    @Test
    void dangerousFunctionsAreRejected() {
        assertCode("SELECT pg_sleep(60) FROM txn.payment_transaction", BUSINESS, null);
        assertCode("SELECT pg_read_file('/etc/passwd') FROM txn.payment_transaction", BUSINESS, null);
        assertCode("SELECT set_config('role','neondb_owner',false) FROM txn.payment_transaction", BUSINESS, "DISALLOWED_FUNCTION");
        assertCode("SELECT amount FROM txn.payment_transaction ORDER BY pg_sleep(5)", BUSINESS, null);
    }

    // ---------- restricted tables / columns ----------

    @Test
    void restrictedTablesAreRejected() {
        assertCode("SELECT * FROM app.chat_history", BUSINESS, "RESTRICTED_TABLE");
        assertCode("SELECT username, password_hash FROM app_user.app_user", BUSINESS, "RESTRICTED_TABLE");
        assertCode("SELECT * FROM pg_catalog.pg_roles", BUSINESS, "RESTRICTED_TABLE");
        assertCode("SELECT * FROM information_schema.tables", BUSINESS, "RESTRICTED_TABLE");
        assertCode("SELECT amount FROM txn.payment_transaction WHERE device_id IN (SELECT device_id FROM app_user.user_device_access)",
                BUSINESS, "RESTRICTED_TABLE");
    }

    @Test
    void subqueryInOrderByIsAlsoScopedOrRejected() {
        SecureQuery q = validator.validate(
                "SELECT location_name FROM location.location l ORDER BY (SELECT COUNT(*) FROM txn.payment_transaction p WHERE p.customer_id = l.customer_id) DESC",
                BUSINESS, CATALOG, ReportFilters.none());
        String body = q.executableSql().substring(q.executableSql().indexOf("SELECT * FROM ("));
        assertThat(body).doesNotContain("txn.payment_transaction");
    }

    @Test
    void sensitiveColumnsAreRejected() {
        assertCode("SELECT card_last_four FROM txn.payment_transaction", BUSINESS, "RESTRICTED_COLUMN");
        assertCode("SELECT email, phone FROM corp.customer", BUSINESS, "RESTRICTED_COLUMN");
    }

    // ---------- permissions ----------

    @Test
    void otherCustomerLiteralIsRejected() {
        assertCode("SELECT SUM(amount) FROM txn.payment_transaction WHERE customer_id = 2", BUSINESS, "UNAUTHORIZED_CUSTOMER");
        assertCode("SELECT SUM(amount) FROM txn.payment_transaction WHERE customer_id IN (1, 3)", BUSINESS, "UNAUTHORIZED_CUSTOMER");
        // own customer id is fine
        validator.validate("SELECT SUM(amount) FROM txn.payment_transaction WHERE customer_id = 1", BUSINESS, CATALOG, ReportFilters.none());
        // admin may query any customer
        validator.validate("SELECT SUM(amount) FROM txn.payment_transaction WHERE customer_id = 2", ADMIN, CATALOG, ReportFilters.none());
    }

    @Test
    void roleWithoutTableAccessIsRejected() {
        assertCode("SELECT SUM(net_amount) FROM txn.settlement", CUSTOMER, "UNAUTHORIZED_TABLE");
        assertCode("SELECT customer_name FROM corp.customer", CUSTOMER, "UNAUTHORIZED_TABLE");
    }

    @Test
    void reservedCteNamesAreRejected() {
        assertCode("WITH dt_txn_payment_transaction AS (SELECT 1 AS x) SELECT * FROM dt_txn_payment_transaction",
                BUSINESS, "INVALID_SQL");
    }

    @Test
    void invalidFiltersAreRejected() {
        assertThatThrownBy(() -> ReportFilters.of("2026-13-01", null, null))
                .isInstanceOf(SqlValidationException.class);
        assertThatThrownBy(() -> ReportFilters.of(null, null, "HACKED'; DROP"))
                .isInstanceOf(SqlValidationException.class);
    }

    private void assertCode(String sql, UserScope scope, String expectedCode) {
        assertThatThrownBy(() -> validator.validate(sql, scope, CATALOG, ReportFilters.none()))
                .isInstanceOf(SqlValidationException.class)
                .satisfies(e -> {
                    if (expectedCode != null) {
                        assertThat(((SqlValidationException) e).getCode()).isEqualTo(expectedCode);
                    }
                });
    }

    /** Mirrors the real Neon schema (approved columns only; sensitive columns removed). */
    static ReportingCatalog testCatalog() {
        return new ReportingCatalog(List.of(
                table("corp", "customer", "customer_id", "customer_name", "status", "created_date", "updated_date"),
                table("location", "location", "location_id", "customer_id", "location_name", "city", "state", "timezone", "created_date"),
                table("device", "device", "device_id", "serial_number", "customer_id", "location_id", "device_type",
                        "firmware_version", "status", "activated_date", "last_communication_time", "created_date"),
                table("device", "device_status_log", "event_id", "device_id", "event_type", "event_time", "description", "created_date"),
                table("txn", "payment_transaction", "transaction_id", "device_id", "customer_id", "amount", "card_type",
                        "entry_mode", "transaction_status", "decline_reason", "transaction_time", "created_date"),
                table("txn", "settlement", "settlement_id", "customer_id", "settlement_date", "total_amount", "fees",
                        "net_amount", "status", "created_date")
        ), Map.of());
    }

    private static ReportingCatalog.TableInfo table(String schema, String name, String... columns) {
        return new ReportingCatalog.TableInfo(schema, name, null,
                java.util.Arrays.stream(columns).map(c -> new ReportingCatalog.ColumnInfo(c, "text", null)).toList());
    }
}

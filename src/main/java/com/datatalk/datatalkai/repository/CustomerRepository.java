package com.datatalk.datatalkai.repository;

import com.datatalk.datatalkai.model.Customer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Customer Repository - Data access layer for customer operations
 */
@Repository
public class CustomerRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String SELECT_BY_ID = 
        "SELECT customer_id, customer_name, email, phone, status, created_date FROM corp.customer WHERE customer_id = ?";

    private final RowMapper<Customer> customerRowMapper = (rs, rowNum) -> {
        Customer customer = new Customer();
        customer.setCustomerId(rs.getInt("customer_id"));
        customer.setCustomerName(rs.getString("customer_name"));
        customer.setEmail(rs.getString("email"));
        customer.setPhone(rs.getString("phone"));
        customer.setStatus(rs.getString("status"));
        customer.setCreatedDate(rs.getString("created_date"));
        return customer;
    };

    /**
     * Find customer by ID
     */
    public Optional<Customer> findById(Integer customerId) {
        try {
            Customer customer = jdbcTemplate.queryForObject(SELECT_BY_ID, customerRowMapper, customerId);
            return Optional.of(customer);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Customers whose name or email contains the term, or with the term's id (ADMIN customer list and search)
     */
    public List<Customer> search(SearchTerm term, int limit) {
        return jdbcTemplate.query(
                "SELECT customer_id, customer_name, email, phone, status, created_date FROM corp.customer"
                        + " WHERE customer_name ILIKE ? OR email ILIKE ? OR customer_id = ?"
                        + " ORDER BY (customer_id = ?) DESC, customer_name LIMIT ?",
                customerRowMapper, term.like(), term.like(), term.id(), term.id(), limit);
    }

    /**
     * Insert a customer and return its new id
     */
    public int insert(Customer customer) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO corp.customer (customer_name, email, phone, status) VALUES (?, ?, ?, ?) RETURNING customer_id",
                Integer.class, customer.getCustomerName(), customer.getEmail(), customer.getPhone(), customer.getStatus());
    }

    /**
     * Update name, email, phone and status of a customer
     */
    public void update(Customer customer) {
        jdbcTemplate.update("UPDATE corp.customer SET customer_name = ?, email = ?, phone = ?, status = ?,"
                        + " updated_date = CURRENT_TIMESTAMP WHERE customer_id = ?",
                customer.getCustomerName(), customer.getEmail(), customer.getPhone(), customer.getStatus(),
                customer.getCustomerId());
    }

    /**
     * Get database summary statistics
     */
    public Map<String, Object> getDatabaseSummary(Integer customerId) {
        Map<String, Object> summary = new HashMap<>();

        try {
            // Get device count for customer
            String deviceCountSql = "SELECT COUNT(*) FROM device.device WHERE customer_id = ?";
            Integer deviceCount = jdbcTemplate.queryForObject(deviceCountSql, Integer.class, customerId);
            summary.put("deviceCount", deviceCount);

            // Get active device count
            String activeDeviceSql = "SELECT COUNT(*) FROM device.device WHERE customer_id = ? AND status = 'ACTIVE'";
            Integer activeDevices = jdbcTemplate.queryForObject(activeDeviceSql, Integer.class, customerId);
            summary.put("activeDevices", activeDevices);

            // Get transaction count (last 30 days)
            String transactionSql = "SELECT COUNT(*) FROM txn.payment_transaction WHERE customer_id = ? AND transaction_time >= NOW() - INTERVAL '30 days'";
            Integer transactionCount = jdbcTemplate.queryForObject(transactionSql, Integer.class, customerId);
            summary.put("recentTransactions", transactionCount);

            // Get approved transaction amount (last 30 days)
            String amountSql = "SELECT COALESCE(SUM(amount), 0) FROM txn.payment_transaction WHERE customer_id = ? AND transaction_status = 'APPROVED' AND transaction_time >= NOW() - INTERVAL '30 days'";
            java.math.BigDecimal approvedAmount = jdbcTemplate.queryForObject(amountSql, java.math.BigDecimal.class, customerId);
            summary.put("approvedAmount", approvedAmount != null ? approvedAmount : 0);

            // Get location count
            String locationSql = "SELECT COUNT(*) FROM location.location WHERE customer_id = ?";
            Integer locationCount = jdbcTemplate.queryForObject(locationSql, Integer.class, customerId);
            summary.put("locationCount", locationCount);

        } catch (Exception e) {
            e.printStackTrace();
            summary.put("error", "Error retrieving summary");
        }

        return summary;
    }

    /**
     * Get overall system statistics (for ADMIN)
     */
    public Map<String, Object> getSystemSummary() {
        Map<String, Object> summary = new HashMap<>();

        try {
            // Customer count
            String customerSql = "SELECT COUNT(*) FROM corp.customer WHERE status = 'ACTIVE'";
            Integer customerCount = jdbcTemplate.queryForObject(customerSql, Integer.class);
            summary.put("activeCustomers", customerCount);

            // Total devices
            String deviceSql = "SELECT COUNT(*) FROM device.device";
            Integer totalDevices = jdbcTemplate.queryForObject(deviceSql, Integer.class);
            summary.put("totalDevices", totalDevices);

            // Total transactions
            String transactionSql = "SELECT COUNT(*) FROM txn.payment_transaction";
            Integer totalTransactions = jdbcTemplate.queryForObject(transactionSql, Integer.class);
            summary.put("totalTransactions", totalTransactions);

            // Total users
            String userSql = "SELECT COUNT(*) FROM app_user.app_user WHERE status = 'ACTIVE'";
            Integer activeUsers = jdbcTemplate.queryForObject(userSql, Integer.class);
            summary.put("activeUsers", activeUsers);

        } catch (Exception e) {
            e.printStackTrace();
            summary.put("error", "Error retrieving system summary");
        }

        return summary;
    }
}

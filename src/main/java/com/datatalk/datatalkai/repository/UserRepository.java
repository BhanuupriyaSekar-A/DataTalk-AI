package com.datatalk.datatalkai.repository;

import com.datatalk.datatalkai.model.User;
import com.datatalk.datatalkai.model.Customer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

/**
 * User Repository - Data access layer for user operations
 * Uses Spring JDBC for database operations
 */
@Repository
public class UserRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String SELECT_BY_USERNAME = 
        "SELECT user_id, username, password_hash, full_name, email, role, customer_id, status, created_date, updated_date " +
        "FROM app_user.app_user WHERE username = ? AND status = 'ACTIVE'";

    private static final String SELECT_BY_USER_ID = 
        "SELECT user_id, username, password_hash, full_name, email, role, customer_id, status, created_date, updated_date " +
        "FROM app_user.app_user WHERE user_id = ?";

    private static final String LOG_LOGIN_ATTEMPT = 
        "INSERT INTO app_user.user_login_history (user_id, login_time, ip_address, status, failure_reason, channel) " +
        "VALUES (?, CURRENT_TIMESTAMP, ?, ?, ?, 'WEB')";

    private static final String LOG_LOGOUT = 
        "UPDATE app_user.user_login_history SET logout_time = CURRENT_TIMESTAMP " +
        "WHERE login_id = (SELECT login_id FROM app_user.user_login_history " +
        "WHERE user_id = ? AND status = 'SUCCESS' AND logout_time IS NULL ORDER BY login_time DESC LIMIT 1)";

    private final RowMapper<User> userRowMapper = new RowMapper<User>() {
        @Override
        public User mapRow(ResultSet rs, int rowNum) throws SQLException {
            User user = new User();
            user.setUserId(rs.getInt("user_id"));
            user.setUsername(rs.getString("username"));
            user.setPasswordHash(rs.getString("password_hash"));
            user.setFullName(rs.getString("full_name"));
            user.setEmail(rs.getString("email"));
            user.setRole(rs.getString("role"));
            user.setCustomerId(rs.getInt("customer_id"));
            if (rs.wasNull()) user.setCustomerId(null);
            user.setStatus(rs.getString("status"));
            Timestamp createdTs = rs.getTimestamp("created_date");
            if (createdTs != null) user.setCreatedDate(createdTs.toLocalDateTime());
            Timestamp updatedTs = rs.getTimestamp("updated_date");
            if (updatedTs != null) user.setUpdatedDate(updatedTs.toLocalDateTime());
            return user;
        }
    };

    /**
     * Find user by username (only active users)
     */
    public Optional<User> findByUsername(String username) {
        try {
            User user = jdbcTemplate.queryForObject(SELECT_BY_USERNAME, userRowMapper, username);
            return Optional.of(user);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Find user by user ID
     */
    public Optional<User> findById(Integer userId) {
        try {
            User user = jdbcTemplate.queryForObject(SELECT_BY_USER_ID, userRowMapper, userId);
            return Optional.of(user);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Log a login attempt
     */
    public void logLoginAttempt(Integer userId, String ipAddress, String status, String failureReason) {
        try {
            jdbcTemplate.update(LOG_LOGIN_ATTEMPT, userId, ipAddress, status, failureReason);
        } catch (Exception e) {
            // Log error but don't fail the login process
            e.printStackTrace();
        }
    }

    /**
     * Log user logout
     */
    public void logLogout(Integer userId) {
        try {
            jdbcTemplate.update(LOG_LOGOUT, userId);
        } catch (Exception e) {
            // Log error but don't fail
            e.printStackTrace();
        }
    }

    /**
     * Get all users (admin only)
     */
    public List<User> findAll() {
        String sql = "SELECT user_id, username, password_hash, full_name, email, role, customer_id, status, created_date, updated_date " +
                     "FROM app_user.app_user ORDER BY username";
        return jdbcTemplate.query(sql, userRowMapper);
    }
}

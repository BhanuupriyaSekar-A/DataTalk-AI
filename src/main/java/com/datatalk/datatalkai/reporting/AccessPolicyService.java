package com.datatalk.datatalkai.reporting;

import com.datatalk.datatalkai.model.User;
import com.datatalk.datatalkai.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Builds the UserScope for the logged-in user.
 * <ul>
 *   <li>User id comes from the server-side session (set at login).</li>
 *   <li>Role, customer and status are re-read from the database on every request.</li>
 *   <li>Role-based table access comes from app.reporting.role-tables.&lt;ROLE&gt; (default: all approved tables).</li>
 *   <li>CUSTOMER users are restricted to the devices assigned in app_user.user_device_access.</li>
 * </ul>
 */
@Service
public class AccessPolicyService {

    private static final String DEVICE_ACCESS_SQL = """
            SELECT uda.device_id
            FROM app_user.user_device_access uda
            JOIN device.device d ON d.device_id = uda.device_id
            WHERE uda.user_id = ? AND d.customer_id = ?
            ORDER BY uda.device_id
            """;

    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    private final SchemaMetadataService schemaMetadataService;
    private final Environment environment;
    private final Set<String> deviceRestrictedRoles;

    public AccessPolicyService(UserRepository userRepository,
                               JdbcTemplate jdbcTemplate,
                               SchemaMetadataService schemaMetadataService,
                               Environment environment,
                               @Value("${app.reporting.device-restricted-roles:CUSTOMER}") List<String> deviceRestrictedRoles) {
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.schemaMetadataService = schemaMetadataService;
        this.environment = environment;
        this.deviceRestrictedRoles = new HashSet<>(deviceRestrictedRoles);
    }

    /** Returns the scope of the session user, or throws 401/403. */
    public UserScope resolve(HttpSession session) {
        Integer userId = session == null ? null : (Integer) session.getAttribute("userId");
        if (userId == null) {
            throw new ReportAccessException(HttpStatus.UNAUTHORIZED, "NOT_AUTHENTICATED", "Please log in");
        }
        return resolve(userId);
    }

    public UserScope resolve(int userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ReportAccessException(HttpStatus.UNAUTHORIZED, "NOT_AUTHENTICATED", "User not found"));
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new ReportAccessException(HttpStatus.FORBIDDEN, "INACTIVE_USER", "Your account is not active");
        }
        String role = user.getRole();
        if (!"ADMIN".equals(role) && user.getCustomerId() == null) {
            throw new ReportAccessException(HttpStatus.FORBIDDEN, "NO_CUSTOMER", "Your account is not linked to a customer");
        }

        Set<String> allTables = schemaMetadataService.getCatalog().tableNames();
        Set<String> allowedTables;
        String configured = environment.getProperty("app.reporting.role-tables." + role);
        if (configured == null || configured.isBlank()) {
            allowedTables = new LinkedHashSet<>(allTables);
        } else {
            allowedTables = new LinkedHashSet<>();
            for (String t : configured.split(",")) {
                if (allTables.contains(t.trim())) {
                    allowedTables.add(t.trim());
                }
            }
        }

        List<Integer> deviceIds = null;
        if (deviceRestrictedRoles.contains(role)) {
            deviceIds = jdbcTemplate.queryForList(DEVICE_ACCESS_SQL, Integer.class, user.getUserId(), user.getCustomerId());
        }
        return new UserScope(user.getUserId(), user.getUsername(), role, user.getCustomerId(),
                Collections.unmodifiableSet(allowedTables), deviceIds == null ? null : List.copyOf(deviceIds));
    }
}

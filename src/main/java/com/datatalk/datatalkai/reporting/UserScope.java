package com.datatalk.datatalkai.reporting;

import java.util.List;
import java.util.Set;

/**
 * Data access scope of the logged-in user, built by Java from the session and the database.
 * The AI never decides any of these values.
 *
 * @param allowedTables    qualified table names (schema.table) the user's role may query
 * @param allowedDeviceIds device ids the user may see; null means "no device-level restriction"
 */
public record UserScope(int userId,
                        String username,
                        String role,
                        Integer customerId,
                        Set<String> allowedTables,
                        List<Integer> allowedDeviceIds) {

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}

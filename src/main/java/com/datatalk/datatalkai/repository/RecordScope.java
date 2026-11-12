package com.datatalk.datatalkai.repository;

import com.datatalk.datatalkai.reporting.UserScope;

import java.util.List;
import java.util.Set;

/**
 * Row-level limits for record pages and search, derived from the user's reporting scope.
 *
 * @param customerId null for ADMIN (all customers)
 * @param deviceIds  null when there is no device-level restriction
 */
public record RecordScope(boolean admin, Integer customerId, List<Integer> deviceIds, Set<String> tables) {

    public static RecordScope of(UserScope scope) {
        return new RecordScope(scope.isAdmin(), scope.isAdmin() ? null : scope.customerId(),
                scope.allowedDeviceIds(), scope.allowedTables());
    }

    public boolean canRead(String table) {
        return tables.contains(table);
    }

    void append(StringBuilder sql, List<Object> args, String alias, boolean hasDeviceColumn) {
        if (customerId != null) {
            sql.append(" AND ").append(alias).append(".customer_id = ?");
            args.add(customerId);
        }
        if (hasDeviceColumn && deviceIds != null) {
            if (deviceIds.isEmpty()) {
                sql.append(" AND FALSE");
                return;
            }
            sql.append(" AND ").append(alias).append(".device_id IN (")
                    .append(String.join(", ", deviceIds.stream().map(d -> "?").toList())).append(')');
            args.addAll(deviceIds);
        }
    }
}

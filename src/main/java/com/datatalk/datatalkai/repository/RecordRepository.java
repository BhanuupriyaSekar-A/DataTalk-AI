package com.datatalk.datatalkai.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Devices, locations and transactions for the record pages and global search.
 * Every read is limited by the caller's RecordScope; all values are bound parameters.
 */
@Repository
public class RecordRepository {

    private static final String DEVICES = """
            SELECT d.device_id, d.serial_number, d.device_type, d.status, d.firmware_version, d.activated_date,
                   d.last_communication_time, d.customer_id, c.customer_name, d.location_id, l.location_name, l.city
            FROM device.device d
            JOIN location.location l ON l.location_id = d.location_id
            JOIN corp.customer c ON c.customer_id = d.customer_id
            WHERE TRUE""";

    private static final String LOCATIONS = """
            SELECT l.location_id, l.location_name, l.city, l.state, l.timezone, l.created_date, l.customer_id, c.customer_name
            FROM location.location l
            JOIN corp.customer c ON c.customer_id = l.customer_id
            WHERE TRUE""";

    private static final String TRANSACTIONS = """
            SELECT pt.transaction_id, pt.amount, pt.transaction_status, pt.card_type, pt.entry_mode, pt.decline_reason,
                   pt.transaction_time, pt.device_id, d.serial_number, d.location_id, l.location_name,
                   pt.customer_id, c.customer_name
            FROM txn.payment_transaction pt
            JOIN device.device d ON d.device_id = pt.device_id
            JOIN location.location l ON l.location_id = d.location_id
            JOIN corp.customer c ON c.customer_id = pt.customer_id
            WHERE TRUE""";

    private final JdbcTemplate jdbcTemplate;

    public RecordRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ---------- devices ----------

    /** Devices matching serial number, id or type (all when the term is empty), optionally at one location. */
    public List<Map<String, Object>> devices(SearchTerm term, Integer locationId, RecordScope scope, int limit) {
        StringBuilder sql = new StringBuilder(DEVICES);
        List<Object> args = new ArrayList<>();
        if (!term.isEmpty()) {
            sql.append(" AND (d.serial_number ILIKE ? OR d.device_id = ? OR d.device_type ILIKE ?)");
            args.addAll(List.of(term.like(), term.id(), term.typeLike()));
        }
        if (locationId != null) {
            sql.append(" AND d.location_id = ?");
            args.add(locationId);
        }
        scope.append(sql, args, "d", true);
        sql.append(" ORDER BY (d.device_id = ?) DESC, d.serial_number LIMIT ?");
        args.addAll(List.of(term.id(), limit));
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    public Optional<Map<String, Object>> device(int deviceId, RecordScope scope) {
        return one(DEVICES + " AND d.device_id = ?", deviceId, scope, "d", true);
    }

    /** Caller must have authorized the device first. */
    public List<Map<String, Object>> deviceEvents(int deviceId, int limit) {
        return jdbcTemplate.queryForList("""
                SELECT event_type, event_time, description FROM device.device_status_log
                WHERE device_id = ? ORDER BY event_time DESC LIMIT ?""", deviceId, limit);
    }

    public int insertDevice(String serialNumber, int customerId, int locationId, String deviceType,
                            String firmwareVersion, String status) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO device.device (serial_number, customer_id, location_id, device_type, firmware_version, status,
                                           activated_date)
                VALUES (?, ?, ?, ?, ?, ?, CASE WHEN ? = 'ACTIVE' THEN CURRENT_TIMESTAMP END)
                RETURNING device_id""", Integer.class,
                serialNumber, customerId, locationId, deviceType, firmwareVersion, status, status);
    }

    // ---------- locations ----------

    /** Locations matching name, city or id (all when the term is empty), optionally for one customer. */
    public List<Map<String, Object>> locations(SearchTerm term, Integer customerId, RecordScope scope, int limit) {
        StringBuilder sql = new StringBuilder(LOCATIONS);
        List<Object> args = new ArrayList<>();
        if (!term.isEmpty()) {
            sql.append(" AND (l.location_name ILIKE ? OR l.city ILIKE ? OR l.location_id = ?)");
            args.addAll(List.of(term.like(), term.like(), term.id()));
        }
        if (customerId != null) {
            sql.append(" AND l.customer_id = ?");
            args.add(customerId);
        }
        scope.append(sql, args, "l", false);
        sql.append(" ORDER BY (l.location_id = ?) DESC, l.location_name LIMIT ?");
        args.addAll(List.of(term.id(), limit));
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    public Optional<Map<String, Object>> location(int locationId, RecordScope scope) {
        return one(LOCATIONS + " AND l.location_id = ?", locationId, scope, "l", false);
    }

    public boolean locationBelongsTo(int locationId, int customerId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM location.location WHERE location_id = ? AND customer_id = ?",
                Integer.class, locationId, customerId);
        return n != null && n > 0;
    }

    public int insertLocation(int customerId, String name, String city, String state, String timezone) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO location.location (customer_id, location_name, city, state, timezone)
                VALUES (?, ?, ?, ?, ?) RETURNING location_id""", Integer.class, customerId, name, city, state, timezone);
    }

    // ---------- transactions ----------

    /**
     * Latest transactions matching the term (id, device serial number or status; all when empty),
     * optionally for one device or location.
     */
    public List<Map<String, Object>> transactions(SearchTerm term, Integer deviceId, Integer locationId,
                                                  RecordScope scope, int limit) {
        StringBuilder sql = new StringBuilder(TRANSACTIONS);
        List<Object> args = new ArrayList<>();
        if (!term.isEmpty()) {
            sql.append(" AND (pt.transaction_id = ? OR d.serial_number ILIKE ? OR pt.transaction_status ILIKE ?)");
            args.addAll(List.of(term.id(), term.like(), term.like()));
        }
        if (deviceId != null) {
            sql.append(" AND pt.device_id = ?");
            args.add(deviceId);
        }
        if (locationId != null) {
            sql.append(" AND d.location_id = ?");
            args.add(locationId);
        }
        scope.append(sql, args, "pt", true);
        sql.append(" ORDER BY (pt.transaction_id = ?) DESC, pt.transaction_time DESC LIMIT ?");
        args.addAll(List.of(term.id(), limit));
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    public Optional<Map<String, Object>> transaction(int transactionId, RecordScope scope) {
        return one(TRANSACTIONS + " AND pt.transaction_id = ?", transactionId, scope, "pt", true);
    }

    private Optional<Map<String, Object>> one(String base, int id, RecordScope scope, String alias,
                                              boolean hasDeviceColumn) {
        StringBuilder sql = new StringBuilder(base);
        List<Object> args = new ArrayList<>(List.of(id));
        scope.append(sql, args, alias, hasDeviceColumn);
        return jdbcTemplate.queryForList(sql.toString(), args.toArray()).stream().findFirst();
    }
}

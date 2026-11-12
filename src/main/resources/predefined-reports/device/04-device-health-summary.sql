-- name: Device health summary
-- description: Uptime, signal, battery and errors per device, worst devices first.
-- param: days | integer | 7 | Number of days to look back
-- example: Show the device health summary for the last 7 days
-- example: Which devices have critical health or low uptime?
-- chart: table
SELECT d.serial_number,
       d.device_type,
       l.location_name,
       ROUND(AVG(h.uptime_pct), 2) AS avg_uptime_pct,
       ROUND(AVG(h.signal_strength), 0) AS avg_signal_strength,
       MIN(h.battery_level) AS min_battery_level,
       SUM(h.error_count) AS total_errors,
       SUM(CASE WHEN h.health_status = 'CRITICAL' THEN 1 ELSE 0 END) AS critical_days,
       SUM(CASE WHEN h.health_status = 'WARNING' THEN 1 ELSE 0 END) AS warning_days
FROM device.device_health_metric h
JOIN device.device d ON d.device_id = h.device_id
JOIN location.location l ON l.location_id = d.location_id
WHERE h.recorded_time >= CURRENT_DATE - (:days * INTERVAL '1 day')
GROUP BY d.serial_number, d.device_type, l.location_name
ORDER BY critical_days DESC, warning_days DESC, total_errors DESC

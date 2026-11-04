-- name: Device events by type
-- description: Count of device status events (errors, reboots, offline, ...) by event type.
-- param: days | integer | 30 | Number of days to look back
-- example: How many device errors happened in the last 30 days?
-- example: Show device events grouped by type
-- chart: bar
SELECT e.event_type,
       COUNT(*) AS events,
       COUNT(DISTINCT e.device_id) AS devices_affected
FROM device.device_status_log e
WHERE e.event_time >= CURRENT_DATE - (:days * INTERVAL '1 day')
GROUP BY e.event_type
ORDER BY events DESC

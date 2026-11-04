-- name: Devices not communicating
-- description: Devices whose last communication is older than the given number of hours.
-- param: hours | integer | 24 | Hours since last communication
-- example: Which devices have not communicated in the last 24 hours?
-- example: List offline devices
-- chart: table
SELECT d.serial_number,
       d.device_type,
       d.status,
       l.location_name,
       d.last_communication_time
FROM device.device d
LEFT JOIN location.location l ON l.location_id = d.location_id
WHERE d.last_communication_time < NOW() - (:hours * INTERVAL '1 hour')
   OR d.last_communication_time IS NULL
ORDER BY d.last_communication_time NULLS FIRST

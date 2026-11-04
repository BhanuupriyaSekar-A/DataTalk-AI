-- name: Device status summary
-- description: Number of devices in each status by device type.
-- example: How many devices are active, inactive or in maintenance?
-- example: Show device count by status
-- chart: bar
SELECT d.status,
       d.device_type,
       COUNT(*) AS devices
FROM device.device d
GROUP BY d.status, d.device_type
ORDER BY d.status, devices DESC

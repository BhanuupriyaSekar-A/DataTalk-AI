-- name: Locations and devices per customer
-- description: Number of locations and devices for each customer.
-- example: How many locations and devices does each customer have?
-- example: Show customer footprint
-- chart: table
SELECT c.customer_name,
       c.status,
       COUNT(DISTINCT l.location_id) AS locations,
       COUNT(DISTINCT d.device_id) AS devices
FROM corp.customer c
LEFT JOIN location.location l ON l.customer_id = c.customer_id
LEFT JOIN device.device d ON d.customer_id = c.customer_id
GROUP BY c.customer_name, c.status
ORDER BY devices DESC

-- name: Sales report by location
-- description: Approved sales, transaction count, average ticket and active devices per location.
-- param: days | integer | 30 | Number of days to look back
-- example: Show the sales report by location for the last 30 days
-- example: Which locations have the highest sales?
-- chart: bar
SELECT l.location_name,
       l.city,
       COUNT(*) AS approved_transactions,
       SUM(pt.amount) AS gross_sales,
       ROUND(AVG(pt.amount), 2) AS avg_ticket,
       COUNT(DISTINCT pt.device_id) AS selling_devices
FROM txn.payment_transaction pt
JOIN device.device d ON d.device_id = pt.device_id
JOIN location.location l ON l.location_id = d.location_id
WHERE pt.transaction_status = 'APPROVED'
  AND pt.transaction_time >= CURRENT_DATE - (:days * INTERVAL '1 day')
GROUP BY l.location_name, l.city
ORDER BY gross_sales DESC

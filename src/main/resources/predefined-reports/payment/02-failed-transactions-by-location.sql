-- name: Failed transactions by location
-- description: Declined, failed and refunded transactions grouped by location.
-- param: days | integer | 30 | Number of days to look back
-- example: Show failed transactions from the last 30 days grouped by location
-- example: Which locations have the most declined payments?
-- chart: bar
SELECT l.location_name,
       l.city,
       COUNT(*) AS failed_transactions,
       SUM(pt.amount) AS failed_amount
FROM txn.payment_transaction pt
JOIN device.device d ON d.device_id = pt.device_id
JOIN location.location l ON l.location_id = d.location_id
WHERE pt.transaction_status IN ('DECLINED', 'FAILED', 'REFUNDED')
  AND pt.transaction_time >= CURRENT_DATE - (:days * INTERVAL '1 day')
GROUP BY l.location_name, l.city
ORDER BY failed_transactions DESC

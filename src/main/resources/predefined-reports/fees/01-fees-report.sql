-- name: Fees report
-- description: Interchange, scheme, processing and gateway fees per customer.
-- param: days | integer | 30 | Number of days to look back
-- example: Show the fees report for the last 30 days
-- example: How much did we pay in processing and interchange fees?
-- chart: bar
SELECT c.customer_name,
       COUNT(DISTINCT f.transaction_id) AS transactions,
       SUM(CASE WHEN f.fee_type = 'INTERCHANGE' THEN f.fee_amount ELSE 0 END) AS interchange_fees,
       SUM(CASE WHEN f.fee_type = 'SCHEME' THEN f.fee_amount ELSE 0 END) AS scheme_fees,
       SUM(CASE WHEN f.fee_type = 'PROCESSING' THEN f.fee_amount ELSE 0 END) AS processing_fees,
       SUM(CASE WHEN f.fee_type = 'GATEWAY' THEN f.fee_amount ELSE 0 END) AS gateway_fees,
       SUM(f.fee_amount) AS total_fees
FROM txn.transaction_fee f
JOIN corp.customer c ON c.customer_id = f.customer_id
WHERE f.fee_time >= CURRENT_DATE - (:days * INTERVAL '1 day')
GROUP BY c.customer_name
ORDER BY total_fees DESC

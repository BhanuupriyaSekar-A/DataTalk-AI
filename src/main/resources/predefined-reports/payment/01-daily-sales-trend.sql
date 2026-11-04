-- name: Daily sales trend
-- description: Approved sales amount and transaction count per day.
-- param: days | integer | 30 | Number of days to look back
-- example: Show daily sales for the last 30 days
-- example: What is the daily revenue trend this month?
-- chart: line
SELECT date_trunc('day', pt.transaction_time)::date AS sales_day,
       COUNT(*) AS approved_transactions,
       SUM(pt.amount) AS approved_amount
FROM txn.payment_transaction pt
WHERE pt.transaction_status = 'APPROVED'
  AND pt.transaction_time >= CURRENT_DATE - (:days * INTERVAL '1 day')
GROUP BY 1
ORDER BY 1

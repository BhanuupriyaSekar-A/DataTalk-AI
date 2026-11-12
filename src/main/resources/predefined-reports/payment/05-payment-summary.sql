-- name: Payment summary
-- description: Transaction count, amount and share of transactions for each payment status.
-- param: days | integer | 30 | Number of days to look back
-- example: Show the payment summary for the last 30 days
-- example: What percentage of payments were approved, declined or refunded?
-- chart: pie
SELECT pt.transaction_status,
       COUNT(*) AS transactions,
       SUM(pt.amount) AS total_amount,
       ROUND(AVG(pt.amount), 2) AS avg_amount,
       ROUND(COUNT(*) * 100.0 / SUM(COUNT(*)) OVER (), 2) AS pct_of_transactions
FROM txn.payment_transaction pt
WHERE pt.transaction_time >= CURRENT_DATE - (:days * INTERVAL '1 day')
GROUP BY pt.transaction_status
ORDER BY transactions DESC

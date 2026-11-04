-- name: Top decline reasons
-- description: Most common decline reasons with counts.
-- param: days | integer | 30 | Number of days to look back
-- example: What are the top decline reasons?
-- example: Why are payments being declined this month?
-- chart: bar
SELECT COALESCE(pt.decline_reason, 'Unknown') AS decline_reason,
       COUNT(*) AS declined_transactions
FROM txn.payment_transaction pt
WHERE pt.transaction_status = 'DECLINED'
  AND pt.transaction_time >= CURRENT_DATE - (:days * INTERVAL '1 day')
GROUP BY 1
ORDER BY declined_transactions DESC

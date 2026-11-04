-- name: Sales by card type
-- description: Approved transaction count and amount by card brand.
-- param: days | integer | 30 | Number of days to look back
-- example: Show sales by card type
-- example: How much revenue came from Visa versus Mastercard?
-- chart: pie
SELECT pt.card_type,
       COUNT(*) AS transactions,
       SUM(pt.amount) AS total_amount
FROM txn.payment_transaction pt
WHERE pt.transaction_status = 'APPROVED'
  AND pt.transaction_time >= CURRENT_DATE - (:days * INTERVAL '1 day')
GROUP BY pt.card_type
ORDER BY total_amount DESC

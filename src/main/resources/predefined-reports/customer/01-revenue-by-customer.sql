-- name: Revenue by customer
-- description: Approved sales amount per customer (business users only see their own customer).
-- param: days | integer | 30 | Number of days to look back
-- example: Show revenue by customer
-- example: Which customer has the highest sales?
-- chart: bar
SELECT c.customer_name,
       COUNT(*) AS approved_transactions,
       SUM(pt.amount) AS approved_amount
FROM txn.payment_transaction pt
JOIN corp.customer c ON c.customer_id = pt.customer_id
WHERE pt.transaction_status = 'APPROVED'
  AND pt.transaction_time >= CURRENT_DATE - (:days * INTERVAL '1 day')
GROUP BY c.customer_name
ORDER BY approved_amount DESC

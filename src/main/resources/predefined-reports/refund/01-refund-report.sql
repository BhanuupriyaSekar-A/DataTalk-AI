-- name: Refund report
-- description: Refund requests by reason with completed, pending and rejected counts and amounts.
-- param: days | integer | 30 | Number of days to look back
-- example: Show the refund report for the last 30 days
-- example: How many refunds are pending and what are the main refund reasons?
-- chart: bar
SELECT r.refund_reason,
       COUNT(*) AS refund_requests,
       SUM(CASE WHEN r.refund_status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed,
       SUM(CASE WHEN r.refund_status IN ('REQUESTED', 'APPROVED') THEN 1 ELSE 0 END) AS pending,
       SUM(CASE WHEN r.refund_status = 'REJECTED' THEN 1 ELSE 0 END) AS rejected,
       SUM(CASE WHEN r.refund_status = 'COMPLETED' THEN r.refund_amount ELSE 0 END) AS refunded_amount,
       SUM(CASE WHEN r.refund_status IN ('REQUESTED', 'APPROVED') THEN r.refund_amount ELSE 0 END) AS pending_amount
FROM txn.refund r
WHERE r.requested_time >= CURRENT_DATE - (:days * INTERVAL '1 day')
GROUP BY r.refund_reason
ORDER BY refund_requests DESC

-- name: Settlement summary by status
-- description: Settlement totals, fees and net amounts grouped by settlement status.
-- param: days | integer | 30 | Number of days to look back
-- example: Show settlement totals for the last 30 days
-- example: How much is pending settlement?
-- chart: bar
SELECT s.status,
       COUNT(*) AS settlements,
       SUM(s.total_amount) AS total_amount,
       SUM(s.fees) AS total_fees,
       SUM(s.net_amount) AS net_amount
FROM txn.settlement s
WHERE s.settlement_date >= CURRENT_DATE - :days
GROUP BY s.status
ORDER BY net_amount DESC

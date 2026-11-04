-- name: Weekly net settlement
-- description: Net settled amount and fees per week.
-- param: weeks | integer | 12 | Number of weeks to look back
-- example: Show weekly settlement amounts
-- example: What were the fees per week?
-- chart: line
SELECT date_trunc('week', s.settlement_date)::date AS week_start,
       SUM(s.total_amount) AS total_amount,
       SUM(s.fees) AS fees,
       SUM(s.net_amount) AS net_amount
FROM txn.settlement s
WHERE s.settlement_date >= CURRENT_DATE - (:weeks * 7)
GROUP BY 1
ORDER BY 1

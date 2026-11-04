-- =====================================================================
-- DataTalk AI - Verify Demo Users and Sample Data
-- Description: SQL queries to verify all demo users and sample data
-- Run this in Neon SQL Editor to confirm setup is complete
-- =====================================================================

-- =====================================================================
-- 1. VERIFY DEMO USERS
-- =====================================================================

-- Count total users
SELECT 
  COUNT(*) as total_users,
  COUNT(DISTINCT role) as distinct_roles
FROM app_user.app_user;

-- List all demo users
SELECT 
  user_id,
  username,
  full_name,
  role,
  customer_id,
  status,
  created_date
FROM app_user.app_user
ORDER BY user_id;

-- Count users by role
SELECT 
  role,
  COUNT(*) as count
FROM app_user.app_user
GROUP BY role
ORDER BY role;

-- =====================================================================
-- 2. VERIFY CUSTOMER DATA
-- =====================================================================

-- List all customers
SELECT 
  customer_id,
  customer_name,
  email,
  phone,
  status,
  created_date
FROM corp.customer
ORDER BY customer_id;

-- Customer count
SELECT COUNT(*) as total_customers FROM corp.customer;

-- =====================================================================
-- 3. VERIFY LOCATIONS
-- =====================================================================

-- List all locations with customer names
SELECT 
  l.location_id,
  l.customer_id,
  c.customer_name,
  l.location_name,
  l.city,
  l.state,
  l.timezone
FROM location.location l
LEFT JOIN corp.customer c ON l.customer_id = c.customer_id
ORDER BY l.customer_id, l.location_id;

-- Count locations per customer
SELECT 
  c.customer_id,
  c.customer_name,
  COUNT(l.location_id) as location_count
FROM corp.customer c
LEFT JOIN location.location l ON c.customer_id = l.customer_id
GROUP BY c.customer_id, c.customer_name
ORDER BY c.customer_id;

-- =====================================================================
-- 4. VERIFY DEVICES
-- =====================================================================

-- Count devices
SELECT COUNT(*) as total_devices FROM device.device;

-- Devices per customer
SELECT 
  d.customer_id,
  c.customer_name,
  COUNT(*) as device_count,
  COUNT(CASE WHEN d.status = 'ACTIVE' THEN 1 END) as active_count,
  COUNT(CASE WHEN d.status = 'INACTIVE' THEN 1 END) as inactive_count,
  COUNT(CASE WHEN d.status = 'MAINTENANCE' THEN 1 END) as maintenance_count
FROM device.device d
LEFT JOIN corp.customer c ON d.customer_id = c.customer_id
GROUP BY d.customer_id, c.customer_name
ORDER BY d.customer_id;

-- List sample devices
SELECT 
  device_id,
  serial_number,
  customer_id,
  device_type,
  status,
  firmware_version,
  activated_date,
  last_communication_time
FROM device.device
LIMIT 20;

-- =====================================================================
-- 5. VERIFY TRANSACTIONS
-- =====================================================================

-- Count transactions
SELECT 
  COUNT(*) as total_transactions,
  COUNT(CASE WHEN transaction_status = 'APPROVED' THEN 1 END) as approved_count,
  COUNT(CASE WHEN transaction_status IN ('DECLINED', 'FAILED') THEN 1 END) as declined_count
FROM txn.payment_transaction;

-- Calculate transaction statistics
SELECT 
  COUNT(*) as total_transactions,
  ROUND(100.0 * COUNT(CASE WHEN transaction_status = 'APPROVED' THEN 1 END) / COUNT(*), 2) as approved_percentage,
  ROUND(100.0 * COUNT(CASE WHEN transaction_status IN ('DECLINED', 'FAILED') THEN 1 END) / COUNT(*), 2) as declined_percentage,
  MIN(amount) as min_amount,
  MAX(amount) as max_amount,
  ROUND(AVG(amount), 2) as avg_amount,
  SUM(amount) as total_amount
FROM txn.payment_transaction;

-- Transactions per customer
SELECT 
  t.customer_id,
  c.customer_name,
  COUNT(*) as transaction_count,
  SUM(CASE WHEN t.transaction_status = 'APPROVED' THEN 1 ELSE 0 END) as approved,
  SUM(CASE WHEN t.transaction_status IN ('DECLINED', 'FAILED') THEN 1 ELSE 0 END) as declined,
  ROUND(SUM(t.amount), 2) as total_amount
FROM txn.payment_transaction t
LEFT JOIN corp.customer c ON t.customer_id = c.customer_id
GROUP BY t.customer_id, c.customer_name
ORDER BY t.customer_id;

-- Sample transactions
SELECT 
  transaction_id,
  device_id,
  customer_id,
  amount,
  transaction_status,
  transaction_time
FROM txn.payment_transaction
ORDER BY transaction_time DESC
LIMIT 20;

-- =====================================================================
-- 6. VERIFY DEVICE STATUS LOG
-- =====================================================================

-- Count device status events
SELECT COUNT(*) as total_events FROM device.device_status_log;

-- Sample device status events
SELECT 
  event_id,
  device_id,
  event_type,
  event_time,
  description
FROM device.device_status_log
ORDER BY event_time DESC
LIMIT 20;

-- =====================================================================
-- 7. VERIFY SETTLEMENTS
-- =====================================================================

-- Count settlements
SELECT COUNT(*) as total_settlements FROM txn.settlement;

-- Settlements per customer
SELECT 
  s.customer_id,
  c.customer_name,
  COUNT(*) as settlement_count,
  SUM(s.total_amount) as total_settled,
  SUM(s.fees) as total_fees,
  SUM(s.net_amount) as net_settled
FROM txn.settlement s
LEFT JOIN corp.customer c ON s.customer_id = c.customer_id
GROUP BY s.customer_id, c.customer_name
ORDER BY s.customer_id;

-- Sample settlements
SELECT 
  settlement_id,
  customer_id,
  settlement_date,
  total_amount,
  fees,
  net_amount,
  status
FROM txn.settlement
ORDER BY settlement_date DESC
LIMIT 20;

-- =====================================================================
-- 8. VERIFY LOGIN HISTORY
-- =====================================================================

-- Count login records
SELECT COUNT(*) as total_login_records FROM app_user.user_login_history;

-- Recent login attempts
SELECT 
  login_id,
  user_id,
  login_time,
  status,
  failure_reason,
  ip_address
FROM app_user.user_login_history
ORDER BY login_time DESC
LIMIT 20;

-- =====================================================================
-- 9. VERIFY CHAT HISTORY
-- =====================================================================

-- Count chat records
SELECT COUNT(*) as total_chat_records FROM app.chat_history;

-- =====================================================================
-- 10. COMPREHENSIVE SUMMARY
-- =====================================================================

SELECT 
  'Customers' as metric, COUNT(*)::text as count
FROM corp.customer
UNION ALL
SELECT 'Locations', COUNT(*)::text FROM location.location
UNION ALL
SELECT 'Users', COUNT(*)::text FROM app_user.app_user
UNION ALL
SELECT 'Devices', COUNT(*)::text FROM device.device
UNION ALL
SELECT 'Transactions', COUNT(*)::text FROM txn.payment_transaction
UNION ALL
SELECT 'Device Events', COUNT(*)::text FROM device.device_status_log
UNION ALL
SELECT 'Settlements', COUNT(*)::text FROM txn.settlement
UNION ALL
SELECT 'Login Records', COUNT(*)::text FROM app_user.user_login_history
UNION ALL
SELECT 'Chat Records', COUNT(*)::text FROM app.chat_history
ORDER BY metric;

-- =====================================================================
-- 11. PASSWORD VERIFICATION
-- =====================================================================

-- Verify all demo users use BCrypt hashed passwords
SELECT 
  username,
  role,
  CASE 
    WHEN password_hash LIKE '$2a$%' THEN 'BCrypt-2a'
    WHEN password_hash LIKE '$2b$%' THEN 'BCrypt-2b'
    WHEN password_hash LIKE '$2y$%' THEN 'BCrypt-2y'
    ELSE 'Unknown/Plain'
  END as hash_type,
  LENGTH(password_hash) as hash_length,
  status
FROM app_user.app_user
ORDER BY username;

-- Verify all demo users have the same password hash (DemoPass@123)
SELECT 
  COUNT(DISTINCT password_hash) as distinct_password_hashes,
  COUNT(*) as total_demo_users
FROM app_user.app_user
WHERE status = 'ACTIVE';

-- =====================================================================
-- 12. DATA INTEGRITY CHECKS
-- =====================================================================

-- Check for orphaned devices (devices with no customer)
SELECT COUNT(*) as orphaned_devices
FROM device.device
WHERE customer_id IS NULL OR customer_id NOT IN (SELECT customer_id FROM corp.customer);

-- Check for orphaned locations (locations with no customer)
SELECT COUNT(*) as orphaned_locations
FROM location.location
WHERE customer_id NOT IN (SELECT customer_id FROM corp.customer);

-- Check for orphaned users (users with customer_id but no matching customer)
SELECT COUNT(*) as orphaned_users
FROM app_user.app_user
WHERE customer_id IS NOT NULL AND customer_id NOT IN (SELECT customer_id FROM corp.customer);

-- =====================================================================
-- END OF VERIFICATION QUERIES
-- =====================================================================

-- If all queries run successfully with appropriate results,
-- the database is properly initialized and ready for application use!

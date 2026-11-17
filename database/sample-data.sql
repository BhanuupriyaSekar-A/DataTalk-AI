-- =====================================================================
-- DataTalk AI - Sample Data
-- Description: Realistic sample data for testing and demo purposes
-- =====================================================================

-- IMPORTANT: This script assumes schema is already created
-- Run neon-schema.sql first, then this script

-- =====================================================================
-- SAMPLE DATA - CORP.CUSTOMER (5 customers)
-- =====================================================================
INSERT INTO corp.customer (customer_name, email, phone, status, created_date) VALUES
('TechPay Solutions', 'admin@techpay.com', '1-800-TECHPAY', 'ACTIVE', NOW() - INTERVAL '120 days'),
('RetailHub Inc', 'contact@retailhub.com', '1-800-RETAIL', 'ACTIVE', NOW() - INTERVAL '90 days'),
('Restaurant Group USA', 'info@restgrp.com', '1-800-EATS', 'ACTIVE', NOW() - INTERVAL '60 days'),
('Gas Station Network', 'support@gasnet.com', '1-800-GASNET', 'ACTIVE', NOW() - INTERVAL '45 days'),
('Quick Commerce Express', 'hello@quickcom.com', '1-800-QUICK', 'ACTIVE', NOW() - INTERVAL '30 days');

-- =====================================================================
-- SAMPLE DATA - LOCATION.LOCATION (15 locations)
-- =====================================================================
INSERT INTO location.location (customer_id, location_name, city, state, timezone, created_date) VALUES
(1, 'TechPay - Manhattan HQ', 'New York', 'NY', 'America/New_York', NOW() - INTERVAL '120 days'),
(1, 'TechPay - Brooklyn Office', 'Brooklyn', 'NY', 'America/New_York', NOW() - INTERVAL '110 days'),
(1, 'TechPay - New Jersey Hub', 'Newark', 'NJ', 'America/New_York', NOW() - INTERVAL '100 days'),

(2, 'RetailHub - Downtown LA', 'Los Angeles', 'CA', 'America/Los_Angeles', NOW() - INTERVAL '90 days'),
(2, 'RetailHub - Santa Monica', 'Santa Monica', 'CA', 'America/Los_Angeles', NOW() - INTERVAL '85 days'),
(2, 'RetailHub - San Francisco', 'San Francisco', 'CA', 'America/Los_Angeles', NOW() - INTERVAL '80 days'),
(2, 'RetailHub - Chicago', 'Chicago', 'IL', 'America/Chicago', NOW() - INTERVAL '75 days'),

(3, 'Restaurant Group - Times Square', 'New York', 'NY', 'America/New_York', NOW() - INTERVAL '60 days'),
(3, 'Restaurant Group - Miami Beach', 'Miami', 'FL', 'America/New_York', NOW() - INTERVAL '55 days'),
(3, 'Restaurant Group - Austin Downtown', 'Austin', 'TX', 'America/Chicago', NOW() - INTERVAL '50 days'),

(4, 'Gas Station - Houston', 'Houston', 'TX', 'America/Chicago', NOW() - INTERVAL '45 days'),
(4, 'Gas Station - Dallas', 'Dallas', 'TX', 'America/Chicago', NOW() - INTERVAL '42 days'),
(4, 'Gas Station - San Antonio', 'San Antonio', 'TX', 'America/Chicago', NOW() - INTERVAL '40 days'),

(5, 'QuickCom - Seattle', 'Seattle', 'WA', 'America/Los_Angeles', NOW() - INTERVAL '30 days'),
(5, 'QuickCom - Denver', 'Denver', 'CO', 'America/Denver', NOW() - INTERVAL '25 days');

-- =====================================================================
-- SAMPLE DATA - APP_USER.APP_USER (6 users with BCrypt hashed passwords)
-- Password info: All demo users use password "DemoPass@123"
-- BCrypt hash: $2a$10$muZDonRTJymtmicJtZ2FZ.MJ7zWFbR9VoQP.uQkAMPaQzInXqyVNa
-- For production, use strong passwords and rotate regularly
-- =====================================================================
INSERT INTO app_user.app_user (username, password_hash, full_name, email, role, customer_id, status, created_date) VALUES
('admin_user', '$2a$10$muZDonRTJymtmicJtZ2FZ.MJ7zWFbR9VoQP.uQkAMPaQzInXqyVNa', 'Bhanupriya', 'bhanupriya@datatalk.com', 'ADMIN', NULL, 'ACTIVE', NOW() - INTERVAL '120 days'),
('techpay_manager', '$2a$10$muZDonRTJymtmicJtZ2FZ.MJ7zWFbR9VoQP.uQkAMPaQzInXqyVNa', 'Arjun Mehta', 'arjun.mehta@techpay.com', 'BUSINESS', 1, 'ACTIVE', NOW() - INTERVAL '115 days'),
('techpay_customer', '$2a$10$muZDonRTJymtmicJtZ2FZ.MJ7zWFbR9VoQP.uQkAMPaQzInXqyVNa', 'Priya Raman', 'priya.raman@techpay.com', 'CUSTOMER', 1, 'ACTIVE', NOW() - INTERVAL '110 days'),
('retail_manager', '$2a$10$muZDonRTJymtmicJtZ2FZ.MJ7zWFbR9VoQP.uQkAMPaQzInXqyVNa', 'Michael Carter', 'michael.carter@retailhub.com', 'MANAGER', 2, 'ACTIVE', NOW() - INTERVAL '85 days'),
('restaurant_owner', '$2a$10$muZDonRTJymtmicJtZ2FZ.MJ7zWFbR9VoQP.uQkAMPaQzInXqyVNa', 'Daniel Brooks', 'daniel.brooks@restgrp.com', 'BUSINESS', 3, 'ACTIVE', NOW() - INTERVAL '55 days'),
('gas_operator', '$2a$10$muZDonRTJymtmicJtZ2FZ.MJ7zWFbR9VoQP.uQkAMPaQzInXqyVNa', 'Emily Watson', 'emily.watson@gasnet.com', 'CUSTOMER', 4, 'ACTIVE', NOW() - INTERVAL '42 days');

-- =====================================================================
-- SAMPLE DATA - DEVICE.DEVICE (60 devices)
-- =====================================================================
-- Customer 1: TechPay (20 devices across 3 locations)
INSERT INTO device.device (serial_number, customer_id, location_id, device_type, firmware_version, status, activated_date, last_communication_time, created_date)
SELECT 
  'DEV-TP-' || LPAD(i::text, 4, '0'),
  1,
  CASE WHEN i <= 7 THEN 1 WHEN i <= 14 THEN 2 ELSE 3 END,
  (ARRAY['POS_TERMINAL', 'MOBILE_READER', 'PAYMENT_GATEWAY'])[((i-1) % 3) + 1],
  '2.5.' || LPAD((i % 10)::text, 2, '0'),
  CASE WHEN random() < 0.85 THEN 'ACTIVE' WHEN random() < 0.95 THEN 'INACTIVE' ELSE 'MAINTENANCE' END,
  NOW() - INTERVAL '120 days' + (random() * INTERVAL '100 days'),
  NOW() - INTERVAL '1 day' + (random() * INTERVAL '23 hours'),
  NOW() - INTERVAL '120 days'
FROM generate_series(1, 20) AS i;

-- Customer 2: RetailHub (15 devices across 4 locations)
INSERT INTO device.device (serial_number, customer_id, location_id, device_type, firmware_version, status, activated_date, last_communication_time, created_date)
SELECT 
  'DEV-RH-' || LPAD((20+i)::text, 4, '0'),
  2,
  CASE WHEN i <= 4 THEN 4 WHEN i <= 8 THEN 5 WHEN i <= 12 THEN 6 ELSE 7 END,
  (ARRAY['POS_TERMINAL', 'MOBILE_READER', 'PAYMENT_GATEWAY'])[((i-1) % 3) + 1],
  '3.1.' || LPAD((i % 10)::text, 2, '0'),
  CASE WHEN random() < 0.90 THEN 'ACTIVE' WHEN random() < 0.95 THEN 'INACTIVE' ELSE 'MAINTENANCE' END,
  NOW() - INTERVAL '90 days' + (random() * INTERVAL '70 days'),
  NOW() - INTERVAL '1 day' + (random() * INTERVAL '23 hours'),
  NOW() - INTERVAL '90 days'
FROM generate_series(1, 15) AS i;

-- Customer 3: Restaurant Group (12 devices across 3 locations)
INSERT INTO device.device (serial_number, customer_id, location_id, device_type, firmware_version, status, activated_date, last_communication_time, created_date)
SELECT 
  'DEV-RG-' || LPAD((35+i)::text, 4, '0'),
  3,
  CASE WHEN i <= 4 THEN 8 WHEN i <= 8 THEN 9 ELSE 10 END,
  (ARRAY['POS_TERMINAL', 'MOBILE_READER'])[((i-1) % 2) + 1],
  '1.8.' || LPAD((i % 10)::text, 2, '0'),
  CASE WHEN random() < 0.88 THEN 'ACTIVE' WHEN random() < 0.93 THEN 'INACTIVE' ELSE 'MAINTENANCE' END,
  NOW() - INTERVAL '60 days' + (random() * INTERVAL '50 days'),
  NOW() - INTERVAL '1 day' + (random() * INTERVAL '23 hours'),
  NOW() - INTERVAL '60 days'
FROM generate_series(1, 12) AS i;

-- Customer 4: Gas Station Network (8 devices across 3 locations)
INSERT INTO device.device (serial_number, customer_id, location_id, device_type, firmware_version, status, activated_date, last_communication_time, created_date)
SELECT 
  'DEV-GS-' || LPAD((47+i)::text, 4, '0'),
  4,
  CASE WHEN i <= 3 THEN 11 WHEN i <= 6 THEN 12 ELSE 13 END,
  'POS_TERMINAL',
  '2.2.' || LPAD((i % 10)::text, 2, '0'),
  CASE WHEN random() < 0.92 THEN 'ACTIVE' WHEN random() < 0.96 THEN 'INACTIVE' ELSE 'MAINTENANCE' END,
  NOW() - INTERVAL '45 days' + (random() * INTERVAL '40 days'),
  NOW() - INTERVAL '1 day' + (random() * INTERVAL '23 hours'),
  NOW() - INTERVAL '45 days'
FROM generate_series(1, 8) AS i;

-- Customer 5: Quick Commerce Express (5 devices across 2 locations)
INSERT INTO device.device (serial_number, customer_id, location_id, device_type, firmware_version, status, activated_date, last_communication_time, created_date)
SELECT 
  'DEV-QC-' || LPAD((55+i)::text, 4, '0'),
  5,
  CASE WHEN i <= 3 THEN 14 ELSE 15 END,
  (ARRAY['MOBILE_READER', 'PAYMENT_GATEWAY'])[((i-1) % 2) + 1],
  '3.0.' || LPAD((i % 10)::text, 2, '0'),
  CASE WHEN random() < 0.90 THEN 'ACTIVE' ELSE 'INACTIVE' END,
  NOW() - INTERVAL '30 days' + (random() * INTERVAL '25 days'),
  NOW() - INTERVAL '1 day' + (random() * INTERVAL '23 hours'),
  NOW() - INTERVAL '30 days'
FROM generate_series(1, 5) AS i;

-- =====================================================================
-- SAMPLE DATA - DEVICE.DEVICE_STATUS_LOG (1,500 events)
-- =====================================================================
-- Generate device status events
INSERT INTO device.device_status_log (device_id, event_type, event_time, description, created_date)
SELECT 
  (1 + (i-1) % 60),
  (ARRAY['HEARTBEAT', 'HEARTBEAT', 'HEARTBEAT', 'HEARTBEAT', 'ERROR', 'ACTIVATED', 'DEACTIVATED', 'MAINTENANCE', 'COMMUNICATION_LOST'])[((i-1) % 9) + 1],
  NOW() - INTERVAL '120 days' + (random() * INTERVAL '119 days'),
  CASE 
    WHEN (i % 9) = 0 THEN 'Device error: Connection timeout'
    WHEN (i % 9) = 1 THEN 'Routine heartbeat check'
    WHEN (i % 9) = 2 THEN 'Device activated by admin'
    WHEN (i % 9) = 3 THEN 'Device deactivated'
    WHEN (i % 9) = 4 THEN 'Maintenance window started'
    WHEN (i % 9) = 5 THEN 'Lost communication with server'
    WHEN (i % 9) = 6 THEN 'Firmware update initiated'
    ELSE 'Network connectivity issue'
  END,
  NOW() - INTERVAL '120 days' + (random() * INTERVAL '119 days')
FROM generate_series(1, 1500) AS i;

-- =====================================================================
-- SAMPLE DATA - TXN.PAYMENT_TRANSACTION (10,000 transactions)
-- Distribution: 85% APPROVED, 12% DECLINED, 3% FAILED/REFUNDED
-- Time range: Last 120 days
-- =====================================================================
INSERT INTO txn.payment_transaction 
  (device_id, customer_id, amount, card_type, entry_mode, transaction_status, decline_reason, card_last_four, transaction_time, created_date)
SELECT 
  (1 + (i-1) % 60),
  CASE WHEN ((i-1) % 60) < 20 THEN 1 WHEN ((i-1) % 60) < 35 THEN 2 WHEN ((i-1) % 60) < 47 THEN 3 WHEN ((i-1) % 60) < 55 THEN 4 ELSE 5 END,
  ROUND((random() * 500 + 10)::numeric, 2),
  (ARRAY['VISA', 'MASTERCARD', 'AMEX', 'DISCOVER', 'DEBIT'])[((i-1) % 5) + 1],
  (ARRAY['SWIPE', 'INSERT', 'TAP', 'ONLINE', 'MANUAL', 'QR'])[((i-1) % 6) + 1],
  CASE 
    WHEN random() < 0.85 THEN 'APPROVED'
    WHEN random() < 0.97 THEN 'DECLINED'
    WHEN random() < 0.98 THEN 'FAILED'
    ELSE 'REFUNDED'
  END,
  CASE 
    WHEN random() < 0.85 THEN NULL
    WHEN random() < 0.92 THEN 'Insufficient Funds'
    WHEN random() < 0.97 THEN 'Card Expired'
    ELSE 'Lost Card'
  END,
  LPAD((1000 + (i % 9000))::text, 4, '0'),
  NOW() - INTERVAL '120 days' + (random() * INTERVAL '120 days'),
  NOW() - INTERVAL '120 days' + (random() * INTERVAL '120 days')
FROM generate_series(1, 10000) AS i;

-- =====================================================================
-- SAMPLE DATA - TXN.SETTLEMENT (Settlement records for sample period)
-- =====================================================================
-- Generate daily settlements for each customer
INSERT INTO txn.settlement (customer_id, settlement_date, total_amount, fees, net_amount, status, created_date)
WITH settlement_data AS (
  SELECT 
    c.customer_id,
    d::date as settlement_date,
    COALESCE(SUM(pt.amount), 0) as total_amount
  FROM corp.customer c
  CROSS JOIN (SELECT DATE(NOW() - INTERVAL '120 days' + (i * INTERVAL '1 day')) as d FROM generate_series(0, 120) i) dates
  LEFT JOIN txn.payment_transaction pt ON pt.customer_id = c.customer_id AND DATE(pt.transaction_time) = d
  WHERE pt.transaction_status = 'APPROVED'
  GROUP BY c.customer_id, d
)
SELECT 
  customer_id,
  settlement_date,
  total_amount,
  ROUND((total_amount * 0.025)::numeric, 2) as fees,
  ROUND((total_amount - (total_amount * 0.025))::numeric, 2) as net_amount,
  CASE WHEN random() < 0.95 THEN 'COMPLETED' ELSE 'PENDING' END,
  NOW()
FROM settlement_data
WHERE total_amount > 0;

-- =====================================================================
-- SAMPLE DATA - APP_USER.USER_DEVICE_ACCESS (User to Device mappings)
-- =====================================================================
-- Assign users to devices based on customer_id and location
INSERT INTO app_user.user_device_access (user_id, device_id, assigned_date)
SELECT 
  u.user_id,
  d.device_id,
  NOW() - INTERVAL '60 days' + (random() * INTERVAL '50 days')
FROM app_user.app_user u
JOIN device.device d ON u.customer_id = d.customer_id
WHERE u.customer_id IS NOT NULL
  AND u.role IN ('BUSINESS', 'MANAGER', 'CUSTOMER')
  AND random() < 0.7;  -- 70% of eligible user-device combinations

-- =====================================================================
-- SAMPLE DATA - APP.CHAT_HISTORY (Placeholder chat records)
-- =====================================================================
INSERT INTO app.chat_history (user_id, user_question, generated_sql, query_status, created_at)
VALUES
(2, 'How many transactions were approved today?', 
  'SELECT COUNT(*) FROM txn.payment_transaction WHERE customer_id = 1 AND DATE(transaction_time) = CURRENT_DATE AND transaction_status = ''APPROVED''',
  'EXECUTED', NOW() - INTERVAL '5 days'),
(3, 'What is my total transaction volume for this month?',
  'SELECT SUM(amount) FROM txn.payment_transaction WHERE customer_id = 1 AND DATE_TRUNC(''month'', transaction_time) = DATE_TRUNC(''month'', CURRENT_DATE)',
  'EXECUTED', NOW() - INTERVAL '3 days'),
(4, 'Show device status summary',
  'SELECT device_type, status, COUNT(*) FROM device.device WHERE customer_id = 2 GROUP BY device_type, status',
  'EXECUTED', NOW() - INTERVAL '2 days'),
(5, 'What are my settlement records?',
  'SELECT settlement_date, total_amount, fees, net_amount, status FROM txn.settlement WHERE customer_id = 3 ORDER BY settlement_date DESC',
  'EXECUTED', NOW() - INTERVAL '1 day'),
(6, 'Show active devices by location',
  'SELECT l.location_name, COUNT(d.device_id) FROM device.device d JOIN location.location l ON d.location_id = l.location_id WHERE d.customer_id = 4 AND d.status = ''ACTIVE'' GROUP BY l.location_name',
  'EXECUTED', NOW());

-- =====================================================================
-- SAMPLE DATA - APP_USER.USER_LOGIN_HISTORY (800 login records)
-- =====================================================================
-- Generate login history for users
INSERT INTO app_user.user_login_history (user_id, login_time, logout_time, ip_address, status, failure_reason, channel, created_date)
SELECT 
  (1 + (i-1) % 6),
  NOW() - INTERVAL '120 days' + (random() * INTERVAL '120 days'),
  CASE WHEN random() < 0.95 THEN NOW() - INTERVAL '120 days' + (random() * INTERVAL '120 days') + INTERVAL '4 hours' ELSE NULL END,
  (ARRAY['192.168.1.', '10.0.0.', '172.16.0.', '203.0.113.'])[((i-1) % 4) + 1] || 
  LPAD((1 + (i % 255))::text, 3, '0'),
  CASE 
    WHEN random() < 0.95 THEN 'SUCCESS'
    WHEN random() < 0.98 THEN 'FAILED'
    ELSE 'LOCKED'
  END,
  CASE 
    WHEN random() < 0.95 THEN NULL
    WHEN random() < 0.98 THEN 'Invalid Credentials'
    ELSE 'Account Locked'
  END,
  'WEB',
  NOW() - INTERVAL '120 days' + (random() * INTERVAL '120 days')
FROM generate_series(1, 800) AS i;

-- =====================================================================
-- VERIFY DATA INSERTION
-- =====================================================================
SELECT 'Customers' as entity, COUNT(*) as count FROM corp.customer
UNION ALL
SELECT 'Locations', COUNT(*) FROM location.location
UNION ALL
SELECT 'Users', COUNT(*) FROM app_user.app_user
UNION ALL
SELECT 'Devices', COUNT(*) FROM device.device
UNION ALL
SELECT 'Device Status Events', COUNT(*) FROM device.device_status_log
UNION ALL
SELECT 'Payment Transactions', COUNT(*) FROM txn.payment_transaction
UNION ALL
SELECT 'Settlements', COUNT(*) FROM txn.settlement
UNION ALL
SELECT 'Chat History', COUNT(*) FROM app.chat_history
UNION ALL
SELECT 'Login History', COUNT(*) FROM app_user.user_login_history
ORDER BY entity;

-- =====================================================================
-- END OF SAMPLE DATA
-- =====================================================================

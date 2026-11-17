-- =====================================================================
-- DataTalk AI - Phase 6 Data Update (realistic demo user names)
-- Run in the Neon SQL Editor as the database OWNER role (neondb_owner). Idempotent.
-- Only full_name and email change; usernames, passwords, roles and customer scope stay the same.
-- =====================================================================

UPDATE app_user.app_user AS u
SET full_name = v.full_name, email = v.email, updated_date = NOW()
FROM (VALUES
    ('admin_user',       'Bhanupriya',     'bhanupriya@datatalk.com'),
    ('techpay_manager',  'Arjun Mehta',    'arjun.mehta@techpay.com'),
    ('techpay_customer', 'Priya Raman',    'priya.raman@techpay.com'),
    ('retail_manager',   'Michael Carter', 'michael.carter@retailhub.com'),
    ('restaurant_owner', 'Daniel Brooks',  'daniel.brooks@restgrp.com'),
    ('gas_operator',     'Emily Watson',   'emily.watson@gasnet.com')
) AS v(username, full_name, email)
WHERE u.username = v.username
  AND (u.full_name IS DISTINCT FROM v.full_name OR u.email IS DISTINCT FROM v.email);

SELECT username, full_name, email, role FROM app_user.app_user ORDER BY user_id;

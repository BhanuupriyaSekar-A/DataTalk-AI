-- =====================================================================
-- DataTalk AI - Database Permissions and Roles
-- Description: Read-only reporting role and access controls
-- =====================================================================

-- =====================================================================
-- CREATE READ-ONLY REPORTING ROLE
-- =====================================================================
-- This role is designed for reporting and analytics with minimal privileges

CREATE ROLE datatalk_ro WITH LOGIN NOINHERIT;

COMMENT ON ROLE datatalk_ro IS 'Read-only reporting role for DataTalk AI analytics';

-- =====================================================================
-- GRANT READ-ONLY PERMISSIONS TO REPORTING ROLE
-- =====================================================================

-- Grant usage on business schemas (not app schema for security)
GRANT USAGE ON SCHEMA corp, location, device, txn TO datatalk_ro;

-- Grant SELECT on all existing tables in business schemas
GRANT SELECT ON ALL TABLES IN SCHEMA corp TO datatalk_ro;
GRANT SELECT ON ALL TABLES IN SCHEMA location TO datatalk_ro;
GRANT SELECT ON ALL TABLES IN SCHEMA device TO datatalk_ro;
GRANT SELECT ON ALL TABLES IN SCHEMA txn TO datatalk_ro;

-- Grant SELECT on all existing views in business schemas
GRANT SELECT ON ALL TABLES IN SCHEMA device TO datatalk_ro;
GRANT SELECT ON ALL TABLES IN SCHEMA txn TO datatalk_ro;

-- Set default privileges for future tables (corp schema)
ALTER DEFAULT PRIVILEGES IN SCHEMA corp GRANT SELECT ON TABLES TO datatalk_ro;
ALTER DEFAULT PRIVILEGES IN SCHEMA corp GRANT SELECT ON SEQUENCES TO datatalk_ro;

-- Set default privileges for future tables (location schema)
ALTER DEFAULT PRIVILEGES IN SCHEMA location GRANT SELECT ON TABLES TO datatalk_ro;
ALTER DEFAULT PRIVILEGES IN SCHEMA location GRANT SELECT ON SEQUENCES TO datatalk_ro;

-- Set default privileges for future tables (device schema)
ALTER DEFAULT PRIVILEGES IN SCHEMA device GRANT SELECT ON TABLES TO datatalk_ro;
ALTER DEFAULT PRIVILEGES IN SCHEMA device GRANT SELECT ON SEQUENCES TO datatalk_ro;

-- Set default privileges for future tables (txn schema)
ALTER DEFAULT PRIVILEGES IN SCHEMA txn GRANT SELECT ON TABLES TO datatalk_ro;
ALTER DEFAULT PRIVILEGES IN SCHEMA txn GRANT SELECT ON SEQUENCES TO datatalk_ro;

-- =====================================================================
-- DENY ACCESS TO APP SCHEMA (for read-only role)
-- =====================================================================
-- The datatalk_ro role should NOT access app schema (users, chat history, etc)
-- This is already denied by default since we didn't grant permissions

-- =====================================================================
-- CONNECTION LIMITS AND TIMEOUTS (PostgreSQL Configuration)
-- =====================================================================
-- NOTE: These configurations may not be directly configurable in Neon's managed service.
-- For Neon, such limits may be set at the project level via Neon dashboard.
-- 
-- Recommended settings for connection/query limits:
-- - Connection pool size: 20 per role
-- - Statement timeout: 30 seconds (suitable for analytics queries)
-- - Idle transaction timeout: 5 minutes
-- 
-- To apply these settings locally (if using self-hosted PostgreSQL):
-- ALTER ROLE datatalk_ro SET statement_timeout = '30 seconds';
-- ALTER ROLE datatalk_ro SET idle_in_transaction_session_timeout = '5 minutes';

-- =====================================================================
-- VERIFICATION QUERIES
-- =====================================================================

-- Verify datatalk_ro role exists
SELECT rolname FROM pg_roles WHERE rolname = 'datatalk_ro';

-- Verify schema access
SELECT schema_name FROM information_schema.schemata WHERE schema_name IN ('corp', 'location', 'device', 'txn');

-- Verify table permissions for datatalk_ro
SELECT 
  schemaname,
  tablename,
  has_table_privilege('datatalk_ro', schemaname||'.'||tablename, 'SELECT') as can_select,
  has_table_privilege('datatalk_ro', schemaname||'.'||tablename, 'INSERT') as can_insert,
  has_table_privilege('datatalk_ro', schemaname||'.'||tablename, 'UPDATE') as can_update,
  has_table_privilege('datatalk_ro', schemaname||'.'||tablename, 'DELETE') as can_delete
FROM pg_tables 
WHERE schemaname IN ('corp', 'location', 'device', 'txn')
ORDER BY schemaname, tablename;

-- =====================================================================
-- END OF PERMISSIONS
-- =====================================================================

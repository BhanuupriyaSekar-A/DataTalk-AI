-- =====================================================================
-- DataTalk AI - Phase 5 Migration (customer, location and device management for ADMIN users)
-- Run in the Neon SQL Editor as the database OWNER role (neondb_owner). Idempotent.
-- The app role needs to insert/update these records; datatalk_ro stays read-only.
-- =====================================================================

GRANT SELECT, INSERT, UPDATE ON corp.customer TO user_datatalk;
GRANT USAGE, SELECT ON SEQUENCE corp.customer_customer_id_seq TO user_datatalk;
GRANT SELECT, INSERT ON location.location, device.device TO user_datatalk;
GRANT USAGE, SELECT ON SEQUENCE location.location_location_id_seq, device.device_device_id_seq TO user_datatalk;

SELECT has_table_privilege('user_datatalk', 'corp.customer', 'INSERT') AS app_can_insert_customer,
       has_table_privilege('user_datatalk', 'corp.customer', 'UPDATE') AS app_can_update_customer,
       has_table_privilege('user_datatalk', 'location.location', 'INSERT') AS app_can_insert_location,
       has_table_privilege('user_datatalk', 'device.device', 'INSERT') AS app_can_insert_device,
       has_table_privilege('datatalk_ro', 'corp.customer', 'INSERT') AS ro_can_insert_customer; -- expected false

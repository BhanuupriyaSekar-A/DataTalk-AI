-- =====================================================================
-- DataTalk AI - PostgreSQL Schema Definition
-- Target: Neon PostgreSQL
-- Description: Database schema for payment and device management system
-- =====================================================================

-- =====================================================================
-- SCHEMAS
-- =====================================================================
CREATE SCHEMA IF NOT EXISTS corp;
COMMENT ON SCHEMA corp IS 'Corporate and customer information';

CREATE SCHEMA IF NOT EXISTS location;
COMMENT ON SCHEMA location IS 'Location and branch information';

CREATE SCHEMA IF NOT EXISTS app_user;
COMMENT ON SCHEMA app_user IS 'User management and authentication';

CREATE SCHEMA IF NOT EXISTS device;
COMMENT ON SCHEMA device IS 'Device management and status tracking';

CREATE SCHEMA IF NOT EXISTS txn;
COMMENT ON SCHEMA txn IS 'Payment transactions and settlements';

CREATE SCHEMA IF NOT EXISTS app;
COMMENT ON SCHEMA app IS 'Application-specific data like chat history';

-- =====================================================================
-- CORP SCHEMA
-- =====================================================================

-- Table: corp.customer
-- Purpose: Master customer information for cashless payment system
CREATE TABLE corp.customer (
    customer_id SERIAL PRIMARY KEY,
    customer_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    phone VARCHAR(20),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE' 
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'ARCHIVED')),
    created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_customer_email ON corp.customer(email);
CREATE INDEX idx_customer_status ON corp.customer(status);
CREATE INDEX idx_customer_created_date ON corp.customer(created_date);

COMMENT ON TABLE corp.customer IS 'Master customer table for the payment system';
COMMENT ON COLUMN corp.customer.customer_id IS 'Unique customer identifier';
COMMENT ON COLUMN corp.customer.customer_name IS 'Legal business name';
COMMENT ON COLUMN corp.customer.email IS 'Primary business email';
COMMENT ON COLUMN corp.customer.phone IS 'Primary business phone number';
COMMENT ON COLUMN corp.customer.status IS 'Account status (ACTIVE, INACTIVE, SUSPENDED, ARCHIVED)';

-- =====================================================================
-- LOCATION SCHEMA
-- =====================================================================

-- Table: location.location
-- Purpose: Customer locations/branches
CREATE TABLE location.location (
    location_id SERIAL PRIMARY KEY,
    customer_id INTEGER NOT NULL REFERENCES corp.customer(customer_id) ON DELETE CASCADE,
    location_name VARCHAR(255) NOT NULL,
    city VARCHAR(100),
    state VARCHAR(100),
    timezone VARCHAR(50) DEFAULT 'UTC',
    created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_location_customer_id ON location.location(customer_id);
CREATE INDEX idx_location_city ON location.location(city);

COMMENT ON TABLE location.location IS 'Customer locations and branches';
COMMENT ON COLUMN location.location.location_id IS 'Unique location identifier';
COMMENT ON COLUMN location.location.customer_id IS 'Associated customer';
COMMENT ON COLUMN location.location.location_name IS 'Name of the location/branch';
COMMENT ON COLUMN location.location.timezone IS 'Timezone for transaction reporting';

-- =====================================================================
-- APP_USER SCHEMA
-- =====================================================================

-- Table: app_user.app_user
-- Purpose: User accounts and authentication
CREATE TABLE app_user.app_user (
    user_id SERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE,
    role VARCHAR(50) NOT NULL 
        CHECK (role IN ('ADMIN', 'BUSINESS', 'MANAGER', 'CUSTOMER')),
    customer_id INTEGER REFERENCES corp.customer(customer_id) ON DELETE SET NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE' 
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'LOCKED', 'PENDING')),
    created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_app_user_username ON app_user.app_user(username);
CREATE INDEX idx_app_user_email ON app_user.app_user(email);
CREATE INDEX idx_app_user_customer_id ON app_user.app_user(customer_id);
CREATE INDEX idx_app_user_role ON app_user.app_user(role);
CREATE INDEX idx_app_user_status ON app_user.app_user(status);

COMMENT ON TABLE app_user.app_user IS 'Application users with role-based access';
COMMENT ON COLUMN app_user.app_user.user_id IS 'Unique user identifier';
COMMENT ON COLUMN app_user.app_user.username IS 'Login username';
COMMENT ON COLUMN app_user.app_user.password_hash IS 'BCrypt hashed password - never plain text';
COMMENT ON COLUMN app_user.app_user.role IS 'User role for authorization (ADMIN, BUSINESS, MANAGER, CUSTOMER)';
COMMENT ON COLUMN app_user.app_user.customer_id IS 'Associated customer (NULL for ADMIN)';
COMMENT ON COLUMN app_user.app_user.status IS 'Account status';

-- Table: app_user.user_login_history
-- Purpose: Track user login/logout events
CREATE TABLE app_user.user_login_history (
    login_id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES app_user.app_user(user_id) ON DELETE CASCADE,
    login_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    logout_time TIMESTAMP,
    ip_address VARCHAR(45),
    status VARCHAR(50) NOT NULL 
        CHECK (status IN ('SUCCESS', 'FAILED', 'LOCKED', 'INACTIVE_USER')),
    failure_reason VARCHAR(255),
    channel VARCHAR(50) DEFAULT 'WEB',
    created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_login_history_user_id ON app_user.user_login_history(user_id);
CREATE INDEX idx_login_history_login_time ON app_user.user_login_history(login_time);
CREATE INDEX idx_login_history_status ON app_user.user_login_history(status);

COMMENT ON TABLE app_user.user_login_history IS 'Audit log for user login/logout events';
COMMENT ON COLUMN app_user.user_login_history.login_id IS 'Unique login event identifier';
COMMENT ON COLUMN app_user.user_login_history.status IS 'SUCCESS, FAILED, LOCKED, or INACTIVE_USER';
COMMENT ON COLUMN app_user.user_login_history.failure_reason IS 'Reason for failed login (invalid password, user locked, etc)';

-- Table: app_user.user_device_access
-- Purpose: Many-to-many relationship between users and devices they can access
CREATE TABLE app_user.user_device_access (
    user_id INTEGER NOT NULL REFERENCES app_user.app_user(user_id) ON DELETE CASCADE,
    device_id INTEGER NOT NULL,
    assigned_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, device_id)
);

CREATE INDEX idx_user_device_user_id ON app_user.user_device_access(user_id);
CREATE INDEX idx_user_device_device_id ON app_user.user_device_access(device_id);

COMMENT ON TABLE app_user.user_device_access IS 'User-to-device access mapping';
COMMENT ON COLUMN app_user.user_device_access.device_id IS 'Device identifier (foreign key to device.device)';

-- =====================================================================
-- DEVICE SCHEMA
-- =====================================================================

-- Table: device.device
-- Purpose: Physical payment devices and terminals
CREATE TABLE device.device (
    device_id SERIAL PRIMARY KEY,
    serial_number VARCHAR(100) NOT NULL UNIQUE,
    customer_id INTEGER NOT NULL REFERENCES corp.customer(customer_id) ON DELETE CASCADE,
    location_id INTEGER NOT NULL REFERENCES location.location(location_id) ON DELETE CASCADE,
    device_type VARCHAR(50) NOT NULL 
        CHECK (device_type IN ('POS_TERMINAL', 'MOBILE_READER', 'PAYMENT_GATEWAY', 'ATM')),
    firmware_version VARCHAR(50),
    status VARCHAR(50) NOT NULL DEFAULT 'INACTIVE' 
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'MAINTENANCE', 'DECOMMISSIONED', 'ERROR')),
    activated_date TIMESTAMP,
    last_communication_time TIMESTAMP,
    created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_device_customer_id ON device.device(customer_id);
CREATE INDEX idx_device_location_id ON device.device(location_id);
CREATE INDEX idx_device_serial_number ON device.device(serial_number);
CREATE INDEX idx_device_status ON device.device(status);

COMMENT ON TABLE device.device IS 'Physical payment devices deployed at locations';
COMMENT ON COLUMN device.device.device_id IS 'Unique device identifier';
COMMENT ON COLUMN device.device.serial_number IS 'Manufacturer serial number';
COMMENT ON COLUMN device.device.device_type IS 'Type of payment device';
COMMENT ON COLUMN device.device.firmware_version IS 'Current device firmware version';

-- Table: device.device_status_log
-- Purpose: Event log for device status changes
CREATE TABLE device.device_status_log (
    event_id SERIAL PRIMARY KEY,
    device_id INTEGER NOT NULL REFERENCES device.device(device_id) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL 
        CHECK (event_type IN ('ACTIVATED', 'DEACTIVATED', 'HEARTBEAT', 'ERROR', 'MAINTENANCE', 'COMMUNICATION_LOST')),
    event_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    description TEXT,
    created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_device_status_log_device_id ON device.device_status_log(device_id);
CREATE INDEX idx_device_status_log_event_time ON device.device_status_log(event_time);
CREATE INDEX idx_device_status_log_event_type ON device.device_status_log(event_type);

COMMENT ON TABLE device.device_status_log IS 'Device status change event history';
COMMENT ON COLUMN device.device_status_log.event_id IS 'Unique event identifier';
COMMENT ON COLUMN device.device_status_log.event_type IS 'Type of status change event';

-- =====================================================================
-- TXN SCHEMA
-- =====================================================================

-- Table: txn.payment_transaction
-- Purpose: All payment transactions processed through devices
CREATE TABLE txn.payment_transaction (
    transaction_id SERIAL PRIMARY KEY,
    device_id INTEGER NOT NULL REFERENCES device.device(device_id) ON DELETE RESTRICT,
    customer_id INTEGER NOT NULL REFERENCES corp.customer(customer_id) ON DELETE RESTRICT,
    amount DECIMAL(15, 2) NOT NULL,
    card_type VARCHAR(50) 
        CHECK (card_type IN ('VISA', 'MASTERCARD', 'AMEX', 'DISCOVER', 'DEBIT', 'PREPAID', 'OTHER')),
    entry_mode VARCHAR(50) 
        CHECK (entry_mode IN ('SWIPE', 'INSERT', 'TAP', 'ONLINE', 'MANUAL', 'QR')),
    transaction_status VARCHAR(50) NOT NULL 
        CHECK (transaction_status IN ('APPROVED', 'DECLINED', 'PENDING', 'REFUNDED', 'FAILED', 'CANCELLED')),
    decline_reason VARCHAR(255),
    card_last_four VARCHAR(4),
    transaction_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_payment_txn_device_id ON txn.payment_transaction(device_id);
CREATE INDEX idx_payment_txn_customer_id ON txn.payment_transaction(customer_id);
CREATE INDEX idx_payment_txn_txn_status ON txn.payment_transaction(transaction_status);
CREATE INDEX idx_payment_txn_transaction_time ON txn.payment_transaction(transaction_time);
CREATE INDEX idx_payment_txn_amount ON txn.payment_transaction(amount);

COMMENT ON TABLE txn.payment_transaction IS 'Payment transactions processed through devices';
COMMENT ON COLUMN txn.payment_transaction.transaction_id IS 'Unique transaction identifier';
COMMENT ON COLUMN txn.payment_transaction.amount IS 'Transaction amount in base currency';
COMMENT ON COLUMN txn.payment_transaction.transaction_status IS 'Final transaction status';
COMMENT ON COLUMN txn.payment_transaction.card_last_four IS 'Last 4 digits of card (PCI compliance)';

-- Table: txn.settlement
-- Purpose: Daily/periodic settlement records
CREATE TABLE txn.settlement (
    settlement_id SERIAL PRIMARY KEY,
    customer_id INTEGER NOT NULL REFERENCES corp.customer(customer_id) ON DELETE RESTRICT,
    settlement_date DATE NOT NULL,
    total_amount DECIMAL(15, 2) NOT NULL,
    fees DECIMAL(15, 2) NOT NULL DEFAULT 0,
    net_amount DECIMAL(15, 2) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING' 
        CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_settlement_customer_id ON txn.settlement(customer_id);
CREATE INDEX idx_settlement_settlement_date ON txn.settlement(settlement_date);
CREATE INDEX idx_settlement_status ON txn.settlement(status);

COMMENT ON TABLE txn.settlement IS 'Settlement records for customer transactions';
COMMENT ON COLUMN txn.settlement.settlement_id IS 'Unique settlement identifier';
COMMENT ON COLUMN txn.settlement.settlement_date IS 'Date of settlement period';
COMMENT ON COLUMN txn.settlement.total_amount IS 'Total transaction amount in settlement';
COMMENT ON COLUMN txn.settlement.fees IS 'Processing fees deducted';

-- =====================================================================
-- APP SCHEMA
-- =====================================================================

-- Table: app.chat_history
-- Purpose: User queries and AI-generated SQL (for future AI integration)
CREATE TABLE app.chat_history (
    chat_id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES app_user.app_user(user_id) ON DELETE CASCADE,
    user_question TEXT NOT NULL,
    generated_sql TEXT,
    query_status VARCHAR(50) DEFAULT 'PENDING' 
        CHECK (query_status IN ('PENDING', 'EXECUTED', 'FAILED', 'INVALID_SQL')),
    query_result TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_chat_history_user_id ON app.chat_history(user_id);
CREATE INDEX idx_chat_history_created_at ON app.chat_history(created_at);

COMMENT ON TABLE app.chat_history IS 'Chat history and AI query logs for data analytics';
COMMENT ON COLUMN app.chat_history.chat_id IS 'Unique chat message identifier';
COMMENT ON COLUMN app.chat_history.user_question IS 'Natural language question from user';
COMMENT ON COLUMN app.chat_history.generated_sql IS 'SQL generated by AI (or manually crafted)';
COMMENT ON COLUMN app.chat_history.query_status IS 'Status of the generated query';

-- =====================================================================
-- FOREIGN KEY CONSTRAINTS
-- =====================================================================
-- Additional constraint: user_device_access.device_id references device.device
ALTER TABLE app_user.user_device_access
ADD CONSTRAINT fk_user_device_access_device_id 
FOREIGN KEY (device_id) REFERENCES device.device(device_id) ON DELETE CASCADE;

-- =====================================================================
-- VIEWS FOR COMMON QUERIES
-- =====================================================================

-- View: User with customer details
CREATE OR REPLACE VIEW app_user.v_user_details AS
SELECT 
    u.user_id,
    u.username,
    u.full_name,
    u.email,
    u.role,
    u.status,
    u.customer_id,
    c.customer_name,
    c.email as customer_email
FROM app_user.app_user u
LEFT JOIN corp.customer c ON u.customer_id = c.customer_id;

COMMENT ON VIEW app_user.v_user_details IS 'User information joined with customer details';

-- View: Device summary by customer
CREATE OR REPLACE VIEW device.v_device_summary AS
SELECT 
    d.customer_id,
    c.customer_name,
    COUNT(d.device_id) as total_devices,
    SUM(CASE WHEN d.status = 'ACTIVE' THEN 1 ELSE 0 END) as active_devices,
    SUM(CASE WHEN d.status = 'INACTIVE' THEN 1 ELSE 0 END) as inactive_devices,
    SUM(CASE WHEN d.status = 'MAINTENANCE' THEN 1 ELSE 0 END) as maintenance_devices
FROM device.device d
JOIN corp.customer c ON d.customer_id = c.customer_id
GROUP BY d.customer_id, c.customer_name;

COMMENT ON VIEW device.v_device_summary IS 'Device count and status summary by customer';

-- View: Transaction summary by customer
CREATE OR REPLACE VIEW txn.v_transaction_summary AS
SELECT 
    pt.customer_id,
    c.customer_name,
    DATE(pt.transaction_time) as transaction_date,
    COUNT(pt.transaction_id) as total_transactions,
    SUM(CASE WHEN pt.transaction_status = 'APPROVED' THEN 1 ELSE 0 END) as approved_count,
    SUM(CASE WHEN pt.transaction_status IN ('DECLINED', 'FAILED') THEN 1 ELSE 0 END) as failed_count,
    SUM(pt.amount) as total_amount,
    SUM(CASE WHEN pt.transaction_status = 'APPROVED' THEN pt.amount ELSE 0 END) as approved_amount
FROM txn.payment_transaction pt
JOIN corp.customer c ON pt.customer_id = c.customer_id
GROUP BY pt.customer_id, c.customer_name, DATE(pt.transaction_time);

COMMENT ON VIEW txn.v_transaction_summary IS 'Daily transaction summary by customer';

-- =====================================================================
-- END OF SCHEMA
-- =====================================================================

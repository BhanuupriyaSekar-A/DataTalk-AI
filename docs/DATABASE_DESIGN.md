# DataTalk AI - Database Design Documentation

## Overview

The DataTalk AI database is designed to support a cashless payment and device management system. It uses a multi-schema architecture for better organization and security.

---

## Architecture & Schemas

### Schema Organization

```
┌─────────────────────────────────────────────┐
│         DataTalk AI Database                │
├─────────────────────────────────────────────┤
│ corp              │ Customer master data    │
│ location          │ Branch & location info │
│ app_user          │ Authentication & users │
│ device            │ Terminal management    │
│ txn               │ Payments & settlements │
│ app               │ Application data       │
└─────────────────────────────────────────────┘
```

### Security Rationale

- **Separation of Concerns**: Each schema has a specific purpose
- **Data Isolation**: Business data separate from operational data
- **Access Control**: Different permissions per schema
- **Audit Trail**: user_login_history in app_user for tracking

---

## Detailed Schema Design

### 1. CORP Schema - Customer & Company Data

**Purpose**: Master customer information for the payment system

#### Tables

##### corp.customer
Primary customer table for all business accounts.

| Column | Type | Constraints | Purpose |
|--------|------|-------------|---------|
| customer_id | SERIAL | PK | Unique customer identifier |
| customer_name | VARCHAR(255) | NOT NULL, UNIQUE | Legal business name |
| email | VARCHAR(255) | NOT NULL, UNIQUE | Primary business email |
| phone | VARCHAR(20) | | Business phone number |
| status | VARCHAR(50) | NOT NULL | ACTIVE, INACTIVE, SUSPENDED, ARCHIVED |
| created_date | TIMESTAMP | NOT NULL | Account creation date |
| updated_date | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Last update |

**Indexes**:
- `idx_customer_email`: Fast lookup by email
- `idx_customer_status`: Filter by account status
- `idx_customer_created_date`: Time-based queries

**Foreign Keys**: None (root entity)

---

### 2. LOCATION Schema - Branches & Sites

**Purpose**: Customer locations, branches, and operational sites

#### Tables

##### location.location
Stores individual customer locations.

| Column | Type | Constraints | Purpose |
|--------|------|-------------|---------|
| location_id | SERIAL | PK | Unique location identifier |
| customer_id | INTEGER | FK(corp.customer) | Associated customer |
| location_name | VARCHAR(255) | NOT NULL | Name of location/branch |
| city | VARCHAR(100) | | City name |
| state | VARCHAR(100) | | State/Province |
| timezone | VARCHAR(50) | DEFAULT 'UTC' | Local timezone |
| created_date | TIMESTAMP | NOT NULL | Creation timestamp |

**Indexes**:
- `idx_location_customer_id`: Find locations by customer
- `idx_location_city`: Geographic queries

**Relationships**: 1 Customer : N Locations

**Use Cases**:
- Store locations for multi-branch customers
- Timezone-based reporting
- Device location tracking

---

### 3. APP_USER Schema - Authentication & Users

**Purpose**: User management, authentication, and audit logging

#### Tables

##### app_user.app_user
Main user account table with role-based access.

| Column | Type | Constraints | Purpose |
|--------|------|-------------|---------|
| user_id | SERIAL | PK | Unique user identifier |
| username | VARCHAR(100) | NOT NULL, UNIQUE | Login username |
| password_hash | VARCHAR(255) | NOT NULL | BCrypt hashed password |
| full_name | VARCHAR(255) | NOT NULL | User's full name |
| email | VARCHAR(255) | UNIQUE | User email address |
| role | VARCHAR(50) | NOT NULL | ADMIN, BUSINESS, MANAGER, CUSTOMER |
| customer_id | INTEGER | FK(corp.customer) | Associated customer (NULL for ADMIN) |
| status | VARCHAR(50) | NOT NULL | ACTIVE, INACTIVE, LOCKED, PENDING |
| created_date | TIMESTAMP | NOT NULL | Account creation date |
| updated_date | TIMESTAMP | | Last update |

**Indexes**:
- `idx_app_user_username`: Fast login lookup
- `idx_app_user_email`: Email verification
- `idx_app_user_customer_id`: Find users by customer
- `idx_app_user_role`: Permission-based queries
- `idx_app_user_status`: Active user filtering

**Roles**:
- **ADMIN**: System administrator with full access
- **BUSINESS**: Business owner/primary account contact
- **MANAGER**: Manager with department-level access
- **CUSTOMER**: Regular user with limited access

**Security Notes**:
- Passwords are NEVER stored as plain text
- Only BCrypt hashes are stored
- password_hash field cannot be read by reporting role

##### app_user.user_login_history
Audit trail for all user login attempts.

| Column | Type | Constraints | Purpose |
|--------|------|-------------|---------|
| login_id | SERIAL | PK | Unique login record ID |
| user_id | INTEGER | FK(app_user) | User attempting login |
| login_time | TIMESTAMP | NOT NULL | Login timestamp |
| logout_time | TIMESTAMP | | Logout timestamp (NULL if still logged in) |
| ip_address | VARCHAR(45) | | Client IP address |
| status | VARCHAR(50) | NOT NULL | SUCCESS, FAILED, LOCKED, INACTIVE_USER |
| failure_reason | VARCHAR(255) | | Reason if failed |
| channel | VARCHAR(50) | DEFAULT 'WEB' | Login channel |
| created_date | TIMESTAMP | NOT NULL | Record creation |

**Indexes**:
- `idx_login_history_user_id`: User login tracking
- `idx_login_history_login_time`: Time-based queries
- `idx_login_history_status`: Failed attempt analysis

**Uses**:
- Security audit trail
- Fraud detection (multiple failed attempts)
- Session duration analysis
- Geographic access tracking

##### app_user.user_device_access
Maps users to devices they can access (many-to-many).

| Column | Type | Constraints | Purpose |
|--------|------|-------------|---------|
| user_id | INTEGER | FK(app_user) | User identifier |
| device_id | INTEGER | FK(device.device) | Device identifier |
| assigned_date | TIMESTAMP | NOT NULL | When access was granted |

**Constraints**: Composite primary key (user_id, device_id)

**Uses**:
- Device access control
- Permission management
- Audit trail for device access

---

### 4. DEVICE Schema - Payment Terminal Management

**Purpose**: Track physical payment devices and their status

#### Tables

##### device.device
Master device table for all payment terminals.

| Column | Type | Constraints | Purpose |
|--------|------|-------------|---------|
| device_id | SERIAL | PK | Unique device identifier |
| serial_number | VARCHAR(100) | NOT NULL, UNIQUE | Manufacturer serial number |
| customer_id | INTEGER | FK(corp.customer) | Device owner |
| location_id | INTEGER | FK(location.location) | Deployment location |
| device_type | VARCHAR(50) | NOT NULL | POS_TERMINAL, MOBILE_READER, PAYMENT_GATEWAY, ATM |
| firmware_version | VARCHAR(50) | | Current firmware version |
| status | VARCHAR(50) | NOT NULL | ACTIVE, INACTIVE, MAINTENANCE, DECOMMISSIONED, ERROR |
| activated_date | TIMESTAMP | | Activation date |
| last_communication_time | TIMESTAMP | | Last heartbeat from device |
| created_date | TIMESTAMP | NOT NULL | Record creation |

**Indexes**:
- `idx_device_customer_id`: Devices by customer
- `idx_device_location_id`: Devices by location
- `idx_device_serial_number`: Device lookup
- `idx_device_status`: Status filtering

**Device Types**:
- **POS_TERMINAL**: Traditional point-of-sale terminal
- **MOBILE_READER**: Mobile payment reader
- **PAYMENT_GATEWAY**: Online payment gateway
- **ATM**: Automated teller machine

**Status Values**:
- **ACTIVE**: Device operational and processing transactions
- **INACTIVE**: Inactive but deployable
- **MAINTENANCE**: Under maintenance
- **DECOMMISSIONED**: Permanently removed from service
- **ERROR**: Device experiencing issues

##### device.device_status_log
Event log for all device status changes.

| Column | Type | Constraints | Purpose |
|--------|------|-------------|---------|
| event_id | SERIAL | PK | Unique event identifier |
| device_id | INTEGER | FK(device.device) | Device |
| event_type | VARCHAR(50) | NOT NULL | ACTIVATED, DEACTIVATED, HEARTBEAT, ERROR, MAINTENANCE, COMMUNICATION_LOST |
| event_time | TIMESTAMP | NOT NULL | Event timestamp |
| description | TEXT | | Event details |
| created_date | TIMESTAMP | NOT NULL | Record creation |

**Indexes**:
- `idx_device_status_log_device_id`: Device event history
- `idx_device_status_log_event_time`: Time-based queries
- `idx_device_status_log_event_type`: Event type filtering

**Event Types**:
- **HEARTBEAT**: Regular health check
- **ERROR**: Device error reported
- **ACTIVATED**: Device brought online
- **DEACTIVATED**: Device taken offline
- **MAINTENANCE**: Maintenance window
- **COMMUNICATION_LOST**: Lost network connectivity

---

### 5. TXN Schema - Transactions & Settlements

**Purpose**: Payment transaction processing and settlement management

#### Tables

##### txn.payment_transaction
All payment transactions processed through devices.

| Column | Type | Constraints | Purpose |
|--------|------|-------------|---------|
| transaction_id | SERIAL | PK | Unique transaction ID |
| device_id | INTEGER | FK(device.device) | Processing device |
| customer_id | INTEGER | FK(corp.customer) | Transaction customer |
| amount | DECIMAL(15,2) | NOT NULL | Transaction amount |
| card_type | VARCHAR(50) | | VISA, MASTERCARD, AMEX, DISCOVER, DEBIT, PREPAID, OTHER |
| entry_mode | VARCHAR(50) | | SWIPE, INSERT, TAP, ONLINE, MANUAL, QR |
| transaction_status | VARCHAR(50) | NOT NULL | APPROVED, DECLINED, PENDING, REFUNDED, FAILED, CANCELLED |
| decline_reason | VARCHAR(255) | | Reason if declined |
| card_last_four | VARCHAR(4) | | Last 4 digits only (PCI compliance) |
| transaction_time | TIMESTAMP | NOT NULL | Transaction timestamp |
| created_date | TIMESTAMP | NOT NULL | Record creation |

**Indexes**:
- `idx_payment_txn_device_id`: Transactions by device
- `idx_payment_txn_customer_id`: Transactions by customer
- `idx_payment_txn_txn_status`: Status filtering
- `idx_payment_txn_transaction_time`: Time-based queries
- `idx_payment_txn_amount`: Amount-based analysis

**Status Distribution (Sample Data)**:
- APPROVED: 85%
- DECLINED: 12%
- FAILED: 2%
- REFUNDED: 1%

**PCI Compliance**:
- Only last 4 digits stored
- Full card numbers never stored
- Secure handling required

##### txn.settlement
Daily/periodic settlement records.

| Column | Type | Constraints | Purpose |
|--------|------|-------------|---------|
| settlement_id | SERIAL | PK | Unique settlement ID |
| customer_id | INTEGER | FK(corp.customer) | Settled customer |
| settlement_date | DATE | NOT NULL | Settlement period date |
| total_amount | DECIMAL(15,2) | NOT NULL | Total transaction amount |
| fees | DECIMAL(15,2) | NOT NULL | Processing fees |
| net_amount | DECIMAL(15,2) | NOT NULL | Amount after fees |
| status | VARCHAR(50) | NOT NULL | PENDING, PROCESSING, COMPLETED, FAILED, CANCELLED |
| created_date | TIMESTAMP | NOT NULL | Record creation |

**Indexes**:
- `idx_settlement_customer_id`: Settlements by customer
- `idx_settlement_settlement_date`: Date-based queries
- `idx_settlement_status`: Status filtering

**Status Values**:
- **PENDING**: Awaiting processing
- **PROCESSING**: Settlement in progress
- **COMPLETED**: Settlement finished
- **FAILED**: Settlement failed
- **CANCELLED**: Settlement cancelled

---

### 6. APP Schema - Application Data

**Purpose**: Application-specific data like chat history and queries

#### Tables

##### app.chat_history
Chat/query history for AI integration (Phase 2).

| Column | Type | Constraints | Purpose |
|--------|------|-------------|---------|
| chat_id | SERIAL | PK | Unique chat message ID |
| user_id | INTEGER | FK(app_user) | User asking question |
| user_question | TEXT | NOT NULL | Natural language question |
| generated_sql | TEXT | | Generated SQL query |
| query_status | VARCHAR(50) | DEFAULT 'PENDING' | PENDING, EXECUTED, FAILED, INVALID_SQL |
| query_result | TEXT | | Query result |
| created_at | TIMESTAMP | NOT NULL | Message timestamp |

**Indexes**:
- `idx_chat_history_user_id`: Chat history by user
- `idx_chat_history_created_at`: Time-based queries

**Status Values**:
- **PENDING**: Awaiting execution
- **EXECUTED**: Successfully executed
- **FAILED**: Execution failed
- **INVALID_SQL**: SQL validation failed

---

## Views

### app_user.v_user_details
Joins user information with customer details.

```sql
SELECT 
    u.user_id, u.username, u.full_name, u.email, u.role, u.status,
    c.customer_id, c.customer_name, c.email as customer_email
FROM app_user.app_user u
LEFT JOIN corp.customer c ON u.customer_id = c.customer_id;
```

**Use Case**: Display user info with customer context

### device.v_device_summary
Device count and status by customer.

```sql
SELECT 
    d.customer_id,
    c.customer_name,
    COUNT(d.device_id) as total_devices,
    SUM(CASE WHEN d.status = 'ACTIVE' THEN 1 ELSE 0 END) as active_devices,
    SUM(CASE WHEN d.status = 'INACTIVE' THEN 1 ELSE 0 END) as inactive_devices
FROM device.device d
JOIN corp.customer c ON d.customer_id = c.customer_id
GROUP BY d.customer_id, c.customer_name;
```

**Use Case**: Device dashboard and reporting

### txn.v_transaction_summary
Daily transaction summary by customer.

```sql
SELECT 
    pt.customer_id,
    c.customer_name,
    DATE(pt.transaction_time) as transaction_date,
    COUNT(pt.transaction_id) as total_transactions,
    SUM(CASE WHEN pt.transaction_status = 'APPROVED' THEN 1 ELSE 0 END) as approved_count,
    SUM(pt.amount) as total_amount,
    SUM(CASE WHEN pt.transaction_status = 'APPROVED' THEN pt.amount ELSE 0 END) as approved_amount
FROM txn.payment_transaction pt
JOIN corp.customer c ON pt.customer_id = c.customer_id
GROUP BY pt.customer_id, c.customer_name, DATE(pt.transaction_time);
```

**Use Case**: Transaction analytics and reporting

---

## Relationships

### Entity Relationship Overview

```
corp.customer (1) ──── (N) location.location
                   ├──── (N) app_user.app_user
                   ├──── (N) device.device
                   └──── (N) txn.payment_transaction

device.device (1) ────────── (N) txn.payment_transaction
                   └────────── (N) device.device_status_log

app_user.app_user (1) ────────── (N) app_user.user_login_history
                      ├────────── (N) app_user.user_device_access
                      └────────── (N) app.chat_history

location.location (1) ──── (N) device.device
```

### Relationship Types

| Parent | Child | Type | Notes |
|--------|-------|------|-------|
| corp.customer | location.location | 1:N | CASCADE DELETE |
| corp.customer | app_user.app_user | 1:N | SET NULL |
| corp.customer | device.device | 1:N | CASCADE DELETE |
| corp.customer | txn.payment_transaction | 1:N | RESTRICT DELETE |
| corp.customer | txn.settlement | 1:N | RESTRICT DELETE |
| location.location | device.device | 1:N | CASCADE DELETE |
| device.device | txn.payment_transaction | 1:N | RESTRICT DELETE |
| device.device | device.device_status_log | 1:N | CASCADE DELETE |
| device.device | app_user.user_device_access | 1:N | CASCADE DELETE |
| app_user.app_user | app_user.user_login_history | 1:N | CASCADE DELETE |
| app_user.app_user | app_user.user_device_access | 1:N | CASCADE DELETE |
| app_user.app_user | app.chat_history | 1:N | CASCADE DELETE |

---

## Data Integrity & Constraints

### Primary Keys
All tables have SERIAL auto-incrementing primary keys for uniqueness.

### Foreign Keys
- CASCADE DELETE used for dependent data (device → status log)
- RESTRICT DELETE used for important data (customer → transactions)
- SET NULL used for optional relationships (user → customer)

### Unique Constraints
- `corp.customer.email`: No duplicate customer emails
- `app_user.app_user.username`: No duplicate usernames
- `app_user.app_user.email`: No duplicate user emails
- `device.device.serial_number`: No duplicate serial numbers

### Check Constraints
All status and type columns use CHECK constraints to ensure valid values.

---

## Sample Data Statistics

| Entity | Count | Notes |
|--------|-------|-------|
| Customers | 5 | Diverse industries |
| Locations | 15 | Multi-branch setup |
| Devices | 60 | Mix of types |
| Users | 6 | ADMIN + per-customer |
| Transactions | 10,000 | 120-day period |
| Device Status Events | 1,500 | Rich history |
| Login Records | 800 | Audit trail |
| Settlement Records | ~120 | Daily settlements |

---

## Performance Optimization

### Indexing Strategy
- Foreign key columns indexed for joins
- Status columns indexed for filtering
- Date columns indexed for range queries
- Username/email indexed for lookup

### Query Optimization
- Views created for common aggregations
- Proper column selection (not SELECT *)
- Parameterized queries (prepared statements)
- Connection pooling enabled

### Scaling Considerations
- Horizontal partitioning by customer possible
- Archive old transactions for performance
- Regular index maintenance recommended
- Query performance monitoring needed

---

## Security Measures

### Data Protection
- Passwords hashed with BCrypt (never plain text)
- Card numbers truncated (last 4 digits only)
- Sensitive data NOT in app schema for easy restriction
- Row-level security via application logic

### Access Control
- Schema-level permissions
- Role-based views
- Read-only reporting role (datatalk_ro)
- Audit logging for sensitive operations

### Compliance
- PCI DSS compliant (card data handling)
- GDPR considerations (user data storage)
- Data retention policy needed
- Encryption at-rest recommended

---

## Maintenance & Monitoring

### Regular Tasks
- Index maintenance (ANALYZE, VACUUM)
- Query performance monitoring
- Backup verification
- Growth trending

### Monitoring Queries
- Failed login attempts
- Device status changes
- Transaction trends
- Settlement status

---

**Last Updated**: November 2026  
**Version**: 1.0  
**Status**: Production Ready

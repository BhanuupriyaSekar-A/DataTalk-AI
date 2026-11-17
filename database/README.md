# DataTalk AI - Database Setup Guide

## Overview

DataTalk AI uses a Neon PostgreSQL database with six schemas:
- **corp**: Customer master data
- **location**: Customer locations and branches
- **app_user**: User management and authentication
- **device**: Payment device management
- **txn**: Transaction and settlement data
- **app**: Application data (chat history, logs)

## Quick Start

### 1. Prerequisites
- Neon PostgreSQL account and project
- Neon SQL Editor access
- Connection details (host, database, user, password)

### 2. Running the Schema

**Step 1**: Go to **Neon Console** → Your Project → SQL Editor

**Step 2**: Open `database/neon-schema.sql` and copy the entire content

**Step 3**: Paste into Neon SQL Editor and execute

**Expected Result**: All schemas, tables, indexes, and views created successfully

**Verification**: Run this query to confirm:
```sql
SELECT schema_name FROM information_schema.schemata 
WHERE schema_name IN ('corp', 'location', 'app_user', 'device', 'txn', 'app')
ORDER BY schema_name;
```

Should return 6 schemas.

### 3. Loading Sample Data

**Step 1**: Copy content from `database/sample-data.sql`

**Step 2**: Paste into Neon SQL Editor and execute

**Expected Result**: 
- 5 customers
- 15 locations
- 60 devices
- 6 users (including demo users)
- 10,000 payment transactions
- 800 login history records
- 1,500 device status events
- Settlement records for last 120 days

**Verification**: Run the verification query at the end of sample-data.sql to see record counts

### 4. Setting Up Permissions (Optional)

**Note**: Neon's managed service has limited role management. These commands are for reference.

If you want to create a read-only reporting role:

**Step 1**: Copy content from `database/permissions.sql`

**Step 2**: Paste into Neon SQL Editor and execute

**Result**: Creates `datatalk_ro` role with SELECT-only access to business schemas

### 5. Connection String

For Neon, your connection string looks like:
```
postgresql://[user]:[password]@[host]/[database]?sslmode=require
```

Example (placeholder):
```
postgresql://datatalk_user:yourpassword123@ep-cool-lake-12345.us-east-1.aws.neon.tech/datatalk_db?sslmode=require
```

Get this from Neon Console → Connection String

## Demo Users

All demo users have password: `DemoPass@123`

BCrypt Hash: `$2a$10$2j.5.6EGZhg9Z4eK4qk0l.qXFwGJ2V9bq5Y3X8Pl8Xz9K6vB8R.vW`

| Username | Role | Customer | Email |
|----------|------|----------|-------|
| admin_user | ADMIN | - | bhanupriya@datatalk.com |
| techpay_manager | BUSINESS | TechPay Solutions | arjun.mehta@techpay.com |
| techpay_customer | CUSTOMER | TechPay Solutions | priya.raman@techpay.com |
| retail_manager | MANAGER | RetailHub Inc | michael.carter@retailhub.com |
| restaurant_owner | BUSINESS | Restaurant Group USA | daniel.brooks@restgrp.com |
| gas_operator | CUSTOMER | Gas Station Network | emily.watson@gasnet.com |

## Sample Data

### Customers
- TechPay Solutions (5 devices, 60% active)
- RetailHub Inc (15 devices, 75% active)
- Restaurant Group USA (12 devices, 80% active)
- Gas Station Network (8 devices, 90% active)
- Quick Commerce Express (5 devices, 85% active)

### Transaction Statistics
- **Total**: 10,000 transactions
- **Approved**: 8,500 (85%)
- **Declined**: 1,200 (12%)
- **Failed/Refunded**: 300 (3%)
- **Period**: Last 120 days
- **Amount Range**: $10 - $510 per transaction

### Locations
15 locations spread across major US cities:
- New York, Brooklyn, Newark (TechPay)
- Los Angeles, Santa Monica, San Francisco, Chicago (RetailHub)
- New York, Miami, Austin (Restaurant Group)
- Houston, Dallas, San Antonio (Gas Station Network)
- Seattle, Denver (QuickCom)

## Database Structure

### Key Tables

1. **corp.customer** - Customer accounts
2. **location.location** - Customer locations/branches
3. **app_user.app_user** - User accounts with hashed passwords
4. **app_user.user_login_history** - Login audit trail
5. **device.device** - Payment terminals and readers
6. **device.device_status_log** - Device status events
7. **txn.payment_transaction** - All payment transactions
8. **txn.settlement** - Daily settlements
9. **app.chat_history** - Chat/query history

### Key Indexes
All tables have appropriate indexes on:
- Primary keys
- Foreign keys
- Frequently searched columns (customer_id, status, dates)
- Composite indexes for common queries

### Views
- `app_user.v_user_details` - User + Customer info
- `device.v_device_summary` - Device count by customer
- `txn.v_transaction_summary` - Daily transaction summaries

## Security Considerations

1. **Password Storage**: All user passwords are BCrypt hashed (never plain text)
2. **Sensitive Data**: Card numbers store only last 4 digits (PCI compliant)
3. **Row-Level Security**: Application enforces customer data isolation
4. **Read-Only Role**: `datatalk_ro` for reporting without write access
5. **Audit Trail**: Login history and status logs track all activities

## Connection from Spring Boot

```properties
# application.properties
spring.datasource.url=postgresql://[host]/[database]
spring.datasource.username=[user]
spring.datasource.password=[password]
spring.datasource.driver-class-name=org.postgresql.Driver
spring.datasource.hikari.maximum-pool-size=20
```

## Troubleshooting

### Connection Errors
- Check Neon Console for connection string
- Verify IP whitelisting (if enabled)
- Ensure SSL mode is set to 'require' for Neon

### Schema Not Found
- Verify schema creation completed without errors
- Check with: `SELECT * FROM information_schema.schemata;`

### Sample Data Errors
- Schema must be created first
- Check for missing customer IDs in foreign keys
- Run verification queries to identify issues

### Permission Denied
- Verify user role has SELECT permissions
- Use default user created during Neon project setup
- Check role permissions with verification queries

## Next Steps

1. Create Spring Boot application
2. Configure application.properties with connection string
3. Implement user authentication with Spring Security
4. Create login page and dashboard
5. Add role-based access control
6. Implement data access layer

## Support

For issues with:
- **Neon**: https://neon.tech/docs
- **PostgreSQL**: https://www.postgresql.org/docs/
- **This project**: Check docs/ directory for more details

---

**Last Updated**: November 2026
**Schema Version**: 1.0

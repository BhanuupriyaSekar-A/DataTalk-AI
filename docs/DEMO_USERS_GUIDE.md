# SL Talk AI - Demo Users Guide

## Overview
This document provides information about demo users available for testing the SL Talk AI application.

## All Demo Users

| Username | Full Name | Role | Customer | Customer ID | Password | Status |
|----------|-----------|------|----------|-------------|----------|--------|
| `admin_user` | Bhanupriya | ADMIN | N/A (System-wide) | NULL | `DemoPass@123` | ACTIVE |
| `techpay_manager` | Arjun Mehta | BUSINESS | TechPay Solutions | 1 | `DemoPass@123` | ACTIVE |
| `techpay_customer` | Priya Raman | CUSTOMER | TechPay Solutions | 1 | `DemoPass@123` | ACTIVE |
| `retail_manager` | Michael Carter | MANAGER | RetailHub Inc | 2 | `DemoPass@123` | ACTIVE |
| `restaurant_owner` | Daniel Brooks | BUSINESS | Restaurant Group USA | 3 | `DemoPass@123` | ACTIVE |
| `gas_operator` | Emily Watson | CUSTOMER | Gas Station Network | 4 | `DemoPass@123` | ACTIVE |

The navbar shows each user's full name with a readable role title: ADMIN → *Administrator*, BUSINESS → *Business Manager*,
MANAGER → *Store Manager*, CUSTOMER → *Customer Analyst* (display only; authorization uses the role codes).
Existing databases seeded before this change can be updated with `database/phase6-user-names.sql`.

## Role Definitions

### ADMIN
- **Permissions**: System-wide access to all data
- **Use Case**: System administrators who manage all customers and data
- **Demo User**: `admin_user`
- **Can Access**: 
  - Dashboard with system-wide metrics
  - All devices across all customers
  - All transactions
  - All settlements
  - System reports

### BUSINESS
- **Permissions**: Full access to their assigned customer's data
- **Use Case**: Business owners or managers responsible for a customer account
- **Demo Users**: `techpay_manager` (TechPay), `restaurant_owner` (Restaurant Group)
- **Customer ID**: 1 (TechPay), 3 (Restaurant Group)
- **Can Access**: 
  - Their customer's dashboard and metrics
  - Their customer's devices
  - Their customer's transactions
  - Their customer's settlements

### MANAGER
- **Permissions**: Limited access to their assigned customer's data
- **Use Case**: Location or team managers
- **Demo User**: `retail_manager` (RetailHub)
- **Customer ID**: 2 (RetailHub)
- **Can Access**: 
  - Their customer's dashboard (read-only)
  - Their customer's devices
  - Their customer's recent transactions

### CUSTOMER
- **Permissions**: Limited access to their customer's data (read-only)
- **Use Case**: End users or customer representatives
- **Demo Users**: `techpay_customer` (TechPay), `gas_operator` (Gas Station)
- **Customer ID**: 1 (TechPay), 4 (Gas Station)
- **Can Access**: 
  - Dashboard with their customer's metrics
  - View transactions
  - View device status

## Password Information

### Demo Password
**All demo users use the same password for testing:**
```
Password: DemoPass@123
```

### Password Hash
All demo users share this BCrypt hash:
```
$2a$10$muZDonRTJymtmicJtZ2FZ.MJ7zWFbR9VoQP.uQkAMPaQzInXqyVNa
```

⚠️ **Security Note**: In production, each user should have a unique, strong password. Never use demo passwords in production!

## Testing Scenarios

### Scenario 1: Admin Access (System-wide View)
```
Username: admin_user
Password: DemoPass@123
Expected View: All customers, all devices, all transactions, system statistics
```

### Scenario 2: Business Owner View
```
Username: techpay_manager OR restaurant_owner
Password: DemoPass@123
Expected View: Only their customer's data
```

### Scenario 3: Manager View (Limited)
```
Username: retail_manager
Password: DemoPass@123
Expected View: Customer's data with limited permissions
```

### Scenario 4: Customer/Operator View (Read-Only)
```
Username: techpay_customer OR gas_operator
Password: DemoPass@123
Expected View: Read-only access to customer metrics
```

## Customer Data Associated with Demo Users

### TechPay Solutions (Customer ID: 1)
- **Demo Users**: `techpay_manager` (BUSINESS), `techpay_customer` (CUSTOMER)
- **Locations**: 3
  - TechPay - Manhattan HQ (New York, NY)
  - TechPay - Brooklyn Office (Brooklyn, NY)
  - TechPay - New Jersey Hub (Newark, NJ)
- **Devices**: 20
- **Status**: ACTIVE

### RetailHub Inc (Customer ID: 2)
- **Demo Users**: `retail_manager` (MANAGER)
- **Locations**: 4
  - RetailHub - Downtown LA (Los Angeles, CA)
  - RetailHub - Santa Monica (Santa Monica, CA)
  - RetailHub - San Francisco (San Francisco, CA)
  - RetailHub - Chicago (Chicago, IL)
- **Devices**: 15
- **Status**: ACTIVE

### Restaurant Group USA (Customer ID: 3)
- **Demo Users**: `restaurant_owner` (BUSINESS)
- **Locations**: 3
  - Restaurant Group - Times Square (New York, NY)
  - Restaurant Group - Miami Beach (Miami, FL)
  - Restaurant Group - Austin Downtown (Austin, TX)
- **Devices**: 12
- **Status**: ACTIVE

### Gas Station Network (Customer ID: 4)
- **Demo Users**: `gas_operator` (CUSTOMER)
- **Locations**: 3
  - Gas Station - Houston (Houston, TX)
  - Gas Station - Dallas (Dallas, TX)
  - Gas Station - San Antonio (San Antonio, TX)
- **Devices**: 8
- **Status**: ACTIVE

### Quick Commerce Express (Customer ID: 5)
- **Demo Users**: None assigned yet (can be used for future testing)
- **Locations**: 2
  - QuickCom - Seattle (Seattle, WA)
  - QuickCom - Denver (Denver, CO)
- **Devices**: 5
- **Status**: ACTIVE

## How to Reset a Demo User Password

If you need to change a demo user's password, run this SQL in Neon:

```sql
-- Generate new BCrypt hash (replace with your hash)
UPDATE app_user.app_user 
SET password_hash = '$2a$10$YOUR_NEW_BCRYPT_HASH_HERE'
WHERE username = 'admin_user';
```

To generate a BCrypt hash, you can use:
1. Online BCrypt generator (for testing only)
2. Spring's BCryptPasswordEncoder in Java
3. Command-line tools like `htpasswd`

## Login Testing Sequence

### Quick Test Path:
1. Start application: `http://localhost:8081/sltalk`
2. Click **Login** on homepage
3. Try each demo user:
   - `admin_user` / `DemoPass@123` → See system-wide dashboard
   - `techpay_manager` / `DemoPass@123` → See TechPay customer data
   - `retail_manager` / `DemoPass@123` → See RetailHub customer data

### API Testing:
```bash
# Login API
curl -X POST http://localhost:8081/sltalk/api/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin_user", "password": "DemoPass@123"}'

# Get User Info
curl -X GET http://localhost:8081/sltalk/api/user-info \
  -H "Cookie: JSESSIONID=<session_id>"

# Logout
curl -X POST http://localhost:8081/sltalk/api/logout \
  -H "Cookie: JSESSIONID=<session_id>"
```

## Database Verification

To verify demo users exist in the database:

```sql
-- View all demo users
SELECT user_id, username, full_name, role, customer_id, status 
FROM app_user.app_user 
ORDER BY role, username;

-- View user count by role
SELECT role, COUNT(*) as count 
FROM app_user.app_user 
GROUP BY role 
ORDER BY role;

-- Check specific user
SELECT user_id, username, full_name, role, customer_id, status, created_date
FROM app_user.app_user 
WHERE username = 'admin_user';
```

## Troubleshooting

### Login Fails for All Users
1. ✓ Verify database is initialized (`neon-schema.sql` and `sample-data.sql` ran)
2. ✓ Check `app_user.app_user` table has records
3. ✓ Verify database connection string in `application.properties`

### Login Works but No Data Shows
1. ✓ Verify `customer_id` matches customer records
2. ✓ Check device and transaction data exists
3. ✓ Verify user's `role` allows access to dashboard

### Password Hash Not Recognized
1. ✓ Ensure it's a valid BCrypt hash (starts with $2a$, $2b$, or $2y$)
2. ✓ Check the cost factor (10 by default)
3. ✓ Verify Spring Security's BCryptPasswordEncoder configuration

## Security Best Practices

⚠️ **For Production Environments:**
1. ❌ **Never** use demo passwords in production
2. ❌ **Never** hardcode passwords in code
3. ✅ Use environment variables or secrets management
4. ✅ Require strong, unique passwords for each user
5. ✅ Implement password expiration policies
6. ✅ Use password reset functionality
7. ✅ Enable multi-factor authentication (future phase)
8. ✅ Audit all login/logout events
9. ✅ Implement rate limiting on login attempts
10. ✅ Use HTTPS/TLS for all connections

## Next Steps

- [ ] Create additional demo users for specific testing
- [ ] Implement password reset functionality
- [ ] Add multi-factor authentication
- [ ] Implement user management UI
- [ ] Add role-based permissions configuration UI

# Neon PostgreSQL Setup Guide

## Step 1: Create a Neon Account
1. Go to **https://neon.tech/**
2. Click **"Sign Up"** (top right)
3. Use your email or GitHub account
4. Verify your email

---

## Step 2: Create a Database Project

After signing in, you'll see the Neon Console dashboard:

1. Click **"New Project"** button
2. Fill in project details:
   - **Project Name**: `DataTalk-AI`
   - **Database Name**: `datatalk` (or your preference)
   - **Region**: Choose closest to you (e.g., `US East`, `EU West`)
   - **PostgreSQL Version**: Select `15` or `16`
3. Click **"Create Project"**

*Wait a few seconds for the project to be created*

---

## Step 3: Find Your Connection Details

### 📍 Location in Neon Console:
```
Dashboard → Your Project → Connection Details (top right corner)
```

### Your Screen Should Show:

```
Connection String
┌─────────────────────────────────────────────────────────────┐
│ postgresql://[user]:[password]@[host]/[database]?sslmode=require │
└─────────────────────────────────────────────────────────────┘

Hostname: [host]
Database: datatalk
User:     [user]
Password: [password]
Port:     5432
```

---

## Step 4: Extract and Format Connection Details

Your connection string looks like:
```
postgresql://neon_user:neon_password@ep-random-name.us-east-1.neon.tech/datatalk?sslmode=require
```

**Break it down:**

| Component | Example | Where Used |
|-----------|---------|-----------|
| **Host** | `ep-random-name.us-east-1.neon.tech` | Part of DB_URL |
| **Port** | `5432` | Part of DB_URL (usually 5432) |
| **Database** | `datatalk` | Part of DB_URL |
| **User** | `neon_user` | DB_USER |
| **Password** | `neon_password` | DB_PASSWORD |

---

## Step 5: Format for Spring Boot

Your `DB_URL` should be:
```
jdbc:postgresql://ep-random-name.us-east-1.neon.tech:5432/datatalk?sslmode=require
```

**Replace these values:**
- `ep-random-name.us-east-1.neon.tech` → Your actual hostname from Neon
- `datatalk` → Your database name
- Keep `sslmode=require` (required by Neon)

---

## Step 6: Set Environment Variables

### **Option A: Windows Command Prompt (Temporary)**
```batch
set DB_URL=jdbc:postgresql://your-host:5432/datatalk?sslmode=require
set DB_USER=your_neon_user
set DB_PASSWORD=your_neon_password
mvn spring-boot:run
```

### **Option B: Windows PowerShell (Temporary)**
```powershell
$env:DB_URL = "jdbc:postgresql://your-host:5432/datatalk?sslmode=require"
$env:DB_USER = "your_neon_user"
$env:DB_PASSWORD = "your_neon_password"
mvn spring-boot:run
```

### **Option C: Edit application.properties (Permanent)**
Edit `src/main/resources/application.properties`:

```properties
# PostgreSQL Neon Database Connection
spring.datasource.url=jdbc:postgresql://ep-random-name.us-east-1.neon.tech:5432/datatalk?sslmode=require
spring.datasource.username=your_neon_user
spring.datasource.password=your_neon_password
spring.datasource.driver-class-name=org.postgresql.Driver
```

### **Option D: .env file (Recommended for Development)**
Create `application-dev.properties` in `src/main/resources/`:

```properties
spring.datasource.url=jdbc:postgresql://your-host:5432/datatalk?sslmode=require
spring.datasource.username=your_user
spring.datasource.password=your_password
```

Then run with:
```bash
mvn spring-boot:run -Dspring.profiles.active=dev
```

---

## Step 7: Run Database Setup Scripts

### In Neon Console:

1. Click on your project
2. Click **"SQL Editor"** (left sidebar)
3. A text editor opens
4. Copy contents of `database/neon-schema.sql` and paste
5. Click **"Run"** button (or `Ctrl+Enter`)
6. Wait for success ✅

**Repeat for:**
- `database/sample-data.sql` (paste → run)
- `database/permissions.sql` (paste → run)

---

## Step 8: Verify Connection

Run this in your terminal:
```bash
mvn spring-boot:run
```

**Success Indicators:**
```
✅ Application started on port 8080
✅ Connected to database
✅ No connection errors in logs
```

**If you see connection errors:**
- Double-check hostname spelling
- Verify `sslmode=require` is in URL
- Confirm password doesn't have special characters (if yes, URL-encode them)
- Check username is correct (not the role, but the connection user)

---

## Neon Console - Key Sections

```
Left Sidebar Menu:
├── Dashboard
├── Databases
├── Roles              ← View connection users
├── SQL Editor         ← Run SQL scripts
├── Branches (advanced)
└── Settings

Connection String Section:
├── Hostname
├── Port
├── Database name
├── User/Role
└── Password (click to reveal)
```

---

## Common Issues

### ❌ "Connection refused"
- ✅ Verify hostname is copied correctly (should include `.neon.tech`)
- ✅ Check port is `5432`
- ✅ Confirm you copied the whole sslmode parameter

### ❌ "Authentication failed"
- ✅ Verify DB_USER matches Neon console exactly
- ✅ Verify DB_PASSWORD matches (case-sensitive!)
- ✅ If password has special chars: `%`, `@`, `#` → URL-encode them:
  - `%` → `%25`
  - `@` → `%40`
  - `#` → `%23`

### ❌ "SSL error / certificate error"
- ✅ Ensure `sslmode=require` is in the URL
- ✅ Neon requires SSL - this is not optional

### ❌ "Database 'datatalk' does not exist"
- ✅ Run `neon-schema.sql` first to create tables
- ✅ Verify database name in URL matches what you created

---

## Quick Reference

**Neon Console → Connection String → Copy the full string:**

```
postgresql://user:password@host/database?sslmode=require
```

**Convert to JDBC format for Spring Boot:**

```
jdbc:postgresql://host:5432/database?sslmode=require
```

---

## Next: Run the Application

Once environment variables are set:

```bash
# Build and run
mvn clean install
mvn spring-boot:run

# Application starts at
http://localhost:8080/datatalk/login

# Demo Login
Username: admin_user
Password: DemoPass@123
```

---

**You're all set! 🚀**

# Neon PostgreSQL Connection Setup - DataTalk AI

## ✅ Connection Details Configured

**Last Updated**: 2026-11-04

### Your Neon Account Details

| Detail | Value |
|--------|-------|
| **Application Name** | DataTalk AI |
| **Hostname** | `ep-lingering-brook-b3tgnze7.aws.neon.tech` |
| **Region** | Asia Pacific Southeast 1 (ap-southeast-1) |
| **Database** | `neondb` |
| **Port** | `5432` |
| **Username** | `user_datatalk` |
| **Password** | `npg_IdEYW0bR8heu` |
| **SSL Mode** | `require` (mandatory for Neon) |

---

## 📝 Spring Boot Configuration

### Updated in: `src/main/resources/application.properties`

```properties
# PostgreSQL Neon Database Connection
# Neon Host: ep-lingering-brook-b3tgnze7.aws.neon.tech (Asia Pacific Southeast 1)
spring.datasource.url=${DB_URL:jdbc:postgresql://ep-lingering-brook-b3tgnze7.aws.neon.tech:5432/neondb?sslmode=require}
spring.datasource.username=${DB_USER:user_datatalk}
spring.datasource.password=${DB_PASSWORD:npg_IdEYW0bR8heu}
spring.datasource.driver-class-name=org.postgresql.Driver
```

---

## 🔄 How to Use

### Option 1: Direct Run (Uses application.properties defaults)
```bash
mvn clean install
mvn spring-boot:run
```

### Option 2: Override with Environment Variables (Secure)

**PowerShell:**
```powershell
$env:DB_URL = "jdbc:postgresql://ep-lingering-brook-b3tgnze7.aws.neon.tech:5432/neondb?sslmode=require"
$env:DB_USER = "user_datatalk"
$env:DB_PASSWORD = "npg_IdEYW0bR8heu"
mvn spring-boot:run
```

**Command Prompt:**
```batch
set DB_URL=jdbc:postgresql://ep-lingering-brook-b3tgnze7.aws.neon.tech:5432/neondb?sslmode=require
set DB_USER=user_datatalk
set DB_PASSWORD=npg_IdEYW0bR8heu
mvn spring-boot:run
```

### Option 3: Create .env file (Production Safe)
Create `application-prod.properties`:
```properties
spring.datasource.url=jdbc:postgresql://ep-lingering-brook-b3tgnze7.aws.neon.tech:5432/neondb?sslmode=require
spring.datasource.username=user_datatalk
spring.datasource.password=npg_IdEYW0bR8heu
```

Run with:
```bash
mvn spring-boot:run -Dspring.profiles.active=prod
```

---

## 🗄️ Next Steps: Load Database Schema

### Step 1: Open Neon SQL Editor
1. Visit **https://console.neon.tech** (or your Neon project URL)
2. Click **"SQL Editor"** (left sidebar)
3. You should see database: `neondb`

### Step 2: Run Database Schema Script

Copy-paste the entire contents of:
```
database/neon-schema.sql
```

Into the SQL Editor and click **"Run"** (or press `Ctrl+Enter`)

**Expected output:** ✅ All tables created successfully

### Step 3: Load Sample Data

Copy-paste the entire contents of:
```
database/sample-data.sql
```

Into the SQL Editor and click **"Run"**

**Expected output:** ✅ 10,000+ records inserted

### Step 4: Set Permissions (Optional)

Copy-paste the entire contents of:
```
database/permissions.sql
```

Into the SQL Editor and click **"Run"**

**Expected output:** ✅ Read-only role created

---

## 🧪 Verify Connection

### Test 1: Run the Application
```bash
mvn spring-boot:run
```

**Expected log output:**
```
✅ HikariPool - Pool initialized with 5 connections
✅ Starting DataTalkAiApplication
✅ Tomcat started on port(s): 8080
✅ Started DataTalkAiApplication in X.XXX seconds
```

### Test 2: Login to Application
Open browser and navigate to:
```
http://localhost:8080/datatalk/login
```

**Use demo credentials:**
- Username: `admin_user`
- Password: `DemoPass@123`

**Expected result:**
- ✅ Login successful
- ✅ Redirected to dashboard
- ✅ User information displays
- ✅ Metrics show data from Neon database

### Test 3: Test API Endpoint
```bash
curl -X POST http://localhost:8080/datatalk/api/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin_user","password":"DemoPass@123"}'
```

**Expected response:**
```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "user_id": 1,
    "username": "admin_user",
    "full_name": "Admin User",
    "role": "ADMIN",
    "customer_id": 1
  }
}
```

---

## 🔑 Connection String Format Reference

Your connection string broken down:

```
jdbc:postgresql://ep-lingering-brook-b3tgnze7.aws.neon.tech:5432/neondb?sslmode=require
       └─ Protocol
                └─ Hostname (Neon endpoint)
                                                          └─ Port (always 5432)
                                                               └─ Database name
                                                                       └─ SSL required
```

---

## ⚠️ Important Notes

1. **SSL is Required**: Neon always requires `sslmode=require` - this is NOT optional
2. **Password Encoding**: If your password has special characters, they must be URL-encoded:
   - `@` → `%40`
   - `#` → `%23`
   - `%` → `%25`
   - Current password `npg_IdEYW0bR8heu` has no special characters ✅

3. **Security**: 
   - ✅ Credentials are in `application.properties` with environment variable fallback
   - ✅ Never commit sensitive credentials to Git
   - Use environment variables for production

4. **Region**: Your database is in **Asia Pacific Southeast 1 (AWS Singapore)**
   - Good latency for Southeast Asia users
   - Higher latency for US/EU users (consider migrating if needed)

---

## 🐛 Troubleshooting

### Error: "Connection refused"
```
❌ org.postgresql.util.PSQLException: Connection to ... refused
```
**Solution:**
- Verify hostname is spelled correctly: `ep-lingering-brook-b3tgnze7.aws.neon.tech`
- Verify port is `5432`
- Confirm database `neondb` exists

### Error: "Authentication failed - password authentication failed for user"
```
❌ FATAL: password authentication failed for user "user_datatalk"
```
**Solution:**
- Verify username: `user_datatalk` (case-sensitive)
- Verify password: `npg_IdEYW0bR8heu` (case-sensitive)
- Try resetting password in Neon Console if needed

### Error: "SSL connection error"
```
❌ FATAL: SSL required
```
**Solution:**
- Add `?sslmode=require` to the JDBC URL (already done ✅)
- Neon requires SSL - cannot be disabled

### Error: "Database 'neondb' does not exist"
```
❌ FATAL: database "neondb" does not exist
```
**Solution:**
- Run `database/neon-schema.sql` in Neon SQL Editor
- This creates all tables and initializes the database

### Error: "No tables found" / Empty database
**Solution:**
1. Open Neon SQL Editor
2. Copy-paste contents of `database/neon-schema.sql`
3. Click Run
4. Then repeat with `database/sample-data.sql`

---

## 📞 Quick Support

**Neon Console**: https://console.neon.tech
**Neon Docs**: https://neon.tech/docs

---

## Checklist

- [x] Neon account created
- [x] Database project created
- [x] Connection credentials obtained
- [x] application.properties updated
- [ ] Database schema scripts executed
- [ ] Sample data loaded
- [ ] Application started and tested
- [ ] Login page verified
- [ ] API endpoints tested

---

**Status**: Ready for database initialization and application deployment! 🚀

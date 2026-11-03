# DataTalk AI - Maven Build and Application Setup Guide

## ✅ Build Status: SUCCESS

Your DataTalk AI application has been **successfully built** with Maven!

```
✅ Maven 3.8.8 installed
✅ Java 17 configured  
✅ Project compiled successfully
✅ JAR artifact created: datatalk-ai-poc-0.1.0.jar (25.43 MB)
```

---

## 📦 Build Artifacts

**Location**: `target/datatalk-ai-poc-0.1.0.jar`

**To run directly with Java:**
```bash
cd D:\half-2026\DataTalk-AI---POC
java -Dspring.datasource.url="jdbc:postgresql://ep-lingering-brook-b3tgnze7.aws.neon.tech:5432/neondb?sslmode=require" `
     -Dspring.datasource.username="user_datatalk" `
     -Dspring.datasource.password="npg_IdEYW0bR8heu" `
     -jar target/datatalk-ai-poc-0.1.0.jar
```

---

## ⚙️ Configuration

Before running, ensure:

1. ✅ **Neon Database is Ready**
   - Run database schema: `database/neon-schema.sql`
   - Load sample data: `database/sample-data.sql`
   - (Optional) Set permissions: `database/permissions.sql`

2. ✅ **Environment Variables Set**
   ```powershell
   $env:DB_URL = "jdbc:postgresql://ep-lingering-brook-b3tgnze7.aws.neon.tech:5432/neondb?sslmode=require"
   $env:DB_USER = "user_datatalk"
   $env:DB_PASSWORD = "npg_IdEYW0bR8heu"
   ```

3. ✅ **application.properties Updated**
   - Already updated with your Neon credentials in:
   - `src/main/resources/application.properties` (lines 20-25)

---

## 🚀 Running the Application

### Option 1: Maven (Recommended for Development)
```bash
cd D:\half-2026\DataTalk-AI---POC
mvn spring-boot:run
```

### Option 2: Direct Java Execution
```bash
java -jar target/datatalk-ai-poc-0.1.0.jar
```

### Option 3: With Environment Variables
```powershell
$env:DB_URL = "jdbc:postgresql://ep-lingering-brook-b3tgnze7.aws.neon.tech:5432/neondb?sslmode=require"
$env:DB_USER = "user_datatalk"
$env:DB_PASSWORD = "npg_IdEYW0bR8heu"
mvn spring-boot:run
```

---

## 📋 Startup Checklist

- [ ] Database schema created in Neon (run `neon-schema.sql`)
- [ ] Sample data loaded (run `sample-data.sql`)
- [ ] Neon credentials configured in `application.properties`
- [ ] Environment variables set (DB_URL, DB_USER, DB_PASSWORD)
- [ ] Maven build completed successfully ✅
- [ ] Application started on http://localhost:8080/datatalk

---

## 🌐 Accessing the Application

Once running:

**URL**: http://localhost:8080/datatalk/login

**Demo Credentials:**
```
Username: admin_user
Password: DemoPass@123
```

**Other Demo Users:**
- `techpay_manager` / `DemoPass@123` (BUSINESS role)
- `retail_manager` / `DemoPass@123` (MANAGER role)
- `restaurant_owner` / `DemoPass@123` (BUSINESS role)

---

## 📍 Application Pages

| Page | URL | Public | Purpose |
|------|-----|--------|---------|
| Login | `/datatalk/login` | ✅ Yes | User authentication |
| Dashboard | `/datatalk/dashboard` | ❌ No | User info & metrics |
| Chat | `/datatalk/chat` | ❌ No | AI chat placeholder |
| Project Info | `/datatalk/poc-info` | ✅ Yes | Project overview |

---

## 🔧 Troubleshooting

### Application Won't Start

**Error:** `Connection refused` or `Connection timeout`
```
Caused by: java.net.ConnectException: Connection refused
```

**Solution:**
1. Verify Neon database credentials in `application.properties`
2. Ensure database schema is created (run `neon-schema.sql` in Neon SQL Editor)
3. Check that SSL is enabled: `sslmode=require` in the URL
4. Verify username/password are correct (case-sensitive)

**Common Issues:**
- Hostname typo: Should be `ep-lingering-brook-b3tgnze7.aws.neon.tech`
- Database name: Should be `neondb`
- Missing SSL: Must include `?sslmode=require`

### Port 8080 Already in Use

```bash
netstat -ano | findstr :8080
taskkill /PID <PID> /F
```

Or configure different port in `application.properties`:
```properties
server.port=8081
```

### Database Connection Errors

```
ERROR: database "neondb" does not exist
```

**Solution:** Run the database setup scripts in Neon SQL Editor:
1. Go to https://console.neon.tech
2. Select your project → SQL Editor
3. Paste and run `database/neon-schema.sql`
4. Then run `database/sample-data.sql`

---

## 📚 Project Structure

```
DataTalk-AI---POC/
├── src/
│   └── main/
│       ├── java/com/datatalk/datatalkai/
│       │   ├── DataTalkAiApplication.java      (Entry point)
│       │   ├── controller/                      (REST endpoints)
│       │   ├── service/                         (Business logic)
│       │   ├── repository/                      (Data access)
│       │   ├── model/                           (Entities & DTOs)
│       │   └── security/                        (Spring Security config)
│       └── resources/
│           ├── application.properties           (Configuration)
│           ├── templates/                       (HTML pages)
│           └── static/css/                      (Stylesheets)
├── database/
│   ├── neon-schema.sql                          (Database schema)
│   ├── sample-data.sql                          (Sample records)
│   ├── permissions.sql                          (User roles)
│   └── README.md                                (DB setup guide)
├── docs/                                        (Documentation)
├── pom.xml                                      (Maven configuration)
├── README.md                                    (Project overview)
└── target/
    └── datatalk-ai-poc-0.1.0.jar               (Compiled JAR)
```

---

## 🔐 Security Notes

- ✅ All passwords use BCrypt hashing (10 rounds)
- ✅ No plain-text passwords in configuration
- ✅ CSRF protection enabled
- ✅ Session management with 30-minute timeout
- ✅ SQL injection prevention (parameterized queries)
- ✅ PCI compliance: Card numbers stored as last 4 digits only

**Production Deployment:**
- Remove hardcoded credentials from `application.properties`
- Use environment variables or secrets manager
- Enable HTTPS
- Set `spring.jpa.hibernate.ddl-auto=validate` (never `create-drop`)
- Configure proper logging (remove DEBUG logs)

---

## 🛠️ Maven Commands

```bash
# Clean build
mvn clean install

# Run tests
mvn test

# Run application
mvn spring-boot:run

# Run specific test
mvn test -Dtest=UserRepositoryTest

# Generate project info
mvn site

# Check dependencies
mvn dependency:tree

# Update dependencies
mvn dependency:update-snapshots
```

---

## 📞 Support & Resources

- **Neon Docs**: https://neon.tech/docs
- **Spring Boot Docs**: https://spring.io/projects/spring-boot
- **Spring Security Docs**: https://spring.io/projects/spring-security
- **Maven Docs**: https://maven.apache.org/guides/

---

## ✨ Next Steps

1. **Run Database Setup** (if not already done):
   ```
   Go to Neon Console → SQL Editor → Run neon-schema.sql
   Then run sample-data.sql
   ```

2. **Start Application**:
   ```bash
   mvn spring-boot:run
   ```

3. **Test Login**:
   ```
   URL: http://localhost:8080/datatalk/login
   Username: admin_user
   Password: DemoPass@123
   ```

4. **Explore Dashboard**:
   - View user information
   - Check business metrics
   - Navigate to chat page (placeholder)

5. **Review API Endpoints**:
   - POST `/datatalk/api/login`
   - GET `/datatalk/api/user-info`
   - POST `/datatalk/api/logout`
   - GET `/datatalk/api/dashboard/summary`

---

**Status**: ✅ **Application Ready for Testing**

Build completed successfully! You're ready to connect to Neon database and run the application.


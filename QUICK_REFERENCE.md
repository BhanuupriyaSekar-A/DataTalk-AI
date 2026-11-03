# 🎯 DataTalk AI - Quick Reference Card

## ⚡ Quick Commands

### Build Project
```bash
cd D:\half-2026\DataTalk-AI---POC
mvn clean install
```

### Run Application
```bash
mvn spring-boot:run
```

### Run Application (Direct JAR)
```bash
java -jar target/datatalk-ai-poc-0.1.0.jar
```

---

## 🌐 Access Points

| Purpose | URL |
|---------|-----|
| **Login Page** | http://localhost:8080/datatalk/login |
| **Dashboard** | http://localhost:8080/datatalk/dashboard |
| **Chat** | http://localhost:8080/datatalk/chat |
| **Project Info** | http://localhost:8080/datatalk/poc-info |

---

## 👤 Demo Credentials

**All passwords**: `DemoPass@123`

| User | Role | Purpose |
|------|------|---------|
| `admin_user` | ADMIN | Full access |
| `techpay_manager` | BUSINESS | Business owner |
| `retail_manager` | MANAGER | Manager role |

---

## 🗄️ Database Setup

### 1. Neon Console (https://console.neon.tech)
- Select project → SQL Editor

### 2. Run Scripts (in order)
```
1. Copy database/neon-schema.sql → Run
2. Copy database/sample-data.sql → Run
3. (Optional) Copy database/permissions.sql → Run
```

---

## 🔌 Database Credentials

```
Host:     ep-lingering-brook-b3tgnze7.aws.neon.tech
Port:     5432
Database: neondb
User:     user_datatalk
Password: npg_IdEYW0bR8heu
SSL:      Required (sslmode=require)
```

---

## 📁 Key Files

| File | Purpose |
|------|---------|
| `pom.xml` | Maven configuration |
| `src/main/resources/application.properties` | Application config |
| `database/neon-schema.sql` | Database schema |
| `database/sample-data.sql` | Sample records |
| `src/main/java/com/datatalk/datatalkai/` | Source code |
| `src/main/resources/templates/` | HTML pages |
| `DEPLOYMENT_GUIDE.md` | **→ Start here** |

---

## 🧪 Quick Test

### Test Login API
```bash
curl -X POST http://localhost:8080/datatalk/api/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin_user","password":"DemoPass@123"}'
```

### Expected Response
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

## 🔧 Troubleshooting

| Issue | Solution |
|-------|----------|
| **Port 8080 in use** | `netstat -ano \| findstr :8080` then kill the PID |
| **Connection refused** | Check Neon credentials in `application.properties` |
| **Database not found** | Run `neon-schema.sql` in Neon SQL Editor |
| **Build fails** | Ensure Java 17+ and Maven 3.8+ installed |

---

## 📊 Project Stats

- **Java Classes**: 14
- **REST Endpoints**: 4
- **HTML Pages**: 4
- **Database Tables**: 10+
- **Database Schemas**: 6
- **Sample Records**: 10,000+
- **JAR Size**: 25.43 MB
- **Dependencies**: 63

---

## ✅ Before Deployment

- [ ] Java 17 installed: `java -version`
- [ ] Maven 3.8+ installed: `mvn -version`
- [ ] Neon account created
- [ ] Database schema created
- [ ] Sample data loaded
- [ ] `application.properties` has credentials
- [ ] Build successful: `mvn clean install`

---

## 🚀 Deployment Checklist

- [ ] Database initialized in Neon
- [ ] Application starts: `mvn spring-boot:run`
- [ ] Login works: http://localhost:8080/datatalk/login
- [ ] Dashboard displays
- [ ] APIs respond correctly
- [ ] Security headers present

---

## 📚 Documentation

**Start Here:**
- **DEPLOYMENT_GUIDE.md** - Complete setup instructions

**Reference:**
- MAVEN_BUILD_SUCCESS.md
- NEON_CONNECTION_SETUP.md
- docs/NEON_SETUP_GUIDE.md
- README.md

---

## 🎯 Next Steps

1. **Initialize Database** (5 min)
   - Run SQL scripts in Neon

2. **Start Application** (30 sec)
   - `mvn spring-boot:run`

3. **Test Login** (2 min)
   - Go to login page, test credentials

4. **Verify Features** (5 min)
   - Check dashboard, APIs, UI

---

## 📞 Support

- **Build Issues**: See MAVEN_BUILD_SUCCESS.md
- **Database Issues**: See NEON_CONNECTION_SETUP.md
- **Deployment Issues**: See DEPLOYMENT_GUIDE.md
- **General Questions**: See README.md

---

**Status**: ✅ **READY FOR DEPLOYMENT**


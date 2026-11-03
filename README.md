# DataTalk AI - Natural Language Business Data Analytics POC

A revolutionary platform that enables business users to ask questions about their data in natural language and receive instant insights.

**Status**: Phase 1 Complete ✅  
**Version**: 0.1.0  
**Last Updated**: November 4, 2026

---

## Quick Start

### Prerequisites
- ✅ Java 17+ (verified: OpenJDK 17.0.20.1)
- ✅ Maven 3.8+ (verified: Apache Maven 3.8.8) 
- ✅ Neon PostgreSQL account (created)
- ✅ Build successful: JAR created (25.43 MB)

### 1. Database Setup

#### Step 1: Create Neon PostgreSQL Account
- Go to https://console.neon.tech
- Create a new project
- Get your connection string

#### Step 2: Run Database Scripts
```bash
# In Neon SQL Editor, run these in order:
1. Copy contents of database/neon-schema.sql and execute
2. Copy contents of database/sample-data.sql and execute
3. Copy contents of database/permissions.sql and execute
```

See **database/README.md** for detailed instructions.

### 2. Configure Application

Update `src/main/resources/application.properties`:
```properties
spring.datasource.url=jdbc:postgresql://your-host/datatalk
spring.datasource.username=your_user
spring.datasource.password=your_password
```

Or use environment variables:
```bash
export DB_URL=jdbc:postgresql://host/database
export DB_USER=username
export DB_PASSWORD=password
```

### 3. Build & Run

```bash
# Build the project
mvn clean install

# Run the application
mvn spring-boot:run

# Or build JAR and run
mvn clean package
java -jar target/datatalk-ai-poc-0.1.0.jar
```

### 4. Access the Application

- **URL**: http://localhost:8080/datatalk/login
- **Demo Username**: `admin_user`
- **Demo Password**: `DemoPass@123`

---

## Demo Users

All demo users use password: `DemoPass@123`

| Username | Role | Customer | Email |
|----------|------|----------|-------|
| admin_user | ADMIN | - | admin@datatalk.com |
| techpay_manager | BUSINESS | TechPay Solutions | john.manager@techpay.com |
| techpay_customer | CUSTOMER | TechPay Solutions | sarah@techalk.com |
| retail_manager | MANAGER | RetailHub Inc | mike@retailhub.com |
| restaurant_owner | BUSINESS | Restaurant Group USA | david@restgrp.com |
| gas_operator | CUSTOMER | Gas Station Network | emma@gasnet.com |

---

## Features - Phase 1

### ✅ Authentication & Security
- Secure login/logout with BCrypt password hashing
- Session management with timeout
- Role-based access control (ADMIN, BUSINESS, MANAGER, CUSTOMER)
- Login attempt tracking and audit logging
- CSRF protection
- No passwords displayed or logged

### ✅ Database
- PostgreSQL with 6 schemas
- 20+ tables with proper relationships
- 10,000+ sample records
- Comprehensive indexing
- Views for common queries
- Read-only reporting role

### ✅ Dashboard
- User information display
- Business metrics summary
- Device statistics
- Transaction analytics
- Customer information
- Role-based summaries

### ✅ User Interface
- Clean, modern Bootstrap 5 design
- Responsive design (mobile-friendly)
- Login page
- Dashboard page
- AI Chat page (placeholder)
- Project info page
- Navigation bar with user menu

### ✅ APIs
- REST endpoints for all operations
- JSON request/response format
- Proper error handling
- API documentation in code

---

## Technology Stack

| Layer | Technology |
|-------|------------|
| **Runtime** | Java 17 |
| **Framework** | Spring Boot 3.2.0 |
| **Security** | Spring Security, BCrypt |
| **Database** | PostgreSQL, Spring JDBC |
| **Frontend** | HTML5, CSS3, JavaScript |
| **UI Framework** | Bootstrap 5 |
| **Build** | Maven |
| **Connection Pool** | HikariCP |

---

## Project Structure

```
datatalk-ai-poc/
├── database/
│   ├── neon-schema.sql          # Database schema definition
│   ├── sample-data.sql          # Sample data insertion
│   ├── permissions.sql          # User roles and permissions
│   └── README.md                # Database setup guide
├── docs/
│   ├── PROJECT_OVERVIEW.md      # Project vision and architecture
│   ├── DATABASE_DESIGN.md       # Detailed schema documentation
│   ├── POC_PROGRESS.md          # Progress tracking
│   └── NEXT_STEPS.md            # Roadmap and future plans
├── src/
│   ├── main/
│   │   ├── java/com/datatalk/datatalkai/
│   │   │   ├── controller/      # REST controllers
│   │   │   ├── service/         # Business logic
│   │   │   ├── repository/      # Data access layer
│   │   │   ├── model/           # Entity classes
│   │   │   ├── security/        # Security config
│   │   │   └── DataTalkAiApplication.java
│   │   └── resources/
│   │       ├── templates/       # HTML pages
│   │       ├── static/css/      # Stylesheets
│   │       └── application.properties
│   └── test/
│       └── java/...             # Unit tests
├── pom.xml                       # Maven configuration
├── README.md                     # This file
└── .gitignore

```

---

## API Endpoints

### Authentication
```
POST /datatalk/api/login
  Request: { "username": "...", "password": "..." }
  Response: { "success": true, "data": { user info } }

GET /datatalk/api/user-info
  Response: { "success": true, "data": { user info } }

POST /datatalk/api/logout
  Response: { "success": true, "message": "Logout successful" }
```

### Dashboard
```
GET /datatalk/api/dashboard/summary
  Response: { "success": true, "data": { metrics } }
```

### Pages
```
GET /datatalk/login           # Login page
GET /datatalk/dashboard       # Dashboard page
GET /datatalk/chat            # Chat page
GET /datatalk/poc-info        # Project info page
```

---

## Database Structure

### Schemas
1. **corp** - Customer master data
2. **location** - Locations and branches
3. **app_user** - User authentication
4. **device** - Payment device management
5. **txn** - Payment transactions
6. **app** - Application data

### Key Tables
- `corp.customer` - 5 sample customers
- `location.location` - 15 locations
- `app_user.app_user` - 6 demo users
- `device.device` - 60 devices
- `txn.payment_transaction` - 10,000 transactions
- `device.device_status_log` - 1,500 events
- `app_user.user_login_history` - 800 login records
- `app.chat_history` - Chat/query history (for Phase 2)

See **docs/DATABASE_DESIGN.md** for detailed schema documentation.

---

## Security Considerations

### Passwords
- All passwords hashed with BCrypt (10 rounds)
- Never stored or displayed as plain text
- Demo password provided separately for testing
- Password reset workflow (not yet implemented)

### Data Protection
- Card numbers stored as last 4 digits only (PCI compliant)
- Row-level security: Users see only their customer's data
- SQL injection prevention via parameterized queries
- XSS protection via HTML escaping
- CSRF protection on all forms

### Audit Trail
- All login attempts logged
- User ID tracked with every operation
- IP address captured for logins
- Timestamps on all transactions
- Failed login attempts recorded with reason

---

## Configuration

### Application Properties
```properties
# Database
spring.datasource.url=...
spring.datasource.username=...
spring.datasource.password=...

# Connection Pool
spring.datasource.hikari.maximum-pool-size=20

# Session
server.servlet.session.timeout=30m

# Security
server.servlet.session.cookie.http-only=true
```

### Environment Variables
```bash
DB_URL=jdbc:postgresql://host/database
DB_USER=username
DB_PASSWORD=password
```

---

## Running Tests

### Unit Tests
```bash
mvn test
```

### Specific Test Class
```bash
mvn test -Dtest=AuthenticationServiceTest
```

### With Coverage
```bash
mvn clean test jacoco:report
```

---

## Logging

Logs are written to: `logs/datatalk-ai.log`

### Configuration
- Level: DEBUG for application code, INFO for framework
- Format: Timestamp, logger, message
- Rotation: 10MB per file, 10 files max
- Console output for development

### View Logs
```bash
tail -f logs/datatalk-ai.log
```

---

## Troubleshooting

### Database Connection Error
```
Error: Unable to connect to database
Solution: 
1. Verify connection string in application.properties
2. Check PostgreSQL/Neon is accessible
3. Verify firewall/security groups allow connection
4. Ensure database user has correct permissions
```

### Login Failed
```
Error: Invalid username or password
Solution:
1. Check username/password spelling
2. Ensure user exists in database
3. Check user status is ACTIVE
4. Review login_history table for failures
```

### Port Already in Use
```
Error: Port 8080 is already in use
Solution:
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8081"
```

### Data Not Showing on Dashboard
```
Error: No data displayed
Solution:
1. Verify sample-data.sql was executed
2. Check user has associated customer_id
3. Verify data exists in database tables
4. Check application logs for errors
```

---

## Performance Optimization

### Database
- Connection pooling enabled (HikariCP)
- Statement timeout: 30 seconds
- Index on all frequently searched columns
- View results from queries are pre-aggregated

### Application
- Minimal dependencies
- No heavy frameworks
- Async API calls on frontend
- Response caching where applicable

### Frontend
- Bootstrap CDN for fast loading
- Inline CSS (no external dependencies)
- Vanilla JavaScript (no frameworks)
- Responsive design

---

## Deployment

### Development
```bash
mvn spring-boot:run
# Or IDE run configuration
```

### Production
```bash
# Build JAR
mvn clean package

# Run with production settings
java -Dspring.profiles.active=prod \
     -Dspring.datasource.url=$DB_URL \
     -Dspring.datasource.username=$DB_USER \
     -Dspring.datasource.password=$DB_PASSWORD \
     -jar target/datatalk-ai-poc-0.1.0.jar
```

### Docker (Optional)
```dockerfile
FROM openjdk:17-slim
COPY target/datatalk-ai-poc-0.1.0.jar app.jar
ENTRYPOINT ["java","-jar","app.jar"]
```

---

## Monitoring & Maintenance

### Health Check
```
GET /datatalk/health
# Should return: UP
```

### Database Maintenance
```sql
-- Analyze query plans
ANALYZE;

-- Vacuum and analyze
VACUUM ANALYZE;

-- Check table sizes
SELECT schemaname, tablename, pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) 
FROM pg_tables 
ORDER BY pg_total_relation_size DESC;
```

---

## Contributing

### Code Style
- Java: Google Java Style Guide
- Comments: Clear, concise, no obvious comments
- Naming: Meaningful, consistent with project
- Methods: Focused, single responsibility

### Adding Features
1. Create feature branch
2. Write tests first
3. Implement functionality
4. Verify all tests pass
5. Update documentation
6. Submit pull request

---

## Documentation

### Available Docs
- **README.md** - This file, quick start guide
- **docs/PROJECT_OVERVIEW.md** - Project vision and architecture
- **docs/DATABASE_DESIGN.md** - Complete schema documentation
- **docs/POC_PROGRESS.md** - Development progress tracking
- **docs/NEXT_STEPS.md** - Roadmap and future plans
- **database/README.md** - Database setup instructions
- **Inline code comments** - Class and method documentation

### Updating Documentation
- Update docs/ when adding features
- Add comments to complex logic
- Keep README in sync with code
- Document breaking changes

---

## Frequently Asked Questions

**Q: Can I change the demo password?**  
A: Yes, update the password hash in sample-data.sql before loading. Use any BCrypt generator to create hashes.

**Q: What if I forget a user password?**  
A: Currently passwords are hashed and can't be retrieved. Reset password functionality planned for Phase 2.

**Q: Can I add more demo data?**  
A: Yes, write SQL INSERT statements following the existing pattern and execute in Neon.

**Q: How do I backup my database?**  
A: Neon handles backups automatically. For local PostgreSQL, use `pg_dump`.

**Q: Is this production-ready?**  
A: This is a POC (Phase 1). Use caution with real data. Production deployment requires security audit and testing.

---

## Support & Issues

### Reporting Issues
1. Check documentation first
2. Review error logs in `logs/datatalk-ai.log`
3. Create GitHub issue with:
   - Error message
   - Steps to reproduce
   - Environment details
   - Screenshots/logs

### Getting Help
- Check docs/ directory
- Review database/README.md
- Read inline code comments
- Check GitHub issues

---

## Roadmap

### Phase 1 (Complete ✅)
- Database design and setup
- User authentication
- Basic dashboard
- REST APIs
- UI pages

### Phase 2 (Next 🔄)
- AI/LLM integration
- Natural language processing
- SQL query generation
- Full chat interface
- Results visualization

### Phase 3 (Future ⏳)
- Advanced analytics
- Report generation
- Data export
- Mobile app
- Enterprise features

See **docs/NEXT_STEPS.md** for detailed roadmap.

---

## License

This project is provided as-is for evaluation and development purposes.

---

## Version History

| Version | Date | Status | Notes |
|---------|------|--------|-------|
| 0.1.0 | Nov 4, 2026 | ✅ Complete | Phase 1 - Foundation |
| 0.2.0 | (TBD) | 🔄 Planned | Phase 2 - AI Integration |
| 1.0.0 | (TBD) | ⏳ Future | Production Release |

---

## Quick Links

- **Project Info**: http://localhost:8080/datatalk/poc-info
- **Login**: http://localhost:8080/datatalk/login
- **Dashboard**: http://localhost:8080/datatalk/dashboard
- **Database Docs**: database/README.md
- **Roadmap**: docs/NEXT_STEPS.md
- **Progress**: docs/POC_PROGRESS.md

---

## Contact & Support

**Project Status**: Active Development  
**Last Updated**: November 4, 2026  
**Maintainers**: Development Team

---

**Thank you for using DataTalk AI!**

This is a revolutionary approach to business intelligence. Let us know your thoughts and suggestions.

Happy Coding! 🚀

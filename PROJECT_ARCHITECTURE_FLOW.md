# DataTalk AI - Project Architecture & Flow Diagram

## Overview
DataTalk AI is a proof-of-concept natural-language reporting system. Users log in, ask business questions in plain English, and get database-backed reports. An AI model generates PostgreSQL SQL, the backend validates it, applies security restrictions, and runs it read-only. Results display as tables and charts, exportable to PDF or Excel.

**Status:** POC Complete ✅ (Phase 1: Foundation, Phase 2: AI Reporting, Phase 3: Export Jobs)

---

## High-Level Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                            USER BROWSER / FRONTEND                                   │
│                          (Thymeleaf + Bootstrap 5 + Chart.js)                        │
└─────────────────────────────────────┬───────────────────────────────────────────────┘
                                      │ HTTP Requests
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                          SPRING BOOT CONTROLLER LAYER                               │
│  ┌──────────────┬──────────────┬──────────────┬──────────────────────────────────┐  │
│  │LoginController  │ ChatController  │ ReportController  │ AnalyticsController │  │
│  │(auth, session)  │(AI questions)  │ (predefined)      │ (admin stats)       │  │
│  └──────────────┴──────────────┴──────────────┴──────────────────────────────────┘  │
└─────────────────────────────────────┬───────────────────────────────────────────────┘
                                      │
        ┌─────────────────────────────┼─────────────────────────────────┐
        │                             │                                 │
        ▼                             ▼                                 ▼
┌──────────────────┐    ┌──────────────────────┐    ┌──────────────────────┐
│ SECURITY LAYER   │    │  SERVICE LAYER       │    │  EXPORT SERVICE      │
├──────────────────┤    ├──────────────────────┤    ├──────────────────────┤
│ - Authentication │    │ - ChatService        │    │ - PDF Generation     │
│ - Authorization  │    │ - ReportService      │    │ - Excel Export       │
│ - Role checking  │    │ - UserService        │    │ - Background Jobs    │
│ - Data isolation │    │ - ExportService      │    │ - Job Persistence    │
└──────────────────┘    │ - AnalyticsService   │    └──────────────────────┘
                        └──────────┬───────────┘
                                   │
        ┌──────────────────────────┼──────────────────────────────┐
        │                          │                              │
        ▼                          ▼                              ▼
┌──────────────────┐    ┌──────────────────────┐    ┌──────────────────────┐
│ AI LAYER         │    │ CHAT / REPORTING     │    │ PREDEFINED REPORTS   │
├──────────────────┤    ├──────────────────────┤    ├──────────────────────┤
│ - AI Service     │◄───│ - ChatRepository     │    │ - Report Definitions │
│ - Provider Mgmt  │    │ - ChatHistory        │    │ - SQL Queries        │
│ (Groq/Gemini/...)   │ - Context Caching    │    │ - Report Metadata    │
│ - Fallback Logic │    │ - Question Tracking  │    │ - ReportService      │
└──────────────────┘    └──────────────────────┘    └──────────────────────┘
        │                          │
        │  SQL Generation          │  Predefined SQL
        │  & Validation            │
        ▼                          ▼
┌──────────────────┐    ┌──────────────────────┐
│ SQL VALIDATION   │    │ REPOSITORY LAYER     │
├──────────────────┤    ├──────────────────────┤
│ - JSqlParser     │───►│ - UserRepository     │
│ - Security Check │    │ - DeviceRepository   │
│ - Read-only Mode │    │ - TransactionRepository
│ - 30s Timeout    │    │ - CustomerRepository │
└──────────────────┘    │ - Analytics...       │
        │               └──────────────────────┘
        │                          │
        └──────────────┬───────────┘
                       │ JDBC + Connection Pooling
                       ▼
        ┌──────────────────────────────────────┐
        │  NEON POSTGRESQL DATABASE            │
        ├──────────────────────────────────────┤
        │ - Core Schema:                       │
        │   • app_user (login, roles)          │
        │   • customer, device, payment        │
        │   • transaction, settlement          │
        │ - Chat History Schema:               │
        │   • chat_conversation                │
        │   • chat_message                     │
        │ - Export Jobs:                       │
        │   • app.export_job (Phase 3)         │
        │ - Analytics Tables:                  │
        │   • Query logs & metrics             │
        └──────────────────────────────────────┘
```

---

## Key Data Flows

### 1. Login Flow
```
User Login 
  → LoginController 
  → AuthenticationService 
  → UserRepository 
  → PostgreSQL 
  → BCrypt Validation 
  → Session Created
  → Redirect to Dashboard
```

**Components Involved:**
- `LoginController` - Handles login requests
- `AuthenticationService` - Validates credentials
- `UserRepository` - Queries app_user table
- Spring Security - Session management
- BCrypt - Password hashing/validation

---

### 2. AI Chat Flow (Main Feature)
```
User Question 
  → ChatController 
  → ChatService (retrieves context & history)
  → AI Service (Groq/Gemini/NVIDIA with fallback)
  → SQL Generation
  → JSqlParser Validation (read-only check)
  → Security Filter (role & customer isolation)
  → Repository Layer
  → PostgreSQL (30s timeout)
  → Results formatted
  → Chart.js visualization
  → Response to user
```

**Components Involved:**
- `ChatController` - Receives user questions
- `ChatService` - Manages chat state and context
- `AIService` - Calls AI providers with fallback logic
- `ChatRepository` - Stores conversation history
- `JSqlValidator` - Validates SQL safety
- Multiple `*Repository` classes - Execute validated queries
- Export services for results

**Features:**
- Multi-provider support (Groq, Gemini, NVIDIA)
- Automatic fallback on timeout/error
- 30-second query timeout
- Role-based data filtering
- Customer-level data isolation
- Follow-up question support with context caching

---

### 3. Predefined Reports Flow
```
User Selects Report 
  → ReportController 
  → ReportService (loads predefined SQL from config)
  → Security Filter (role & customer checks)
  → Repository Layer
  → PostgreSQL
  → Results cached
  → Display in table format
  → Export options available
```

**Components Involved:**
- `ReportController` - Lists & executes reports
- `ReportService` - Loads 11 predefined reports
- Report metadata loader
- Security filters
- Cache layer

**Available Reports:**
- 11 predefined SQL reports
- Payment analytics
- Device management
- Settlement tracking
- Customer reports

---

### 4. Export Flow (Background Jobs)
```
User Click Export (PDF/Excel)
  → ExportController
  → ExportService (creates background job)
  → ExportJobRepository
  → Background task queue
  → PDF/Excel generation (Apache POI / iText)
  → Save to disk
  → Job status stored in app.export_job
  → User notified
  → User downloads from export folder
```

**Components Involved:**
- `ExportService` - Manages export jobs
- `ExportJobRepository` - Persists job metadata
- PDF generator (Flying Saucer)
- Excel generator (Apache POI)
- File storage system
- Background job scheduler

**Features:**
- Large exports run asynchronously
- Job persistence (Phase 3)
- Progress tracking
- Configurable export directory

---

### 5. Analytics Flow (Admin Only)
```
Admin navigates to /datatalk/analytics
  → AnalyticsController
  → AnalyticsService
  → Query logs table
  → Aggregate metrics
  → PostgreSQL
  → Stats calculated (popularity, providers, timing)
  → Charts & metrics displayed
```

**Components Involved:**
- `AnalyticsController` - Admin analytics endpoint
- `AnalyticsService` - Calculates metrics
- Query logs storage
- Analytics repository
- Chart.js visualization

**Metrics Tracked:**
- Question count & success rate
- Popular reports
- AI provider usage
- Query timing
- Failure reasons

---

## Project Structure

```
DataTalk-AI---POC/
├── src/
│   ├── main/
│   │   ├── java/com/datatalk/datatalkai/
│   │   │   ├── ai/                          # AI model integration
│   │   │   │   ├── AIService.java
│   │   │   │   ├── AIProviderFactory.java
│   │   │   │   ├── GroqAIProvider.java
│   │   │   │   ├── GeminiAIProvider.java
│   │   │   │   └── NvidiaAIProvider.java
│   │   │   │
│   │   │   ├── chat/                        # Chat features
│   │   │   │   ├── ChatService.java
│   │   │   │   ├── ChatRepository.java
│   │   │   │   └── ChatController.java
│   │   │   │
│   │   │   ├── reporting/                   # AI-generated SQL reporting
│   │   │   │   ├── ReportingService.java
│   │   │   │   ├── JSqlValidator.java
│   │   │   │   └── SecurityFilter.java
│   │   │   │
│   │   │   ├── reports/                     # Predefined reports
│   │   │   │   ├── ReportService.java
│   │   │   │   ├── ReportRepository.java
│   │   │   │   └── ReportController.java
│   │   │   │
│   │   │   ├── export/                      # PDF/Excel export
│   │   │   │   ├── ExportService.java
│   │   │   │   ├── ExportJobRepository.java
│   │   │   │   ├── PdfExporter.java
│   │   │   │   └── ExcelExporter.java
│   │   │   │
│   │   │   ├── analytics/                   # Query analytics (admin)
│   │   │   │   ├── AnalyticsService.java
│   │   │   │   └── AnalyticsController.java
│   │   │   │
│   │   │   ├── controller/                  # HTTP controllers
│   │   │   │   ├── LoginController.java
│   │   │   │   ├── ChatController.java
│   │   │   │   ├── ReportController.java
│   │   │   │   ├── ExportController.java
│   │   │   │   └── AnalyticsController.java
│   │   │   │
│   │   │   ├── service/                     # Business logic
│   │   │   │   ├── UserService.java
│   │   │   │   ├── ChatService.java
│   │   │   │   ├── ReportService.java
│   │   │   │   ├── ExportService.java
│   │   │   │   └── AnalyticsService.java
│   │   │   │
│   │   │   ├── repository/                  # Data access
│   │   │   │   ├── UserRepository.java
│   │   │   │   ├── ChatRepository.java
│   │   │   │   ├── DeviceRepository.java
│   │   │   │   ├── TransactionRepository.java
│   │   │   │   ├── CustomerRepository.java
│   │   │   │   ├── ExportJobRepository.java
│   │   │   │   └── AnalyticsRepository.java
│   │   │   │
│   │   │   ├── model/                       # Data models & DTOs
│   │   │   │   ├── User.java
│   │   │   │   ├── ChatConversation.java
│   │   │   │   ├── ChatMessage.java
│   │   │   │   ├── Device.java
│   │   │   │   ├── Transaction.java
│   │   │   │   └── dto/
│   │   │   │       ├── ChatRequestDTO.java
│   │   │   │       ├── ChatResponseDTO.java
│   │   │   │       └── ExportJobDTO.java
│   │   │   │
│   │   │   ├── security/                    # Spring Security config
│   │   │   │   ├── SecurityConfig.java
│   │   │   │   ├── UserDetailsService.java
│   │   │   │   ├── JwtTokenProvider.java
│   │   │   │   └── RoleAuthority.java
│   │   │   │
│   │   │   ├── config/                      # Application config
│   │   │   │   ├── AppConfig.java
│   │   │   │   ├── DatabaseConfig.java
│   │   │   │   └── ExportConfig.java
│   │   │   │
│   │   │   ├── util/                        # Utilities
│   │   │   │   ├── SqlValidator.java
│   │   │   │   ├── QueryTimeout.java
│   │   │   │   └── SecurityUtils.java
│   │   │   │
│   │   │   └── DataTalkAiApplication.java   # Main entry point
│   │   │
│   │   └── resources/
│   │       ├── application.properties       # Config defaults
│   │       ├── templates/                   # Thymeleaf HTML templates
│   │       │   ├── login.html
│   │       │   ├── chat.html
│   │       │   ├── reports.html
│   │       │   ├── analytics.html
│   │       │   └── poc-info.html
│   │       ├── static/                      # CSS, JS, images
│   │       │   ├── css/
│   │       │   ├── js/
│   │       │   └── images/
│   │       └── predefined-reports/          # SQL report definitions
│   │           ├── report-1.sql
│   │           ├── report-2.sql
│   │           └── ...
│   │
│   └── test/
│       └── java/com/datatalk/datatalkai/
│           ├── LoginAndAccessIntegrationTest.java
│           ├── ChatServiceTest.java
│           ├── AIProviderTest.java
│           └── ExportServiceTest.java
│
├── database/                                # SQL scripts
│   ├── neon-schema.sql                     # Core schema
│   ├── sample-data.sql                     # Test data
│   ├── permissions.sql                     # Role permissions
│   ├── phase2-migration.sql                # Phase 2 additions
│   ├── phase3-migration.sql                # Export jobs table
│   ├── verify-demo-users.sql               # Demo user verification
│   └── README.md
│
├── docs/                                   # Documentation
│   ├── POC_IMPLEMENTATION_DETAILS.md
│   ├── PROJECT_STRUCTURE_AND_FILE_DETAILS.md
│   ├── DATABASE_DESIGN.md
│   ├── PREDEFINED_REPORTS_REFERENCE.md
│   ├── DEMO_USERS_GUIDE.md
│   ├── POC_PROGRESS.md
│   ├── NEXT_STEPS.md
│   └── DATATALK_AI_BUSINESS_AND_TECHNICAL_REQUIREMENTS.md
│
├── logs/                                   # Application logs
│   └── datatalk-ai.log
│
├── pom.xml                                 # Maven configuration
├── .env                                    # Environment variables (git-ignored)
├── .gitignore
├── .idea/                                  # IntelliJ config
└── README.md
```

---

## Technology Stack

### Backend
| Component | Technology | Version |
|-----------|-----------|---------|
| Language | Java | 17 |
| Framework | Spring Boot | 3.2 |
| Build Tool | Maven | 3.x |
| Web Server | Embedded Tomcat | 10.x |
| Security | Spring Security | 6.x |
| Database Access | Spring JDBC | 3.2 |
| Validation | JSqlParser | Latest |

### Frontend
| Component | Technology | Purpose |
|-----------|-----------|---------|
| Template Engine | Thymeleaf | HTML rendering |
| CSS Framework | Bootstrap 5 | UI/styling |
| Charts | Chart.js | Data visualization |
| JavaScript | Vanilla JS | Client-side logic |

### Database
| Component | Technology | Details |
|-----------|-----------|---------|
| Database | PostgreSQL (Neon) | Cloud-hosted |
| Connection Pool | HikariCP | JDBC connection pooling |
| Query Timeout | 30 seconds | Safety limit |

### AI Providers (Configurable)
| Provider | Model | Status |
|----------|-------|--------|
| Groq | openai/gpt-oss-20b | Default/Primary |
| Gemini | Multiple options | Fallback 1 |
| NVIDIA | deepseek-ai/deepseek-v4.1-flash | Fallback 2 |
| OpenAI | gpt-4 | Alternative |

**Fallback Strategy:** If primary provider fails or times out, automatically tries next provider in list.

### Export
| Component | Technology | Format |
|-----------|-----------|--------|
| PDF | iText / Flying Saucer | PDF output |
| Excel | Apache POI | .xlsx files |
| Storage | File System | Configurable directory |

---

## User Roles & Access Control

### Four Role Levels

```
┌────────────────────────────────────────────┐
│         Role-Based Access Control          │
├────────────────────────────────────────────┤
│ ADMIN                                      │
│   ✓ Full system access                    │
│   ✓ All reports & analytics               │
│   ✓ User management                       │
│   ✓ System configuration                  │
│                                            │
│ BUSINESS / MANAGER                        │
│   ✓ Predefined reports                    │
│   ✓ AI chat for their data                │
│   ✓ Export results                        │
│   ✗ System configuration                  │
│                                            │
│ CUSTOMER                                  │
│   ✓ Own devices & transactions only       │
│   ✓ Predefined reports (filtered)         │
│   ✓ AI chat (results filtered)            │
│   ✗ Access to other customer data         │
│   ✗ Sensitive columns hidden              │
│                                            │
└────────────────────────────────────────────┘
```

### Data Isolation Strategy

1. **Authentication:** BCrypt password validation
2. **Authorization:** Spring Security role checks
3. **Row-Level Security:** Queries filtered by customer ID
4. **Column-Level Security:** Sensitive fields excluded for CUSTOMER users
5. **Query Validation:** JSqlParser ensures read-only, no DDL/DML

---

## Configuration

All credentials come from **environment variables** or `.env` file (git-ignored).

### Required Configuration

```properties
# Database
DB_URL=jdbc:postgresql://<neon-host>:5432/neondb?sslmode=require
DB_USER=<db-user>
DB_PASSWORD=<db-password>

# AI: primary provider + fallbacks (tried in order on error/timeout)
AI_PROVIDER=groq                        # groq | gemini | nvidia | openai
AI_FALLBACK_PROVIDERS=gemini,nvidia
GROQ_API_KEY=<key>                      # Required if using Groq
GEMINI_API_KEY=<key>                    # Required if using Gemini
NVIDIA_API_KEY=<key>                    # Required if using NVIDIA

# Optional
EXPORT_DIR=<folder for export files>    # default: <java.io.tmpdir>/datatalk-exports
server.port=8081                        # default: 8081
```

### Default Settings (application.properties)
- Server port: 8081
- Context path: /datatalk
- Session timeout: 30 minutes
- Query timeout: 30 seconds
- Log file: logs/datatalk-ai.log

---

## Demo Users

Six demo users with different roles:

| Username | Role | Purpose |
|----------|------|---------|
| admin_user | ADMIN | System administration |
| techpay_manager | BUSINESS | Payment processor |
| retail_manager | MANAGER | Retail data |
| techpay_customer | CUSTOMER | Tech Pay customer |
| restaurant_owner | BUSINESS | Restaurant business |
| gas_operator | CUSTOMER | Gas station operator |

**Note:** All use synthetic demo data. Reset passwords via [docs/DEMO_USERS_GUIDE.md](docs/DEMO_USERS_GUIDE.md).

---

## Quick Start

### 1. Database Setup (Neon SQL Editor)
Run in this order:
```
1. database/neon-schema.sql
2. database/sample-data.sql
3. database/permissions.sql
4. database/phase2-migration.sql
5. database/phase3-migration.sql  (as neondb_owner)
```

### 2. Configure Environment
Create `.env` file in project root with:
```
DB_URL=...
DB_USER=...
DB_PASSWORD=...
AI_PROVIDER=groq
GROQ_API_KEY=...
```

### 3. Build & Run
```bash
mvn spring-boot:run
```
Visit: http://localhost:8081/datatalk/login

### 4. Run Tests
```bash
mvn test  # 32 tests (requires database for integration tests)
```

---

## Key Features

### ✅ Implemented

- **Login & Session Management**
  - BCrypt password hashing
  - Session-based authentication
  - Login history tracking

- **AI Chat**
  - Natural language question → SQL
  - Multi-provider with fallback (Groq, Gemini, NVIDIA)
  - JSqlParser validation (read-only enforcement)
  - 30-second timeout protection
  - Table + Chart visualization
  - Follow-up question support
  - Query caching

- **Predefined Reports** (11 total)
  - Payment analytics
  - Device management
  - Settlement tracking
  - Customer reports
  - Pre-cached SQL

- **Data Security**
  - Role-based access control (4 levels)
  - Customer-level data isolation
  - Sensitive column filtering
  - Read-only query enforcement
  - SQL injection prevention

- **Export**
  - PDF generation
  - Excel export (.xlsx)
  - Background job processing
  - Job persistence (Phase 3)

- **Analytics** (Admin only)
  - Query popularity
  - Success/failure rates
  - AI provider usage metrics
  - Performance timing

- **UI/UX**
  - Responsive Bootstrap 5 design
  - Interactive Chart.js visualizations
  - Pagination & filtering
  - Mobile-friendly

### 📋 Partial Implementation
- Export job background queue (fully functional, storage pending Phase 3)
- Advanced caching strategies

### 🔄 Not Yet Implemented
- Real-time collaboration
- Advanced security audit logging
- Multi-language support
- Mobile app

---

## Performance & Safety

### Query Protection
- **Timeout:** 30 seconds per query
- **Read-only:** JSqlParser blocks INSERT, UPDATE, DELETE
- **Validation:** No DDL, no stored procedures
- **Pooling:** HikariCP connection pool (20 connections)

### AI Safety
- **Primary provider:** Groq (fast, cost-effective)
- **Fallback chain:** Automatic retry on timeout/error
- **Timeout:** 30 seconds per provider call
- **Rate limiting:** (Can be configured per provider)

### Export Safety
- **Async processing:** Large exports don't block UI
- **Disk safety:** Writes to temp directory with cleanup
- **Job tracking:** Metadata stored in database
- **Cleanup:** Old exports auto-deleted after 7 days

---

## Troubleshooting

### "No AI provider configured"
**Solution:** Set at least one `*_API_KEY` in `.env` or environment variables.

### Slow or failed AI answers
**Solution:** The app automatically falls back to the next provider. Check `logs/datatalk-ai.log` for details.

### "Export job persistence failed – run database/phase3-migration.sql"
**Status:** Exports still work, but job metadata isn't stored until Phase 3 script runs.

### Port already in use
**Solution:** 
```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8091
```

### Database connection issues
**Check:**
- `.env` file has correct credentials
- Database host is accessible
- SSL mode is set to `require` in connection string
- Network firewall allows port 5432

---

## API Endpoints

### Public (No Auth)
- `GET /datatalk/login` - Login page
- `POST /datatalk/login` - Submit login

### Authenticated
- `GET /datatalk/dashboard` - Main dashboard
- `GET /datatalk/reports` - Predefined reports
- `POST /datatalk/api/chat` - Submit question
- `GET /datatalk/api/chat/{conversationId}` - Get chat history
- `POST /datatalk/api/export` - Export results (PDF/Excel)

### Admin Only
- `GET /datatalk/analytics` - Query analytics page
- `GET /datatalk/api/analytics/stats` - Analytics data
- `POST /datatalk/admin/users` - User management

---

## Database Schema Summary

### Core Tables
- `app_user` - Login credentials, roles, status
- `customer` - Customer master data
- `device` - IoT/payment devices
- `transaction` - Payment/service transactions
- `settlement` - Reconciliation records

### Chat Tables (Phase 2)
- `chat_conversation` - Chat session metadata
- `chat_message` - Individual messages & responses

### Export Tables (Phase 3)
- `app.export_job` - Job metadata, status, file path

### Analytics Tables
- `query_log` - Query tracking
- `provider_usage` - AI provider metrics

---

## Deployment Considerations

### Prerequisites
- Java 17+
- PostgreSQL 12+ (Neon cloud)
- Maven 3.6+
- API keys for AI providers (Groq, Gemini, NVIDIA)

### Environment Setup
1. Create `.env` or set system environment variables
2. Run database migrations in order
3. Build: `mvn clean package`
4. Run: `java -jar target/datatalk-ai-poc-0.1.0.jar`

### Monitoring
- **Logs:** `logs/datatalk-ai.log`
- **Metrics:** `/datatalk/analytics` (admin only)
- **Health check:** `GET /actuator/health` (Spring Boot)

---

## Documentation Files

| File | Purpose |
|------|---------|
| `docs/POC_IMPLEMENTATION_DETAILS.md` | Architecture, flows, APIs, setup |
| `docs/PROJECT_STRUCTURE_AND_FILE_DETAILS.md` | Folder structure & file purposes |
| `docs/DATABASE_DESIGN.md` | Schema, ER diagram, roles |
| `docs/PREDEFINED_REPORTS_REFERENCE.md` | All 11 reports & how to add |
| `docs/DEMO_USERS_GUIDE.md` | Demo users & password reset |
| `docs/POC_PROGRESS.md` | Status & completed items |
| `docs/NEXT_STEPS.md` | Planned enhancements |
| `database/README.md` | SQL script execution guide |

---

## Disclaimer

This is a **POC (Proof of Concept)** that uses **synthetic demo data only**. It has **not been load-tested** or **security-certified** for production use. Before deploying to production:

- [ ] Complete security audit
- [ ] Load testing & performance tuning
- [ ] Real data handling procedures
- [ ] Backup & disaster recovery plan
- [ ] User access audit logging
- [ ] Compliance verification (GDPR, HIPAA, etc.)

---

## Summary

**DataTalk AI** demonstrates a modern, secure approach to natural-language data analytics:

1. **AI-Powered Questions** → SQL generation with fallback providers
2. **Security-First** → Role-based access, data isolation, read-only enforcement
3. **User-Friendly** → Chat interface, charts, exports (PDF/Excel)
4. **Production-Ready POC** → Full authentication, analytics, background jobs
5. **Extensible** → Add providers, reports, custom features

The architecture separates concerns (Controllers → Services → Repositories → Database), uses Spring Security for access control, validates all SQL queries, and provides analytics for admin monitoring.

---

*Last Updated: November 4, 2026*  
*Version: 0.1.0 (POC Phase 2/3)*

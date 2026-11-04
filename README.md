# DataTalk AI – Natural-Language Reporting POC

DataTalk AI is a personal proof of concept. Users log in, ask business questions in plain English, and get
database-backed reports. An AI model generates PostgreSQL SQL. The backend validates it, applies the user's
role and customer restrictions, and runs it read-only. The results are shown as a table and chart, and can be
exported to PDF or Excel.

| | |
|---|---|
| **Status** | POC complete (Phase 1 foundation, Phase 2 AI reporting, Phase 3 finalization). See [docs/POC_PROGRESS.md](docs/POC_PROGRESS.md) |
| **Stack** | Java 17, Spring Boot 3.2, Maven, Spring JDBC, Spring Security, Thymeleaf, Bootstrap 5, Chart.js, Neon PostgreSQL |
| **AI providers** | Groq (default), Gemini, NVIDIA (OpenAI-compatible); configurable primary plus automatic fallback |
| **Last updated** | November 4, 2026 |

## Documentation

**Personal POC technical documentation**

| Document | Content |
|----------|---------|
| [docs/POC_IMPLEMENTATION_DETAILS.md](docs/POC_IMPLEMENTATION_DETAILS.md) | Architecture, flows, APIs, setup, and implemented vs. partial vs. not-implemented features |
| [docs/PROJECT_STRUCTURE_AND_FILE_DETAILS.md](docs/PROJECT_STRUCTURE_AND_FILE_DETAILS.md) | Actual folder structure and the purpose of every important file and class |
| [docs/DATABASE_DESIGN.md](docs/DATABASE_DESIGN.md) | Neon configuration, schemas, tables, ER diagram, roles, script order |
| [docs/PREDEFINED_REPORTS_REFERENCE.md](docs/PREDEFINED_REPORTS_REFERENCE.md) | All predefined SQL reports, and how to add a new one |
| [docs/DEMO_USERS_GUIDE.md](docs/DEMO_USERS_GUIDE.md) | Demo users, and how to set or reset passwords |
| [docs/POC_PROGRESS.md](docs/POC_PROGRESS.md) / [docs/NEXT_STEPS.md](docs/NEXT_STEPS.md) | Status and planned next steps |
| [database/README.md](database/README.md) | Running the SQL scripts in the Neon SQL Editor |

**Company proposal (for the AI/ML team):**
[docs/DATATALK_AI_BUSINESS_AND_TECHNICAL_REQUIREMENTS.md](docs/DATATALK_AI_BUSINESS_AND_TECHNICAL_REQUIREMENTS.md).
This is a business and technical requirements document. It is not a code handover.

## Quick start

### 1. Database (Neon SQL Editor, in this order)
1. `database/neon-schema.sql`
2. `database/sample-data.sql`
3. `database/permissions.sql`
4. `database/phase2-migration.sql`
5. `database/phase3-migration.sql` (export job table). Run it as the database owner (`neondb_owner`).

`database/verify-demo-users.sql` is an optional check script. See [database/README.md](database/README.md).

### 2. Configuration
Credentials come **only** from environment variables or a local, git-ignored `.env` file in the project root.
The `.env` file is loaded automatically through `spring.config.import`. Never commit real values.

```properties
# Database
DB_URL=jdbc:postgresql://<neon-host>:5432/neondb?sslmode=require
DB_USER=<db-user>
DB_PASSWORD=<db-password>

# AI: primary provider + fallbacks (tried in order on error or timeout)
AI_PROVIDER=groq                      # groq | gemini | nvidia | openai
AI_FALLBACK_PROVIDERS=gemini,nvidia
GROQ_API_KEY=<key>                    # GROQ_MODEL default: openai/gpt-oss-20b
GEMINI_API_KEY=<key>                  # GEMINI_MODEL: comma-separated list allowed
NVIDIA_API_KEY=<key>                  # NVIDIA_MODEL default: deepseek-ai/deepseek-v4.1-flash

# Optional
EXPORT_DIR=<folder for export files>  # default: <java.io.tmpdir>/datatalk-exports
```
The full list of settings and defaults is in `src/main/resources/application.properties`.

### 3. Build and run
```bash
mvn spring-boot:run          # or run DataTalkAiApplication from IntelliJ
mvn test                     # 32 tests; LoginAndAccessIntegrationTest needs the database
```
Open **http://localhost:8081/datatalk/login**. The port is set by `server.port` and the context path is `/datatalk`.

### 4. Demo users
Six demo users exist, one or more per role: `admin_user` (ADMIN), `techpay_manager` (BUSINESS),
`retail_manager` (MANAGER), `techpay_customer` (CUSTOMER), `restaurant_owner` (BUSINESS) and `gas_operator` (CUSTOMER).
Demo passwords and the reset procedure are in [docs/DEMO_USERS_GUIDE.md](docs/DEMO_USERS_GUIDE.md).

## Main features
- Login and logout with BCrypt passwords, session management and login history.
- Role-based access: ADMIN, BUSINESS, MANAGER, CUSTOMER.
- **AI chat:** question → AI SQL → JSqlParser validation → read-only scoped execution (30 s timeout).
  - Results appear as a table and chart, with filters, pagination and an explanation.
  - Follow-up questions are supported.
  - Each answer shows which provider:model answered.
- Customer-level data isolation. CUSTOMER users see only their assigned devices, and sensitive columns are blocked.
- 11 predefined reports covering payments, devices, settlements and customers.
- Chat history and saved conversation context. Repeated questions reuse cached SQL.
- PDF and Excel export. Large exports run in the background, and export jobs are stored in `app.export_job`.
- **Query Analytics** (ADMIN only, `/datatalk/analytics`): questions, success and failure counts, timings, popular reports and provider usage.
- POC information page at `/datatalk/poc-info`.

## Project layout (short)
```
database/            SQL scripts (schema, sample data, permissions, migrations)
docs/                documentation (see table above)
src/main/java/com/datatalk/datatalkai/
  ai/ chat/ reporting/ reports/ export/ analytics/   AI reporting features
  controller/ service/ repository/ model/ security/ config/
src/main/resources/  application.properties, templates/, static/, predefined-reports/
src/test/java/       unit tests + one small login/access integration test
```
The full structure is in [docs/PROJECT_STRUCTURE_AND_FILE_DETAILS.md](docs/PROJECT_STRUCTURE_AND_FILE_DETAILS.md).

## Troubleshooting
- **"No AI provider configured":** set at least one `*_API_KEY` in `.env` or the environment.
- **Slow or failed AI answers:** the app falls back to the next provider automatically. The badge on each answer shows which provider was used.
- **"Export job persistence failed – run database/phase3-migration.sql"** in the log: exports still work, but jobs are not stored until that script is run.
- **Port in use:** `mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8091`.
- **Logs:** `logs/datatalk-ai.log`.

## Disclaimer
This is a POC that uses synthetic data only. It has not been load-tested or security-certified for production use.

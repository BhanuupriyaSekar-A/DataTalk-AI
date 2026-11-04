# DataTalk AI – POC Progress

**Last updated:** November 4, 2026
**Overall status:** POC functionally complete (Phases 1–3). One database step is pending: run `database/phase3-migration.sql` on Neon.

Status keys: ✅ Completed · 🟡 Partially completed · ❌ Not implemented · 🔮 Future enhancement

---

## Phase 1 – Foundation ✅
- ✅ Neon PostgreSQL schema with 6 schemas (`corp`, `location`, `app_user`, `device`, `txn`, `app`), keys, indexes and comments (`database/neon-schema.sql`).
- ✅ Synthetic sample data: 5 customers, 15 locations, 60 devices, 6 users, login history, device events, about 10,000 transactions over 120 days, and settlements (`database/sample-data.sql`).
- ✅ Read-only reporting role `datatalk_ro` and application user grants (`database/permissions.sql`).
- ✅ Spring Boot 3.2 / Java 17 / Maven application with Spring JDBC and Spring Security.
- ✅ Login and logout APIs, BCrypt passwords, session management, login history (IP, status, failure reason).
- ✅ Role-based access (ADMIN, BUSINESS, MANAGER, CUSTOMER) and a user-info API (user_id, username, full name, role, customer_id).
- ✅ Login page, dashboard (user details and database summary) and POC information page.

## Phase 2 – AI Natural-Language Reporting ✅
- ✅ AI providers: Groq (default `openai/gpt-oss-20b`), Gemini and NVIDIA, plus a generic OpenAI-compatible provider.
  - The primary provider and fallback order are configurable, with automatic fallback on error or timeout.
  - Keys come from environment variables or `.env` only.
- ✅ The UI shows the configured provider chain and which provider:model answered each question.
- ✅ Schema discovery from database metadata and comments, filtered by role. The prompt includes predefined-report examples.
- ✅ Natural-language → SQL → JSqlParser validation:
  - SELECT/CTE only, table allow-list per role, blocked sensitive columns.
- ✅ Backend-enforced scoping and execution:
  - Customer- and device-level scoping is enforced in Java.
  - Execution is read-only, with a 30 s statement timeout, row limits and pagination.
- ✅ Chat UI with result tables, Chart.js charts, date and status filters, an explanation, and SQL details.
- ✅ Chat history (`app.chat_history`), conversations with follow-up context (`app.user_ai_context`), and an SQL cache for repeated questions.
- ✅ 11 predefined reports covering payments, devices, settlements and customers.
- ✅ PDF and Excel export. Large results (over 2,000 rows) run as background jobs and are downloadable by the owner only.

## Phase 3 – Finalization ✅ (with one pending database step)
- ✅ **Export job persistence:**
  - `app.export_job` stores job ID, user, report name, format, status, created and completed dates, file location and error.
  - Code: `ExportJobRepository` and `ExportJobService`.
  - Persistence is best-effort, so exports still work if the table is missing.
  - 🟡 **Pending:** run `database/phase3-migration.sql` as `neondb_owner` in the Neon SQL Editor. Until then, jobs are kept in memory only and a warning is logged.
- ✅ **Query Analytics (ADMIN only)** at `/datatalk/analytics` and `GET /api/admin/analytics`:
  - Total, successful and failed questions, and active users.
  - Average AI answer time and average SQL-only time.
  - Answer source, failure reasons, popular reports, AI provider usage, and export job counts.
- ✅ Fixed the logout bug: `logout_time` was never recorded because the UPDATE used an invalid `UPDATE … LIMIT`.
- ✅ Removed the hardcoded database password default from `application.properties`. It now comes from `DB_PASSWORD` in the environment or `.env`.
- ✅ Replaced the outdated Phase 1 integration tests with one small `LoginAndAccessIntegrationTest`.
- ✅ Project cleanup: removed outdated root reports, build scripts and logs, and duplicate docs.
- ✅ Documentation: personal technical docs and the company requirements proposal (see README).

---

## Testing status
| Test | Result |
|------|--------|
| `SqlSafetyValidatorTest` (17 unit tests) | ✅ Pass |
| `AiProviderChainTest` (8 unit tests) | ✅ Pass |
| `LoginAndAccessIntegrationTest` (7 tests, needs the Neon DB): login, wrong password, 401 without login, ADMIN analytics allowed, non-admin analytics → 403, CUSTOMER sees no settlement reports, logout | ✅ Pass |
| Manual API checks: AI question via Groq (CUSTOMER), predefined report, PDF direct export, Excel background job (status, download, other users get 404), analytics as ADMIN and as CUSTOMER (403) | ✅ Pass (Nov 4, 2026) |
| Export job rows in `app.export_job` | 🟡 Not verifiable until the phase 3 migration is run |
| Comprehensive integration, E2E, load and security testing | ❌ Not done (future phase) |

## Partially completed 🟡
- **Query Analytics timings:** these are approximations derived from `app.chat_history.duration_ms`. There are no separate AI-time and SQL-time columns.
- **Performance optimization:** only basic measures are in place: SQL cache, timeouts, row limits and pagination.
- **AI accuracy evaluation:** manual spot checks only; there is no evaluation dataset.

## Not implemented ❌
- Password reset UI and user-management screens. Demo passwords are reset with SQL; see `docs/DEMO_USERS_GUIDE.md`.
- Production deployment, CI/CD, monitoring and alerting.
- Load and security testing.

## Known issues
- `gemini-3.8-flash` often returns HTTP 503 or hangs; `gemini-3.1-flash-lite` works.
- NVIDIA free-tier models take 30–100 s.
- The Groq free tier is limited to 8K tokens per minute.
- The background export executor and its download files are local to one application instance.
- **Rotate the Neon DB password:** older git commits contained it in plain text.

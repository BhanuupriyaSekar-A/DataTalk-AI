# SL Talk AI – Project Structure and File Details

> Reference guide to every important folder and file in the SL Talk AI POC.
> Generated from the actual source code (Phase 1 + Phase 2 + Phase 3). Where behaviour could not be
> confirmed from the code, it is marked **(uncertain)**.

---

## 1. Overview

SL Talk AI is a personal proof-of-concept for **natural-language business reporting**:

1. A user logs in (session-based, BCrypt passwords, roles `ADMIN`, `BUSINESS`, `MANAGER`, `CUSTOMER`).
2. The user types a business question in Home > Report Search (or runs a predefined report).
3. The backend builds a prompt from **approved schema metadata** and **predefined report examples**, and sends it to
   an AI provider via a **fallback chain** (Groq → Gemini → NVIDIA by default; OpenAI is also supported).
4. The AI returns a JSON report definition containing a PostgreSQL `SELECT`.
5. The SQL is **parsed and validated in Java** (JSqlParser), rewritten with Java-generated **role/customer/device
   scope CTEs**, and executed inside a **READ ONLY transaction** under a SELECT-only database role (`datatalk_ro`)
   against **Neon PostgreSQL**.
6. Results are shown as a paginated table plus an optional chart (Chart.js) and can be exported as **PDF** or **Excel**
   (large exports run as background jobs persisted in `app.export_job`).
7. Administrators get a **Query Analytics** page (`/analytics`) computed from `app.chat_history` and `app.export_job`.

**Tech stack** (from `pom.xml`): Java 17, Spring Boot 3.2.0 (web, jdbc, security, thymeleaf), PostgreSQL JDBC 42.7.4,
JSqlParser 4.9, OpenPDF 1.3.43, Apache POI (poi-ooxml) 5.2.5, Lombok, Jackson. Front end: Thymeleaf HTML pages,
Bootstrap 5.3 / Font Awesome 6.4 (CDN), Chart.js 4.4.1 (CDN), vanilla JavaScript.

Application base URL: `http://localhost:8081/sltalk` (`server.port=8081`, `server.servlet.context-path=/sltalk`).

---

## 2. Repository tree

Generated from `git ls-files` + `git ls-files --others --exclude-standard` (files that exist on disk now).
Excluded: `.git/`, `.idea/`, `target/`, `logs/`. `.env` is a **git-ignored local secrets file** (contents never shown).

```text
DataTalk-AI---POC/
├── .env                                  (git-ignored, local only – optional secrets file; never committed)
├── .gitignore
├── README.md
├── pom.xml
├── database/
│   ├── README.md
│   ├── neon-schema.sql
│   ├── sample-data.sql
│   ├── permissions.sql
│   ├── phase2-migration.sql
│   ├── phase3-migration.sql
│   └── verify-demo-users.sql
├── docs/
│   ├── DATABASE_DESIGN.md                      (actual schema, ERD, roles, scripts)
│   ├── DATATALK_AI_BUSINESS_AND_TECHNICAL_REQUIREMENTS.md  (company AI/ML team proposal)
│   ├── DEMO_USERS_GUIDE.md                     (demo users, password set/reset)
│   ├── NEXT_STEPS.md                           (planned phases)
│   ├── POC_IMPLEMENTATION_DETAILS.md           (how the POC works, status per feature)
│   ├── POC_PROGRESS.md                         (completed / partial / pending)
│   ├── PREDEFINED_REPORTS_REFERENCE.md         (all predefined report SQL)
│   └── PROJECT_STRUCTURE_AND_FILE_DETAILS.md   (this file)
└── src/
    ├── main/
    │   ├── java/com/datatalk/datatalkai/
    │   │   ├── DataTalkAiApplication.java
    │   │   ├── ai/
    │   │   │   ├── AiException.java
    │   │   │   ├── AiHttpClients.java
    │   │   │   ├── AiProvider.java
    │   │   │   ├── AiProviderChain.java
    │   │   │   ├── AiProviderConfig.java
    │   │   │   ├── AiReportResponse.java
    │   │   │   ├── AiResponseParser.java
    │   │   │   ├── GeminiProvider.java
    │   │   │   ├── OpenAiCompatibleProvider.java
    │   │   │   └── PromptBuilder.java
    │   │   ├── analytics/
    │   │   │   └── QueryAnalyticsService.java
    │   │   ├── chat/
    │   │   │   ├── ChatHistoryRepository.java
    │   │   │   ├── ChatRecord.java
    │   │   │   ├── ChatResult.java
    │   │   │   ├── ChatService.java
    │   │   │   ├── ChatStatus.java
    │   │   │   └── UserAiContextService.java
    │   │   ├── controller/
    │   │   │   ├── AnalyticsApiController.java
    │   │   │   ├── ApiExceptionHandler.java
    │   │   │   ├── AuthApiController.java
    │   │   │   ├── ChatApiController.java
    │   │   │   ├── DashboardApiController.java
    │   │   │   ├── ExportApiController.java
    │   │   │   ├── PageController.java
    │   │   │   └── ReportApiController.java
    │   │   ├── export/
    │   │   │   ├── ExportFormat.java
    │   │   │   ├── ExportJobRepository.java
    │   │   │   ├── ExportJobService.java
    │   │   │   └── ReportExportService.java
    │   │   ├── model/
    │   │   │   ├── Customer.java
    │   │   │   ├── User.java
    │   │   │   └── dto/
    │   │   │       ├── ApiResponse.java
    │   │   │       ├── LoginRequest.java
    │   │   │       └── UserInfoResponse.java
    │   │   ├── reporting/
    │   │   │   ├── AccessPolicyService.java
    │   │   │   ├── ReportAccessException.java
    │   │   │   ├── ReportExecutionException.java
    │   │   │   ├── ReportExecutionService.java
    │   │   │   ├── ReportFilters.java
    │   │   │   ├── ReportPage.java
    │   │   │   ├── ReportingCatalog.java
    │   │   │   ├── ReportingConfig.java
    │   │   │   ├── SchemaMetadataService.java
    │   │   │   ├── SecureQuery.java
    │   │   │   ├── SqlSafetyValidator.java
    │   │   │   ├── SqlValidationException.java
    │   │   │   └── UserScope.java
    │   │   ├── reports/
    │   │   │   ├── PredefinedReport.java
    │   │   │   └── PredefinedReportService.java
    │   │   ├── repository/
    │   │   │   ├── CustomerRepository.java
    │   │   │   └── UserRepository.java
    │   │   ├── security/
    │   │   │   └── SecurityConfig.java
    │   │   └── service/
    │   │       ├── AuthenticationService.java
    │   │       └── DashboardService.java
    │   └── resources/
    │       ├── application.properties
    │       ├── predefined-reports/
    │       │   ├── customer/
    │       │   │   ├── 01-revenue-by-customer.sql
    │       │   │   └── 02-locations-and-devices.sql
    │       │   ├── device/
    │       │   │   ├── 01-device-status-summary.sql
    │       │   │   ├── 02-offline-devices.sql
    │       │   │   └── 03-device-events.sql
    │       │   ├── payment/
    │       │   │   ├── 01-daily-sales-trend.sql
    │       │   │   ├── 02-failed-transactions-by-location.sql
    │       │   │   ├── 03-sales-by-card-type.sql
    │       │   │   └── 04-decline-reasons.sql
    │       │   └── settlement/
    │       │       ├── 01-settlement-summary.sql
    │       │       └── 02-weekly-net-settlement.sql
    │       ├── static/
    │       │   ├── css/style.css
    │       │   ├── img/logo.svg
    │       │   └── js/ (chat.js, navbar.js, records.js, search.js)
    │       └── templates/
    │           ├── analytics.html
    │           ├── dashboard.html
    │           ├── fragments/navbar.html
    │           ├── home.html
    │           ├── login.html
    │           ├── poc-info.html
    │           └── records.html
    └── test/java/com/datatalk/datatalkai/
        ├── LoginAndAccessIntegrationTest.java
        ├── ai/AiProviderChainTest.java
        ├── chat/ChatServiceTestAccess.java
        └── reporting/SqlSafetyValidatorTest.java
```

> Note: at the time of writing many Phase 2/3 files (e.g. `ai/`, `chat/`, `reporting/`, `export/`, `analytics/`,
> `predefined-reports/`, `static/js/`, `src/test/`, `database/phase*-migration.sql`) are **untracked** in git but are
> part of the project. Several older root-level `.md` / `.bat` / `.ps1` files were deleted and are not documented.

---

## 3. Folder / package purposes

| Folder / package | Purpose |
|---|---|
| `database/` | SQL scripts to create the Neon schema, load demo data, set up roles/permissions and apply Phase 2/3 migrations. |
| `docs/` | Project documentation (database design, demo users, progress, next steps, this file). |
| `com.datatalk.datatalkai` | Spring Boot entry point (`DataTalkAiApplication`). |
| `…ai` | AI provider abstraction, provider implementations (Gemini, OpenAI-compatible: Groq/OpenAI/NVIDIA), fallback chain, prompt building and JSON answer parsing. |
| `…analytics` | ADMIN-only Query Analytics calculations (`QueryAnalyticsService`). |
| `…chat` | Chat orchestration (`ChatService`), chat history persistence, per-user AI context (`app.user_ai_context`). |
| `…controller` | MVC page controller, REST API controllers, and the reporting API exception handler. |
| `…export` | PDF/Excel generation, background export jobs, export job persistence (`app.export_job`). |
| `…model`, `…model.dto` | Plain data classes (`User`, `Customer`) and API DTOs (`ApiResponse`, `LoginRequest`, `UserInfoResponse`). |
| `…reporting` | Security core of reporting: user scope, schema metadata catalog, SQL safety validator, read-only executor, filters, exceptions. |
| `…reports` | Predefined report model and loader for `resources/predefined-reports/**`. |
| `…repository` | Phase 1 JDBC repositories for users and customers. |
| `…security` | Spring Security configuration. |
| `…service` | Phase 1 services (authentication, dashboard summary). |
| `resources/predefined-reports/<category>/` | SQL report templates with header metadata (name, description, params, examples, chart). |
| `resources/templates/` | Thymeleaf HTML pages (login, dashboard, chat, analytics, poc-info). |
| `resources/static/` | Shared CSS (`css/style.css`) and scripts (`js/search.js` Home search, `js/chat.js` Report Search AI chat, `js/navbar.js`, `js/records.js`), served at `/sltalk/static/**`. |
| `src/test/java/…` | JUnit 5 unit tests (validator, AI chain) and a Spring Boot integration smoke test. |

---

## 4. Configuration

### `pom.xml`
- **Purpose:** Maven build definition (`com.datatalk:datatalk-ai-poc:0.1.0`, jar packaging, parent `spring-boot-starter-parent 3.2.0`, Java 17).
- **Main responsibility:** Declares dependencies:
  `spring-boot-starter-web`, `-jdbc`, `-security`, `-thymeleaf`, `thymeleaf-extras-springsecurity6`, `postgresql 42.7.4` (runtime),
  `spring-security-crypto`, `jackson-databind`, `jsqlparser 4.9`, `openpdf 1.3.43`, `poi-ooxml 5.2.5`, `lombok` (optional),
  test: `spring-boot-starter-test`, `spring-security-test`.
- **Plugins:** `spring-boot-maven-plugin` (Lombok excluded from the fat jar), `maven-compiler-plugin 3.11.0` (source/target 17).

### `src/main/resources/application.properties`
- **Purpose:** All runtime configuration; secrets come from environment variables or the git-ignored `.env`.
- **Main responsibility (sections):**
  - `spring.config.import=optional:file:.env[.properties]` – loads local `KEY=value` file if present; real env vars still win.
  - Server: port `8081`, context path `/sltalk`, session cookie http-only, session timeout `30m`.
  - Datasource (Neon PostgreSQL) + HikariCP pool (max 20, min idle 5).
  - Logging: `DEBUG` for `com.datatalk.datatalkai` and Spring Security; file `logs/sltalk-ai.log` (10 MB, 10 files).
  - Jackson: `non_null` inclusion, ISO dates, indented output. Thymeleaf: cache off, `classpath:/templates/`.
  - `spring.mvc.static-path-pattern=/static/**`.
  - `app.ai.*` – AI provider chain, models, URLs, timeouts, trust store.
  - `app.reporting.*` – approved schemas, blocked columns, read-only role, timeouts, paging, role → tables.
  - `app.export.*` – export limits and directory.
  - Also defines `app.name`, `app.version=0.2.0`, `app.description`, `app.max-login-attempts=5`,
    `app.account-lock-duration=30m` – **no code reference to the last two was found** (uncertain whether used).
- Full variable list: see [Section 17](#17-configuration--environment-variables).

### `DataTalkAiApplication.java` (`com.datatalk.datatalkai`)
- **Purpose:** Spring Boot entry point (`@SpringBootApplication`).
- **Important methods:**
  - `main(String[])` – starts the application.
  - `passwordEncoder()` – `@Bean` `BCryptPasswordEncoder` used by `AuthenticationService`.

### `reporting/ReportingConfig.java`
- **Purpose:** Spring wiring for plain-Java reporting components.
- **Important methods:** `sqlSafetyValidator(int maxSqlLength)` – creates the `SqlSafetyValidator` bean from `app.reporting.max-sql-length` (default 6000).

### `ai/AiProviderConfig.java`
- Described under [AI provider integration](#7-ai-provider-integration).

---

## 5. Authentication & authorization

### `security/SecurityConfig.java`
- **Purpose:** Spring Security filter chain (`@EnableWebSecurity`).
- **Main responsibility:**
  - `permitAll`: `/login`, `/api/login`, `/static/**`, `/health`, `/info`, `/poc-info`.
  - `hasRole("ADMIN")`: `/analytics`, `/api/admin/**`.
  - Everything else: `authenticated()`.
  - Form login configured (`/login`, success → `/home`), logout at `/logout` (invalidates session, deletes `JSESSIONID`).
    The UI itself logs in through the JSON endpoint `/api/login`; whether the form-login processing URL is functional
    (no `UserDetailsService` bean was found) is **uncertain**.
  - CSRF: cookie token repository, ignored for `/api/**`.
  - `/api/**` returns **HTTP 401** (`HttpStatusEntryPoint`) instead of redirecting when unauthenticated.
  - Session concurrency: max 1 session per user, expired → `/login?expired=true`.
- **Important methods:** `securityFilterChain(HttpSecurity)`.

### `controller/AuthApiController.java` (`/api`)
- **Purpose:** JSON login/logout/user-info API.
- **Important methods:**
  - `login(LoginRequest, …)` – `POST /api/login`; validates input, calls `AuthenticationService.authenticate`,
    calls `request.changeSessionId()` (session fixation protection), stores a `UsernamePasswordAuthenticationToken`
    with authority `ROLE_<role>` in the `SecurityContext` (saved via `HttpSessionSecurityContextRepository`), and sets
    session attributes `userId`, `username`, `role`, `customerId`, `userInfo`.
  - `getUserInfo(HttpSession)` – `GET /api/user-info`; returns fresh user info from the database.
  - `logout(HttpSession)` – `POST /api/logout`; records logout time, clears the security context, invalidates the session.
  - `getClientIp(HttpServletRequest)` – reads `X-Forwarded-For` / `X-Real-IP` / remote address.
- **Dependencies:** `AuthenticationService`, DTOs `LoginRequest`, `UserInfoResponse`, `ApiResponse`.

### `service/AuthenticationService.java`
- **Purpose:** Username/password verification and user-info building.
- **Important methods:**
  - `authenticate(username, password, ipAddress)` – finds an ACTIVE user, checks BCrypt hash, logs every attempt
    (`SUCCESS`/`FAILED`/`INACTIVE_USER`) to `app_user.user_login_history`, returns `UserInfoResponse` (with customer name).
  - `getUserInfo(Integer userId)` – builds `UserInfoResponse` for an id.
  - `logout(Integer userId)` – delegates to `UserRepository.logLogout`.
- **Dependencies:** `UserRepository`, `CustomerRepository`, `PasswordEncoder`.

### `repository/UserRepository.java`
- **Purpose:** JDBC access to `app_user.app_user` and `app_user.user_login_history`.
- **Important methods:**
  - `findByUsername(String)` – active users only.
  - `findById(Integer)` – any status (status is checked by callers).
  - `logLoginAttempt(userId, ip, status, failureReason)` – inserts login history row (channel `WEB`); errors are swallowed.
  - `logLogout(Integer)` – sets `logout_time` on the latest open successful login.
  - `findAll()` – all users ordered by username (no caller found in the current code).
- **Dependencies:** `JdbcTemplate`, `model.User`.

### `repository/CustomerRepository.java`
- **Purpose:** JDBC access to `corp.customer` and Phase 1 dashboard counts.
- **Important methods:**
  - `findById(Integer)` – customer by id.
  - `getDatabaseSummary(Integer customerId)` – device count, active devices, 30-day transaction count and approved amount, location count.
  - `getSystemSummary()` – active customers, total devices, total transactions, active users (ADMIN dashboard).
- **Dependencies:** `JdbcTemplate`, `model.Customer`.

### `reporting/AccessPolicyService.java`
- **Purpose:** Builds the `UserScope` for every reporting/chat/export/analytics request.
- **Main responsibility:** User id comes from the server-side session; role, customer and status are **re-read from
  the database on every request**. Allowed tables come from `app.reporting.role-tables.<ROLE>` (default: all catalog
  tables). Roles in `app.reporting.device-restricted-roles` (default `CUSTOMER`, not set in properties) are limited
  to devices in `app_user.user_device_access` for their customer.
- **Important methods:**
  - `resolve(HttpSession)` – throws `ReportAccessException` 401 `NOT_AUTHENTICATED` when no `userId` in session.
  - `resolve(int userId)` – throws 403 `INACTIVE_USER` / `NO_CUSTOMER` (non-admin without customer); returns `UserScope`.
- **Dependencies:** `UserRepository`, `JdbcTemplate`, `SchemaMetadataService`, Spring `Environment`.

### `reporting/UserScope.java` (record)
- **Fields:** `userId`, `username`, `role`, `customerId`, `allowedTables` (qualified names), `allowedDeviceIds` (`null` = no device restriction).
- **Important methods:** `isAdmin()`.

### Models / DTOs
| File | Purpose |
|---|---|
| `model/User.java` | Lombok data class for `app_user.app_user` (incl. `passwordHash`); helpers `isAdmin()`, `isActiveUser()`. |
| `model/Customer.java` | Lombok data class for `corp.customer`. |
| `model/dto/LoginRequest.java` | `username`, `password` request body. |
| `model/dto/UserInfoResponse.java` | Safe user info (id, username, full name, email, role, customerId, customerName, status) – no password/hash. |
| `model/dto/ApiResponse.java` | Generic JSON wrapper `{success, message, data, error}`; factories `success(data)`, `success(data, message)`, `error(code)`, `error(code, message)`. |

### Roles & demo users
Demo users are created by `database/sample-data.sql`: `admin_user` (ADMIN), `techpay_manager` (BUSINESS, customer 1),
`techpay_customer` (CUSTOMER, customer 1), `retail_manager` (MANAGER, customer 2), `restaurant_owner` (BUSINESS,
customer 3), `gas_operator` (CUSTOMER, customer 4). All use the documented demo password – see
`docs/DEMO_USERS_GUIDE.md`.

---

## 6. Page controllers & UI templates

### `controller/PageController.java`
- **Purpose:** Returns Thymeleaf views.
- **Important methods:**
  | Method | Route | Behaviour |
  |---|---|---|
  | `login()` | `GET /login` | `login.html` |
  | `home(HttpSession)` | `GET /home` | `home.html` (landing page after login: central Application / Report search); redirect to `/login` if not logged in |
  | `dashboard(HttpSession)` | `GET /dashboard` | redirect to `/login` if no `userId` in session |
  | `chat(report, name, ask)` | `GET /chat` | former AI Chat page: redirects to `/home?mode=report` (Report Search), forwarding `report`, `name` and `ask` |
  | `analytics(HttpSession)` | `GET /analytics` | redirect to `/login` if not logged in, to `/home` if role ≠ ADMIN (SecurityConfig already returns 403 for non-admins) |
  | `pocInfo()` | `GET /poc-info` | `poc-info.html` (public) |
  | `root(HttpSession)` | `GET ""`, `GET /` | redirect to `/home` when logged in, otherwise to `/login` |

### `controller/DashboardApiController.java` (`/api/dashboard`)
- **Important methods:** `getDashboardSummary(HttpSession)` – `GET /api/dashboard/summary`; ADMIN → system summary,
  others → their customer summary; error `NO_CUSTOMER` if no customer.
- **Dependencies:** `DashboardService` (→ `CustomerRepository`).

### `service/DashboardService.java`
- **Important methods:** `getCustomerDashboard(Integer)`, `getSystemDashboard()` – thin wrappers over `CustomerRepository`.

### Templates (`src/main/resources/templates/`)
| File | Purpose | Calls / behaviour |
|---|---|---|
| `login.html` | Login page: light background with subtle animated accents, white card with icon inputs, show-password toggle and demo-account chips (fill the form only). | Inline script `POST /sltalk/api/login`, then redirects to `/sltalk/home`; `showAlert()` helper; link to `poc-info`. |
| `home.html` | Chatbot-style home with one search bar and two modes. **Application Search**: centred search, then Google-style grouped results (`?mode=app&q=...`). **Report Search** (`?mode=report`): the AI report chat – Recent Questions panel (shown by default as a right-side panel on screens ≥ 992px, above the conversation on smaller screens; can be hidden), example requests, Filters / Recent questions / New conversation tools, conversation area (`chatMessages`); once the conversation starts the search bar docks at the bottom. Keeps the element ids used by `chat.js` (`historyPanel`, `historyList`, `aiModelInfo`, `filterFrom`, `filterTo`, `filterStatus`, `micBtn`). | Loads Bootstrap, Chart.js 4.4.1 (CDN), `chat.js` and `search.js`. |
| `fragments/navbar.html` | Minimal header for signed-in pages: logo (→ `/home`), Search button, user menu (Home, Project Info, Logout). No module menus or separate AI Chat entry; pages are reached through the Home search. | Loads `/sltalk/static/js/navbar.js` (user name/role, `logout()`). |
| `dashboard.html` | Welcome page with user id, role, customer, summary cards. | Inline script: `GET /api/user-info`, `loadDashboardSummary()` → `GET /api/dashboard/summary`, `logout()` → `POST /api/logout`. |
| `chat.html` | AI chat page in three columns: collapsible left panel "Instant Reports" (`reportsPanel` / `reportList`), center chat, collapsible right panel "Recent Questions" (`historyPanel` / `historyList`); AI model info (`aiModelInfo`), filters (`filterFrom`, `filterTo`, `filterStatus`), message area (`chatMessages`), question form (`askForm`, `questionInput`, `charCount`). Panel open/closed state is kept in `localStorage`. | Loads Bootstrap, Chart.js 4.4.1 (CDN) and `/sltalk/static/js/chat.js`. `?report=<id>&name=...` runs an instant report (no AI call); `?ask=<text>` prefills the question box without sending it. |
| `analytics.html` | ADMIN Query Analytics page: period selector (`days`: 7/30/90/all), KPI cards (total, successful, failed/rejected, active users, avg AI answer time, avg SQL-only time) and tables (popular reports, AI provider usage, answer source, failure reasons, export jobs). | Inline script (`th:inline="none"`): `load()` → `GET /sltalk/api/admin/analytics?days=N`, helpers `ms()`, `table()`, `logout()`. |
| `poc-info.html` | Static public project information page (overview, features, tech stack, schemas, phases, completed/partial/pending work, last updated). | No API calls besides Bootstrap. |

### Static assets
#### `static/js/search.js`
- **Purpose:** Home search with two modes. **Application Search** (no AI calls) matches pages and actions in the
  browser (admin-only entries hidden for non-admins), instant reports, and customers / devices / transactions / locations
  from `GET /api/search` (same user scope as the record pages). A record-type word in the query ("customer ABC",
  "Chennai location", "device 12") limits record results to that type. Records open their read-only details
  (`/customers?id=N`, ...). Searching happens only on submit (Enter or the search button; there is no as-you-type
  dropdown) and opens the grouped results view (`?mode=app&q=...`).
- **Report Search** (`?mode=report`) is the AI report chat: submitting sends the question to `chat.js` (`window.SLTalkChat.ask`
  → `POST /api/chat/ask`). Instant reports that fully match a sent question (ranked in the browser by name, category and
  description) are shown next to the AI answer and run via `POST /api/reports/{id}/run` (no AI call). Recent Questions
  are shown every time Report Search is opened (`chat.showHistory(true)`). `?report=<id>&name=...` runs an instant
  report and `?ask=<text>` prefills a question (not sent).
- Keyboard: Enter submits, Esc clears, ←/→ switch the mode tabs; browser back/forward, loading, error and no-results states.

#### `static/js/chat.js`
- **Purpose:** AI report chat shown in Home > Report Search (API base `'/sltalk/api'`; on HTTP 401 it redirects to login).
  The question input is the Home search bar; `search.js` calls it through `window.SLTalkChat`
  (`ask(question, related)`, `runReport(id, name)`, `reports` promise of `GET /api/reports`, `isBusy()`).
- **Important functions:**
  | Function | Description |
  |---|---|
  | `api(path, options)` | `fetch` wrapper with JSON headers; handles 401 and `success=false`. |
  | `filters()`, `filterQuery()`, `resetFilters()` | Read/serialize/reset the date-range and status filters (Filters button). |
  | `ask(question, related)` | `POST /api/chat/ask` and renders the answer; shows matching instant reports as one-click chips. |
  | `runReport(reportId, name)` | `POST /api/reports/{id}/run`. |
  | `renderAnswer(container, r)` | Renders title, explanation, model badge, status/errors, export buttons (the generated SQL is not shown to users). |
  | `renderPage(container, page)` | Renders a result table page with pagination. |
  | `renderChart(container, page)` | Draws a Chart.js bar/line/pie chart (destroys previous instance). |
  | `loadPage(chatId, page, pageSize)`, `applyFilters(chatId)` | `GET /api/chat/{chatId}/results` with filters. |
  | `exportReport(chatId, format, btn)` | `GET /api/chat/{chatId}/export?format=…`; downloads the file or, on HTTP 202, starts polling. |
  | `pollExport(jobId)` | Polls `GET /api/exports/{jobId}`, then downloads from `/api/exports/{jobId}/download`. |
  | `clearConversation()` | `POST /api/chat/conversation/new` (New conversation button). |
  | `showHistory(show)`, `toggleHistory()` | Show/hide the Recent Questions panel (visible by default; × hides it, the Recent questions button toggles it). |
  | `loadHistory()` | `GET /api/chat/history?limit=15` into the Recent Questions panel; clicking an entry prefills the search bar. |
  | `loadAiInfo()` | `GET /api/chat/ai-info` – shows the primary provider/model. |
  | `initVoice()` | Browser speech recognition into the search bar (mic button). |
  | `modelBadge(r)`, `formatCell(v, type)`, `esc(v)` | Rendering helpers (HTML escaping, number/date formatting). |

#### `static/css/style.css`
- **Purpose:** Shared stylesheet. Sections: Global (DMS/SeedLive blue palette variables), Login Page, Navigation,
  Home search and results, Report Search chat, Card, Dashboard, Alert, Form, Button, Table, Utility, Responsive Design,
  Print, Loading Spinner, Animation, Custom Scrollbar.

---

## 7. AI provider integration (`ai` package)

| File | Type | Purpose |
|---|---|---|
| `AiProvider.java` | interface | Replaceable provider: `name()`, `model()`, `isConfigured()` (API key present), `complete(systemPrompt, userPrompt)` → raw text; throws `AiException`. |
| `AiException.java` | exception | Network/timeout/HTTP/JSON problems from AI calls. |
| `AiHttpClients.java` | utility | `create(connectTimeoutSeconds, trustStoreType)` builds a `java.net.http.HttpClient`; `resolveType()` maps `auto` → `Windows-ROOT` on Windows (avoids PKIX errors behind TLS-inspecting proxies), `default` → JVM cacerts, or any KeyStore type. |
| `OpenAiCompatibleProvider.java` | class | Any OpenAI-compatible `/chat/completions` API (used for **groq**, **openai**, **nvidia**). Sends `temperature 0`, `max_tokens`, optional `reasoning_effort`, optional `response_format: json_object`; `Authorization: Bearer` header. `summarize(body)` trims error bodies to 200 chars. |
| `GeminiProvider.java` | class | Google Gemini `models/{model}:generateContent` with `x-goog-api-key` header, `responseMimeType: application/json`; skips "thought" parts. |
| `AiProviderChain.java` | class | Ordered provider list (primary then fallbacks). |
| `AiProviderConfig.java` | `@Configuration` | Builds the `AiProviderChain` bean from properties. |
| `AiReportResponse.java` | record | Expected AI JSON: `answerable`, `understanding`, `sql`, `title`, `explanation`, `chartType`, `clarification`; `normalizeChartType()` → `table|bar|line|pie`. |
| `AiResponseParser.java` | utility | `parse(raw)` strips `<think>` blocks / markdown, extracts the outer `{…}`, maps to `AiReportResponse`, trims long fields; `answerable` becomes false if no SQL. |
| `PromptBuilder.java` | utility | Builds system and user prompts (see Section 8). |

### `AiProviderChain` – important methods
- `complete(systemPrompt, userPrompt, Function<String,T> parser)` – tries each **configured** provider in order;
  a provider "fails" if it throws `AiException` (including parser failures); returns `Completion<T>` or throws
  `AiException` with all failure messages (or "No AI provider is configured" if none have keys).
- `Completion<T>` record – `value`, `provider`, `model`, `failures`; `label()` = `provider:model`, `usedFallback()`.
- `primaryLabel()` – first configured provider label (used when all fail).
- `describe()` – safe list (`order`, `role`, `provider`, `model`, `configured`) for `/api/chat/ai-info`; never includes keys.
- `providers()` – the provider list.

### `AiProviderConfig.aiProviderChain(...)` – behaviour
- Provider names = `app.ai.provider` + `app.ai.fallback-providers` (deduplicated, lower-case).
- Each provider's `app.ai.<name>.model` may be **comma-separated**; each model becomes its own chain entry.
- Default models in code: gemini `gemini-3.8-flash`, nvidia `deepseek-ai/deepseek-v4.1-flash`, groq `openai/gpt-oss-20b`, other `gpt-4o-mini`.
- Per-provider read timeout `app.ai.<name>.read-timeout-seconds`, falling back to `app.ai.read-timeout-seconds`.
- Groq: JSON mode, `app.ai.groq.max-tokens` (default 1024), `app.ai.groq.reasoning-effort` (default `low`).
- OpenAI: JSON mode on; NVIDIA: JSON mode off.
- Unknown provider name → `IllegalStateException` at startup.

---

## 8. Schema discovery & prompt building

### `reporting/SchemaMetadataService.java`
- **Purpose:** Reads approved schemas/tables/columns and their `COMMENT` descriptions from `information_schema` +
  `obj_description`/`col_description`, builds a cached `ReportingCatalog`.
- **Main responsibility:**
  - Only schemas in `app.reporting.approved-schemas` (`corp,location,device,txn`).
  - Drops columns listed in `app.reporting.blocked-columns` (card last four, customer email/phone).
  - **Fail closed:** skips tables without a `customer_id` or `device_id` column (cannot be scoped).
  - For categorical columns (`status`, `transaction_status`, `card_type`, `entry_mode`, `device_type`, `event_type`)
    loads up to 20 distinct values to help the AI write correct filters.
- **Important methods:** `getCatalog()` (lazy, double-checked cached load), `refresh()` (clears the cache),
  private `load()`, `distinctValues(table, column)`.
- **Dependencies:** `JdbcTemplate`.

### `reporting/ReportingCatalog.java`
- **Purpose:** Immutable catalog of approved tables (`TableInfo`) and columns (`ColumnInfo`).
- **Important methods:** `table(qualifiedName)`, `resolveUnqualified(tableName)` (only if unique), `tableNames()`,
  `allColumnNames()`, `isEmpty()`, `describe(allowedTables)` (compact text schema for the prompt, incl. categorical
  values), `columnList(qualifiedName)` (used to build scope CTEs). `TableInfo.qualifiedName()`, `TableInfo.hasColumn()`.

### `ai/PromptBuilder.java`
- **Purpose:** Builds prompts containing **only** schema descriptions, report examples and the question (no credentials, no row data).
- **Important methods:**
  - `systemPrompt(ReportingCatalog, UserScope, List<Example>)` – rules (single SELECT, schema-qualified tables,
    no customer filter / no LIMIT because Java enforces them, ORDER BY, chart type guidance, "failed" and "sales"
    definitions, refuse sensitive questions with `answerable:false`), today's date, user role description,
    `catalog.describe(scope.allowedTables())`, and reference examples.
  - `userPrompt(question, List<PreviousTurn>)` – adds previous questions/SQL for follow-up questions.
  - Records `PreviousTurn(question, title, sql)` and `Example(name, description, question, sql)`.

### Example selection
`PredefinedReportService.relevantExamples(question, allowedTables, limit)` picks up to `app.ai.max-examples` (4)
reports by keyword overlap, restricted to reports using only tables the user may access (see Section 11).

---

## 9. SQL generation and validation (security core)

### `reporting/SqlSafetyValidator.java`
- **Purpose:** Validates AI-generated / predefined SQL with **JSqlParser** and rewrites it so it can only read the
  logged-in user's data. A failure throws `SqlValidationException` and the query is never executed.
- **Checks in `validate(sql, scope, catalog, filters)`:**
  1. Non-empty (`EMPTY_SQL`), length ≤ max (`SQL_TOO_LONG`), no `$$` (`INVALID_SQL`), non-admin must have customer (`NO_CUSTOMER`).
  2. Exactly one statement (`MULTIPLE_STATEMENTS`), parseable (`INVALID_SQL`), must be `Select` (`NOT_SELECT`).
  3. AST inspection (`Inspector`, extends JSqlParser `TablesNamesFinder`): no recursive CTE, no `SELECT INTO`,
     no `FOR UPDATE/SHARE` (`FORBIDDEN_KEYWORD`), no schema-qualified functions; CTE names starting with `dt_` are reserved.
  4. Every table must be in the catalog (`RESTRICTED_TABLE`) and in the user's `allowedTables` (`UNAUTHORIZED_TABLE`);
     cross-database names rejected.
  5. Every column must be an approved column, alias, CTE name or special identifier (`RESTRICTED_COLUMN`).
  6. Every function must be on `ALLOWED_FUNCTIONS` (`DISALLOWED_FUNCTION`) – blocks `pg_sleep`, `pg_read_file`, `set_config`, etc.
  7. Non-admins cannot compare `customer_id` with another customer's literal (`UNAUTHORIZED_CUSTOMER`).
  8. `lexicalSafetyNet()` – second independent check on the deparsed SQL: forbidden keywords, `pg_*` objects,
     system schemas (`pg_catalog`, `information_schema`, `public`, `app`, `app_user`, …), any un-rewritten approved table.
- **Rewrite:** each table reference becomes a Java-generated CTE `dt_<schema>_<table>` exposing only approved columns
  and rows filtered by `customer_id = <session customer>` (or `device_id IN (SELECT … WHERE customer_id = …)` for
  tables without `customer_id`), `device_id IN (<assigned devices>)` for device-restricted roles, plus UI filters
  (date column per table: `transaction_time`, `settlement_date`, `event_time`; status column `transaction_status`).
  Final SQL: `WITH dt_… AS (…) SELECT * FROM (<rewritten query>) AS dt_result`.
- **Important methods:** `validate(...)`, `parseSingleStatement()`, `lexicalSafetyNet()`, `buildScopeCtes()`,
  `scopeName(qualifiedTable)`, `clean(identifier)`; inner class `Inspector` (visitors for `WithItem`, `PlainSelect`,
  `SetOperationList`, `ParenthesedSelect`, `Table`, `Column`, `SelectItem`, `Function`, `AnalyticExpression`,
  `TableFunction`, `EqualsTo`, `InExpression`).
- **Dependencies:** `ReportingCatalog`, `UserScope`, `ReportFilters`, JSqlParser. Created by `ReportingConfig`.

### `reporting/SecureQuery.java` (record)
`originalSql` (as generated), `executableSql` (scoped, no LIMIT), `tables` (approved tables referenced). Only
`SecureQuery` objects are accepted by the executor.

### `reporting/ReportFilters.java` (record)
`dateFrom`, `dateTo`, `status`. `of(dateFrom, dateTo, status)` parses/validates (`INVALID_FILTER` for bad dates,
from > to, unknown status); allowed statuses `APPROVED, DECLINED, FAILED, REFUNDED`. `none()`, `isEmpty()`.

### `reporting/ReportExecutionService.java`
- **Purpose:** Executes validated queries safely.
- **Main responsibility:** Every execution runs inside a `TransactionTemplate` that issues
  `SET TRANSACTION READ ONLY`, `SET LOCAL statement_timeout = '<n>s'`, `SET LOCAL ROLE <app.reporting.read-only-role>`;
  JDBC query timeout = timeout + 5 s; role name validated by regex.
- **Important methods:**
  - `executePage(SecureQuery, page, pageSize)` – `count(*)` over the query, then `LIMIT/OFFSET` page → `ReportPage`.
  - `count(SecureQuery)` – total rows (used to decide direct vs. background export).
  - `stream(SecureQuery, maxRows, RowSink)` – streams rows (fetch size 500) to a sink for exports.
  - `normalizePageSize(Integer)` – default 25, max 100.
  - `runReadOnly()`, `friendlyMessage()` (timeout / missing role / permission denied → user-friendly text),
    `readMetadata()`, `readRow()`, `typeOf()` (`number|date|text`), `convert()` (timestamps → `yyyy-MM-dd HH:mm:ss`).
  - Interface `RowSink { start(columns, types); row(values); }`.
- **Dependencies:** `DataSource`, `PlatformTransactionManager`.

### `reporting/ReportPage.java` (record)
`columns`, `columnTypes`, `rows`, `totalRows`, `page`, `pageSize`, `totalPages`.

### Exceptions in `reporting`
| Class | Meaning |
|---|---|
| `SqlValidationException` | SQL failed safety/permission checks; has `getCode()` (e.g. `RESTRICTED_TABLE`). |
| `ReportAccessException` | Auth/authorization/not-found in reporting APIs; carries `HttpStatus` + code. |
| `ReportExecutionException` | Validated query failed at runtime (timeout, DB error, role not configured). |

---

## 10. Chat flow, chat history and saved user context (`chat` package)

### `chat/ChatService.java`
- **Purpose:** Orchestrates a question: *question → (cached SQL | AI) → Java validation + scope → read-only execution → history*.
- **Important methods:**
  - `ask(UserScope, rawQuestion, ReportFilters, pageSize)` –
    1. validates question (empty → `EMPTY_QUESTION`, > 1000 chars → `QUESTION_TOO_LONG`);
    2. gets the current conversation id; detects follow-ups (`isFollowUp`, regex on words like "and", "those", "same");
    3. **SQL reuse:** for non-follow-ups, reuses SQL from an identical successful question by the same user within
       7 days (`ChatHistoryRepository.findReusable`), re-validated with the current scope (source `CACHE`);
    4. otherwise calls `AiProviderChain.complete(...)` with `PromptBuilder` prompts and `AiResponseParser::parse`;
    5. handles `AI_ERROR`, `NOT_ANSWERABLE`, `REJECTED` (validation failure) outcomes;
    6. executes via `execute(...)` (source `AI`).
  - `runReport(UserScope, reportId, params, filters, pageSize)` – renders a predefined report, validates it
    (unauthorized/restricted table → 403), executes and stores it as source `PREDEFINED`.
  - `availableReports(UserScope)` – predefined reports whose default SQL validates for the user.
  - `results(UserScope, chatId, page, pageSize, filters)` – re-validates stored SQL and returns another page.
  - `secureQueryFor(UserScope, chatId, filters)` – validated query of the user's own successful answer (used by results and export).
  - `history(UserScope, conversationId, limit)`, `findOwned(UserScope, chatId)` (404 `CHAT_NOT_FOUND`).
  - `aiProviders()` – `AiProviderChain.describe()`.
  - private `execute(...)` / `failure(...)` – write `app.chat_history` rows and update user context.
  - static helpers `dbProvider()` (compact `provider:model` ≤ 30 chars), `isFollowUp()`, `normalize()`; `topicOf()`.
- **Dependencies:** `AiProviderChain`, `SchemaMetadataService`, `SqlSafetyValidator`, `ReportExecutionService`,
  `PredefinedReportService`, `ChatHistoryRepository`, `UserAiContextService`, `PromptBuilder`, `AiResponseParser`.

### `chat/ChatStatus.java` (enum)
`SUCCESS→EXECUTED`, `REJECTED→INVALID_SQL`, `NOT_ANSWERABLE/AI_ERROR/EXECUTION_ERROR→FAILED` (DB constraint values);
detailed code stored in `error_message` as `"[CODE] message"`. Method `dbValue()`.

### `chat/ChatResult.java` (record)
API response: `chatId`, `conversationId`, `question`, `status`, `source` (AI/CACHE/PREDEFINED), `aiProvider`,
`understanding`, `title`, `explanation`, `chartType`, `sql`, `errorCode`, `message`, `page` (first `ReportPage`),
`durationMs`, `aiFailures`.

### `chat/ChatRecord.java` (record)
One `app.chat_history` row (question, SQL, metadata – never report data). `isSuccess()`.

### `chat/ChatHistoryRepository.java`
- **Purpose:** JDBC access to `app.chat_history`; every read filtered by `user_id`.
- **Important methods:** `insert(ChatRecord)` (returns generated `chat_id`), `findForUser(chatId, userId)`,
  `listForUser(userId, conversationId, limit)` (ordered ascending within a conversation, otherwise newest first),
  `findReusable(userId, normalizedQuestion, maxAgeDays)` (latest `EXECUTED` AI/CACHE answer to the same normalized question).

### `chat/UserAiContextService.java`
- **Purpose:** Compact per-user AI context in `app.user_ai_context` (JSONB): `conversationId`, `preferences`
  (`lastChartType`, `lastFilters`), `recentTurns` (max 3; question/title/sql), `topics` counters, `updatedAt`.
- **Important methods:** `get(userId)`, `currentConversationId(userId)`, `startNewConversation(userId)` (new UUID,
  clears turns), `recentTurns(userId, conversationId, max)`, `recordTurn(...)`, `saveFilters(userId, from, to, status)`,
  private `save()` (UPSERT), static `sanitize(text, max)` – masks 12–19 digit numbers, API-key-like tokens and
  `password=…` phrases, truncates text (300 chars) / SQL (1500 chars).
- **Dependencies:** `JdbcTemplate`, `ObjectMapper`.

### `controller/ChatApiController.java` (`/api/chat`)
| Endpoint | Method | Description |
|---|---|---|
| `POST /api/chat/ask` | `ask(AskRequest, session)` | Body `AskRequest(question, dateFrom, dateTo, status, pageSize)` → `ChatResult`. |
| `GET /api/chat/{chatId}/results` | `results(...)` | Page/filter a stored answer; also saves filters to user context. |
| `GET /api/chat/history` | `history(conversationId, limit, session)` | Own history; `conversationId=current` resolves the active one; limit clamped 1–200. |
| `POST /api/chat/conversation/new` | `newConversation(session)` | Starts a new conversation. |
| `GET /api/chat/ai-info` | `aiInfo(session)` | Provider chain description. |
| `GET /api/chat/context` | `context(session)` | The user's own saved AI context JSON. |

Dependencies: `AccessPolicyService`, `ChatService`, `UserAiContextService`.

---

## 11. Predefined reports

### `reports/PredefinedReport.java` (record)
`id`, `category`, `name`, `description`, `parameters` (`Parameter(name, type, defaultValue, description)` – integers),
`examples`, `chartType`, `sqlTemplate`.

### `reports/PredefinedReportService.java`
- **Purpose:** Loads `classpath*:predefined-reports/**/*.sql` at startup and serves them as runnable reports and AI examples.
- **Important methods:**
  - `load()` (`@PostConstruct`) – id = `<category>-<file name without numeric prefix>`, e.g. `payment-daily-sales-trend`.
  - `parse(id, category, content)` – reads header lines `-- name:`, `-- description:`, `-- param: name | integer | default | description`, `-- example:`, `-- chart:`.
  - `all()`, `find(id)`.
  - `renderSql(report, values)` – substitutes `:param` with validated integers (1–3650; `INVALID_PARAMETER`), leaving `::casts` intact.
  - `relevantExamples(question, allowedTables, limit)` – keyword-overlap scoring (`tokens()`, stop-words), only reports using allowed tables (`usesOnly()`).

### `controller/ReportApiController.java` (`/api/reports`)
- `list(session)` – `GET /api/reports`: reports available to the user as `ReportInfo` (no SQL template).
- `run(reportId, RunRequest, session)` – `POST /api/reports/{reportId}/run`; body `RunRequest(params, dateFrom, dateTo, status, pageSize)` → `ChatResult`.

### Report files (`src/main/resources/predefined-reports/`)
| File | Name | Params (default) | Chart |
|---|---|---|---|
| `customer/01-revenue-by-customer.sql` | Revenue by customer | `days` (30) | bar |
| `customer/02-locations-and-devices.sql` | Locations and devices per customer | – | table |
| `device/01-device-status-summary.sql` | Device status summary | – | bar |
| `device/02-offline-devices.sql` | Devices not communicating | `hours` (24) | table |
| `device/03-device-events.sql` | Device events by type | `days` (30) | bar |
| `payment/01-daily-sales-trend.sql` | Daily sales trend | `days` (30) | line |
| `payment/02-failed-transactions-by-location.sql` | Failed transactions by location | `days` (30) | bar |
| `payment/03-sales-by-card-type.sql` | Sales by card type | `days` (30) | pie |
| `payment/04-decline-reasons.sql` | Top decline reasons | `days` (30) | bar |
| `settlement/01-settlement-summary.sql` | Settlement summary by status | `days` (30) | bar |
| `settlement/02-weekly-net-settlement.sql` | Weekly net settlement | `weeks` (12) | line |

Because `CUSTOMER` users may only query `txn.payment_transaction`, `device.device`, `device.device_status_log`,
`location.location`, reports touching `txn.settlement` or `corp.customer` are hidden from them (verified by
`LoginAndAccessIntegrationTest.customerDoesNotSeeSettlementReports`).

---

## 12. PDF/Excel export & export jobs (`export` package)

### `export/ExportFormat.java` (enum)
`PDF` (`pdf`, `application/pdf`, default) and `EXCEL` (`xlsx`). `from(String)` accepts `pdf`, `excel`, `xlsx`
(otherwise `IllegalArgumentException`); `extension()`, `contentType()`.

### `export/ReportExportService.java`
- **Purpose:** Writes real query results (streamed from the DB) to PDF (OpenPDF) or Excel (Apache POI `SXSSFWorkbook`, 200-row window).
- **Important methods:**
  - `write(format, SecureQuery, title, generatedFor, OutputStream)` – returns rows written (max `app.export.max-rows`, 50 000).
  - private `writePdf(...)` – A4 landscape, title + "generated … for user (role)", header row, table flushed every 500 rows, row count footer.
  - private `writeExcel(...)` – title row, generated-for row, bold header, numeric/date cells.
  - `fileName(title, format)` – slug of title (≤ 60 chars) + date + extension.
  - `maxRows()`.
- **Dependencies:** `ReportExecutionService.stream(...)`.

### `export/ExportJobService.java`
- **Purpose:** Background export jobs for large reports.
- **Main responsibility:** Fixed thread pool (2 threads); files written to `app.export.directory` as `<jobId>.<ext>`;
  in-memory job map plus persistence in `app.export_job`; jobs older than **1 hour** are cleaned up (file deleted,
  row marked `EXPIRED`) when a new job is submitted.
- **Important methods:**
  - `submit(userId, chatId, format, SecureQuery, title, generatedFor)` – creates `Job` (status `RUNNING`), runs export async, updates to `COMPLETED`/`FAILED`.
  - `recordDirect(userId, chatId, format, title, rows, error)` – records a streamed (direct) export (`delivery = DIRECT`).
  - `get(jobId, userId)` – memory first, then `app.export_job`; only the owner can see it (404 `EXPORT_NOT_FOUND`).
  - `fileOf(Job)` – returns the file if completed and present, else 409 `EXPORT_NOT_READY`.
  - `cleanup()`, `shutdown()` (`@PreDestroy`), private `fromRow(...)`.
  - Nested `Job` class (`getId`, `getStatus`, `getRows`, `getError`, `getFileName`, `getFormat`, `getCreatedAt`) and enum `Status { RUNNING, COMPLETED, FAILED, EXPIRED }`.
- **Dependencies:** `ReportExportService`, `ExportJobRepository`.

### `export/ExportJobRepository.java`
- **Purpose:** Best-effort persistence in `app.export_job` (created by `database/phase3-migration.sql`); DB errors are
  logged as warnings and exports still work.
- **Important methods:** `insert(jobId, userId, chatId, reportName, format, status, delivery)`,
  `complete(jobId, status, rows, fileLocation, error)`, `markExpired(jobId)`, `findForUser(jobId, userId)`, private `run(action, Runnable)`.

### `controller/ExportApiController.java` (`/api`)
| Endpoint | Method | Description |
|---|---|---|
| `GET /api/chat/{chatId}/export?format=pdf|excel&dateFrom&dateTo&status&async` | `export(...)` | Loads the user's own chat item, re-validates SQL with filters, counts rows. If `async=true` or rows > `app.export.async-threshold-rows` (2000) → **202 Accepted** with `{jobId, status, rows}`; otherwise streams the file directly and records it via `recordDirect`. |
| `GET /api/exports/{jobId}` | `status(...)` | Job status (owner only). |
| `GET /api/exports/{jobId}/download` | `download(...)` | Downloads a completed job file (owner only). |

Dependencies: `AccessPolicyService`, `ChatService`, `ReportExportService`, `ExportJobService`, `ReportExecutionService`.

---

## 13. Query Analytics (ADMIN only)

### `analytics/QueryAnalyticsService.java`
- **Purpose:** Basic analytics from `app.chat_history` (and `app.export_job` when present).
- **Important methods:**
  - `summary(int days)` – period clamped to 0–3650 (0 = all time). Returns a map with:
    - `totals` – `total_questions`, `successful`, `failed`, `active_users`, `avg_ai_answer_ms` (successful `AI` answers,
      end-to-end), `avg_sql_execution_ms` (successful `CACHE`/`PREDEFINED` answers – no AI call);
    - `bySource` – questions/successful/avg ms per source;
    - `failureReasons` – code parsed from `error_message` `[CODE]` prefix, else `query_status`;
    - `popularReports` – top 10 executed report titles;
    - `providerUsage` – per `ai_provider` for AI-sourced rows;
    - `exports` – `exportStats()`.
  - private `exportStats(int)` – counts by `export_format`/`job_status`; returns an empty list if `app.export_job` is missing.
- **Dependencies:** `JdbcTemplate`.

### `controller/AnalyticsApiController.java` (`/api/admin/analytics`)
- `summary(days=30, session)` – `GET /api/admin/analytics?days=N`. Protected twice: `SecurityConfig` (`/api/admin/**`
  requires `ROLE_ADMIN`) and an in-method check of the DB role via `AccessPolicyService` (403 `ADMIN_ONLY`).
- Dependencies: `AccessPolicyService`, `QueryAnalyticsService`.

UI: `templates/analytics.html`, route `/analytics` (`PageController.analytics`), reachable for ADMIN through
Application Search ("Query Analytics").

---

## 14. Exception handling

### `controller/ApiExceptionHandler.java`
- `@RestControllerAdvice(assignableTypes = {ChatApiController, ReportApiController, ExportApiController})` – maps
  exceptions to `ApiResponse` errors (messages never contain secrets):
  | Exception | HTTP | Error code |
  |---|---|---|
  | `ReportAccessException` | exception's status | exception's code |
  | `SqlValidationException` | 400 | exception's code |
  | `ReportExecutionException` | 422 | `EXECUTION_ERROR` |
  | `AiException` | 502 | `AI_ERROR` |
  | `DataAccessException` | 500 | `DATABASE_ERROR` (logged) |
  | `IllegalArgumentException` | 400 | `BAD_REQUEST` |
- Note: `AnalyticsApiController`, `AuthApiController` and `DashboardApiController` are **not** in `assignableTypes`, so
  exceptions there use Spring's default error handling (`server.error.include-message=always`). For analytics, non-admins
  are normally already blocked with 403 by `SecurityConfig`.
- Within `ChatService`, AI/validation/execution failures of `ask` are usually returned as a `ChatResult` with a
  non-success `status` (and stored in history) rather than thrown.

---

## 15. Database scripts (`database/`)

Run order (from the script headers): `neon-schema.sql` → `sample-data.sql` → `permissions.sql` → `phase2-migration.sql` → `phase3-migration.sql`.

| File | Purpose | Main content |
|---|---|---|
| `neon-schema.sql` | Base schema. | Schemas `corp`, `location`, `app_user`, `device`, `txn`, `app`; tables `corp.customer`, `location.location`, `app_user.app_user`, `app_user.user_login_history`, `app_user.user_device_access`, `device.device`, `device.device_status_log`, `txn.payment_transaction`, `txn.settlement`, `app.chat_history`; indexes, comments, FK; views `app_user.v_user_details`, `device.v_device_summary`, `txn.v_transaction_summary`. |
| `sample-data.sql` | Demo data. | 5 customers, 15 locations, 6 demo users (BCrypt hashes), 60 devices, ~1,500 device events, ~10,000 transactions (last 120 days), settlements, user-device access, placeholder chat history, ~800 login records; verification counts. |
| `permissions.sql` | Read-only reporting role. | `CREATE ROLE datatalk_ro`, `USAGE`/`SELECT` on `corp`, `location`, `device`, `txn`, default privileges, verification queries; no access to `app`. |
| `phase2-migration.sql` | AI reporting (idempotent, run as DB owner). | Adds `app.chat_history` columns (`conversation_id`, `ai_interpretation`, `report_title`, `explanation`, `chart_type`, `row_count`, `error_message`, `ai_provider`, `source`, `duration_ms`) + indexes; creates `app.user_ai_context` (JSONB); grants app role `user_datatalk` access to `app`; re-applies `datatalk_ro` SELECT-only and revokes `app`/`app_user`; `ALTER ROLE datatalk_ro` read-only + 30 s timeout; `GRANT datatalk_ro TO user_datatalk WITH INHERIT FALSE, SET TRUE` (PostgreSQL 16+); verification queries. |
| `phase3-migration.sql` | Export job persistence (idempotent). | Creates `app.export_job` (`job_id` PK, `user_id`, `chat_id`, `report_name`, `export_format` PDF/EXCEL, `job_status` RUNNING/COMPLETED/FAILED/EXPIRED, `delivery` DIRECT/BACKGROUND, `row_count`, `created_date`, `completed_date`, `file_location`, `error_message`), index, grants to `user_datatalk`, revoke from `datatalk_ro`. |
| `verify-demo-users.sql` | Manual verification. | Read-only queries checking users, customers, locations, devices, transactions, events, settlements, login/chat history, BCrypt hashes and orphan records. |
| `README.md` | Database setup guide. | Quick start, demo users, sample data, structure, security, troubleshooting. |

> Note: the comment on `app.chat_history.query_status` in `phase2-migration.sql` lists SUCCESS/REJECTED/…, but the code
> stores the Phase 1 constraint values (`EXECUTED`, `INVALID_SQL`, `FAILED`) – see `ChatStatus`.

---

## 16. Tests (`src/test/java/com/datatalk/datatalkai/`)

| File | Type | What it covers |
|---|---|---|
| `reporting/SqlSafetyValidatorTest.java` | Unit (no DB/AI) | Uses an in-memory `ReportingCatalog` mirroring the real schema. Tests: scoping to customer (`validSelectIsScopedToUsersCustomer`), safe CTEs, admin not customer-filtered but UI filters applied, CUSTOMER device restriction, device log scoped through devices, unqualified table resolution, garbage/multiple/DML/DDL/`SELECT INTO`/`FOR UPDATE` rejection, dangerous functions (`pg_sleep`, `pg_read_file`, `set_config`), restricted tables (`app.*`, `app_user.*`, `pg_catalog`, `information_schema`), subqueries in ORDER BY, sensitive columns, other-customer literals, role table access, reserved `dt_` CTE names, invalid filters. |
| `ai/AiProviderChainTest.java` | Unit | Stub providers: primary answers without fallback, fallback on timeout, fallback on non-JSON answer, unconfigured providers skipped, all failing, none configured, `describe()` never exposes keys, `dbProvider()` label fits 30 chars. |
| `chat/ChatServiceTestAccess.java` | Test helper | Exposes package-private `ChatService.dbProvider()` to other test packages. |
| `LoginAndAccessIntegrationTest.java` | `@SpringBootTest` + MockMvc | **Requires the Neon database and demo users.** Login returns identity/scope, wrong password rejected, APIs return 401 without login, ADMIN can open `/api/admin/analytics` and `/analytics`, non-admins get 403, CUSTOMER does not see settlement reports, logout ends the session. Uses the documented demo password (see `docs/DEMO_USERS_GUIDE.md`). Its Javadoc mentions `DB_USERNAME`, while `application.properties` uses `DB_USER`. |

---

## 17. Configuration & environment variables

Values below are the defaults from `application.properties`. **Secrets: only the variable name is shown.**

### Database
| Property | Env var | Default |
|---|---|---|
| `spring.datasource.url` | `DB_URL` | Neon pooler JDBC URL with `sslmode=require` (see file) |
| `spring.datasource.username` | `DB_USER` | `user_datatalk` |
| `spring.datasource.password` | `DB_PASSWORD` | *secret – set via env var / `.env`* |
| `app.reporting.read-only-role` | `REPORT_DB_ROLE` | `datatalk_ro` |

### AI providers
| Property | Env var | Default |
|---|---|---|
| `app.ai.provider` | `AI_PROVIDER` | `groq` |
| `app.ai.fallback-providers` | `AI_FALLBACK_PROVIDERS` | `gemini,nvidia` |
| `app.ai.groq.api-key` | `GROQ_API_KEY` | *secret* (empty) |
| `app.ai.groq.model` | `GROQ_MODEL` | `openai/gpt-oss-20b` |
| `app.ai.groq.base-url` | `GROQ_BASE_URL` | `https://api.groq.com/openai/v1` |
| `app.ai.groq.read-timeout-seconds` | `GROQ_READ_TIMEOUT_SECONDS` | `30` |
| `app.ai.groq.max-tokens` | `GROQ_MAX_TOKENS` | `1024` |
| `app.ai.groq.reasoning-effort` | `GROQ_REASONING_EFFORT` | `low` |
| `app.ai.openai.api-key` | `OPENAI_API_KEY` | *secret* (empty) |
| `app.ai.openai.model` | `OPENAI_MODEL` | `gpt-4o-mini` |
| `app.ai.openai.base-url` | `OPENAI_BASE_URL` | `https://api.openai.com/v1` |
| `app.ai.gemini.api-key` | `GEMINI_API_KEY` | *secret* (empty) |
| `app.ai.gemini.model` | `GEMINI_MODEL` | `gemini-3.8-flash` (comma-separated list allowed) |
| `app.ai.gemini.base-url` | `GEMINI_BASE_URL` | `https://generativelanguage.googleapis.com/v1beta` |
| `app.ai.gemini.read-timeout-seconds` | `GEMINI_READ_TIMEOUT_SECONDS` | `60` |
| `app.ai.nvidia.api-key` | `NVIDIA_API_KEY` | *secret* (empty) |
| `app.ai.nvidia.model` | `NVIDIA_MODEL` | `deepseek-ai/deepseek-v4.1-flash` |
| `app.ai.nvidia.base-url` | `NVIDIA_BASE_URL` | `https://integrate.api.nvidia.com/v1` |
| `app.ai.nvidia.read-timeout-seconds` | `NVIDIA_READ_TIMEOUT_SECONDS` | `90` |
| `app.ai.read-timeout-seconds` | `AI_READ_TIMEOUT_SECONDS` | `120` |
| `app.ai.trust-store-type` | `AI_TRUST_STORE_TYPE` | `auto` |
| `app.ai.connect-timeout-seconds` | – | `10` |
| `app.ai.max-examples` | – | `4` |

### Reporting & export
| Property | Env var | Default |
|---|---|---|
| `app.reporting.approved-schemas` | – | `corp,location,device,txn` |
| `app.reporting.blocked-columns` | – | `txn.payment_transaction.card_last_four,corp.customer.email,corp.customer.phone` |
| `app.reporting.statement-timeout-seconds` | – | `30` |
| `app.reporting.default-page-size` / `max-page-size` | – | `25` / `100` |
| `app.reporting.max-sql-length` | – | `6000` |
| `app.reporting.role-tables.CUSTOMER` | – | `txn.payment_transaction,device.device,device.device_status_log,location.location` (other roles: all approved tables) |
| `app.reporting.device-restricted-roles` | – | not set in file; code default `CUSTOMER` |
| `app.export.max-rows` | – | `50000` |
| `app.export.async-threshold-rows` | – | `2000` |
| `app.export.directory` | `EXPORT_DIR` | `${java.io.tmpdir}/datatalk-exports` |

`.env` (project root, git-ignored) may hold any of the variables above as `KEY=value` lines; real environment
variables take precedence. Never commit it.

---

## 18. How a request flows

### Login
1. `login.html` → `POST /sltalk/api/login` (permitted in `SecurityConfig`).
2. `AuthApiController.login` → `AuthenticationService.authenticate` → `UserRepository.findByUsername` + BCrypt
   `PasswordEncoder.matches` → `UserRepository.logLoginAttempt`; `CustomerRepository.findById` for the customer name.
3. Controller changes the session id, stores the Spring `SecurityContext` (`ROLE_<role>`) and session attributes.
4. Browser redirects to `/home` → `PageController.home` → `home.html` (central search; `search.js` → `GET /api/user-info`,
   `GET /api/reports`, and `GET /api/search` while typing).

### Ask a question
1. `chat.js ask()` → `POST /api/chat/ask` → `ChatApiController.ask`.
2. `AccessPolicyService.resolve(session)` → `UserScope` (DB re-check, role tables, device ids); `ReportFilters.of(...)`.
3. `ChatService.ask`: `UserAiContextService.currentConversationId` → cache check via `ChatHistoryRepository.findReusable`
   → else `SchemaMetadataService.getCatalog` + `PredefinedReportService.relevantExamples` + `PromptBuilder`
   → `AiProviderChain.complete` (Groq/Gemini/NVIDIA/OpenAI providers via `AiHttpClients`) → `AiResponseParser.parse`.
4. `SqlSafetyValidator.validate` → `SecureQuery` (scoped CTEs).
5. `ReportExecutionService.executePage` (read-only transaction, `SET LOCAL ROLE datatalk_ro`, timeout) → `ReportPage`.
6. `ChatHistoryRepository.insert` + `UserAiContextService.recordTurn` → `ChatResult` → `chat.js renderAnswer/renderPage/renderChart`.
   Further pages: `GET /api/chat/{chatId}/results` → `ChatService.results`.

### Run a predefined report
1. `chat.js reportsPromise` → `GET /api/reports` → `ReportApiController.list` → `ChatService.availableReports`
   (each report rendered + validated for the user's scope); offered as matching reports next to AI answers and in
   Application Search results.
2. Click → `POST /api/reports/{id}/run` → `ReportApiController.run` → `ChatService.runReport`
   → `PredefinedReportService.find/renderSql` → `SqlSafetyValidator.validate` → `ReportExecutionService.executePage`
   → history (`source = PREDEFINED`) → `ChatResult`.

### Export
1. `chat.js exportReport()` → `GET /api/chat/{chatId}/export?format=pdf|excel` → `ExportApiController.export`.
2. `AccessPolicyService.resolve` → `ChatService.findOwned` + `ChatService.secureQueryFor` (re-validation with current scope and filters)
   → `ReportExecutionService.count`.
3. Small: `ReportExportService.write` streams PDF/Excel to the response (`ReportExecutionService.stream`), then
   `ExportJobService.recordDirect` → `ExportJobRepository` (`app.export_job`, `delivery = DIRECT`).
4. Large (> 2000 rows or `async=true`): `ExportJobService.submit` (background thread, file in `app.export.directory`,
   `delivery = BACKGROUND`) → HTTP 202 → `chat.js pollExport()` → `GET /api/exports/{jobId}` → `GET /api/exports/{jobId}/download`
   (`ExportJobService.get` / `fileOf`, owner only).

### Query Analytics (ADMIN)
`/analytics` (`SecurityConfig` hasRole ADMIN → `PageController.analytics`) → `analytics.html load()` →
`GET /api/admin/analytics?days=N` → `AnalyticsApiController.summary` → `AccessPolicyService.resolve` (admin check)
→ `QueryAnalyticsService.summary`.

---

## 19. Points of uncertainty

- `app.max-login-attempts` and `app.account-lock-duration` are defined but no code using them was found.
- Spring Security `formLogin` is configured but the UI uses `/api/login`; no `UserDetailsService` bean was found, so
  whether form login at `POST /login` works is unverified.
- `UserRepository.findAll()` has no caller in the current code.
- `LoginAndAccessIntegrationTest` depends on a live Neon database (7 tests; passing at the last verification run).

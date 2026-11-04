# DataTalk AI – Predefined Reports Reference

This document describes the 11 predefined SQL reports shipped with the POC, how they are loaded, secured,
executed and exposed, and how to add a new one. Everything here is derived from the source code:

| Concern | Source |
|---|---|
| Report files | `src/main/resources/predefined-reports/{customer,device,payment,settlement}/*.sql` |
| Loader / parameter rendering / AI example selection | `reports/PredefinedReportService.java`, `reports/PredefinedReport.java` |
| Run / list logic | `chat/ChatService.java` (`runReport`, `availableReports`, `examplesFor`) |
| REST API | `controller/ReportApiController.java` |
| User scope & role tables | `reporting/AccessPolicyService.java`, `reporting/UserScope.java`, `application.properties` |
| SQL validation & row scoping | `reporting/SqlSafetyValidator.java`, `reporting/ReportFilters.java` |
| Execution (read-only, timeout, paging) | `reporting/ReportExecutionService.java` |
| UI sidebar | `templates/chat.html`, `static/js/chat.js` (`loadReports`, `runReport`) |

---

## 1. Summary of all reports

| # | Report id | Name | Category | Tables | Parameters (default) | Chart | ADMIN | BUSINESS / MANAGER | CUSTOMER |
|---|---|---|---|---|---|---|---|---|---|
| 1 | `payment-daily-sales-trend` | Daily sales trend | payment | `txn.payment_transaction` | `days` (30) | line | ✅ all customers | ✅ own customer | ✅ own customer, assigned devices |
| 2 | `payment-failed-transactions-by-location` | Failed transactions by location | payment | `txn.payment_transaction`, `device.device`, `location.location` | `days` (30) | bar | ✅ | ✅ | ✅ |
| 3 | `payment-sales-by-card-type` | Sales by card type | payment | `txn.payment_transaction` | `days` (30) | pie | ✅ | ✅ | ✅ |
| 4 | `payment-decline-reasons` | Top decline reasons | payment | `txn.payment_transaction` | `days` (30) | bar | ✅ | ✅ | ✅ |
| 5 | `device-device-status-summary` | Device status summary | device | `device.device` | – | bar | ✅ | ✅ | ✅ |
| 6 | `device-offline-devices` | Devices not communicating | device | `device.device`, `location.location` | `hours` (24) | table | ✅ | ✅ | ✅ |
| 7 | `device-device-events` | Device events by type | device | `device.device_status_log` | `days` (30) | bar | ✅ | ✅ | ✅ |
| 8 | `settlement-settlement-summary` | Settlement summary by status | settlement | `txn.settlement` | `days` (30) | bar | ✅ | ✅ | ❌ |
| 9 | `settlement-weekly-net-settlement` | Weekly net settlement | settlement | `txn.settlement` | `weeks` (12) | line | ✅ | ✅ | ❌ |
| 10 | `customer-revenue-by-customer` | Revenue by customer | customer | `txn.payment_transaction`, `corp.customer` | `days` (30) | bar | ✅ | ✅ | ❌ |
| 11 | `customer-locations-and-devices` | Locations and devices per customer | customer | `corp.customer`, `location.location`, `device.device` | – | table | ✅ | ✅ | ❌ |

The role columns assume the current `application.properties` (see [§3](#3-which-roles-can-run-which-reports)).
The API returns and the UI shows the reports sorted by category, then id: `customer`, `device`, `payment`, `settlement`.

---

## 2. How predefined reports are loaded

`PredefinedReportService.load()` runs once at application start-up (`@PostConstruct`):

1. Scans the classpath with `classpath*:predefined-reports/**/*.sql`.
2. **Category** = the name of the folder directly under `predefined-reports/`
   (regex `.*/predefined-reports/([^/]+)/[^/]+$`).
3. **Report id** = `<category>-<file name without ".sql" and without a leading "<digits>-" prefix>`.
   Example: `payment/01-daily-sales-trend.sql` → `payment-daily-sales-trend`.
   The numeric prefix only controls file ordering on disk; it is not part of the id.
4. Reports are sorted by `category`, then `id`, and kept in memory (`LinkedHashMap`). A duplicate id would
   silently overwrite the earlier one.

### Header parsing (`PredefinedReportService.parse`)

Each line is trimmed. While **no SQL has been collected yet**, a line that starts with `-- ` and contains `:` is
treated as a header line `-- <key>: <value>` (key is case-insensitive):

| Key | Meaning | Default if missing |
|---|---|---|
| `name` | Display name (also used as the chat "question" text and report title) | the report id |
| `description` | Business description (shown as tooltip, used as "understanding"/explanation) | empty |
| `param` | `name \| type \| default \| description` – may repeat | no parameters |
| `example` | Example natural-language question – may repeat | no examples |
| `chart` | Chart type | `table` |

- Unknown keys are ignored.
- Every other non-empty line (and every line after the first SQL line) becomes part of the SQL template.
  Blank lines are dropped.
- `param` parsing: split on `|`; the default (3rd part) is parsed with `Integer.parseInt` – a non-integer
  default or fewer than 3 parts makes start-up fail. The 4th part (description) is optional. The `type`
  part is stored and returned by the API but is **not interpreted**: all parameters are handled as integers.
- `chart` is not validated at load time; when the report is run it is normalised by
  `AiReportResponse.normalizeChartType` to one of `table`, `bar`, `line`, `pie` (anything else → `table`).

### Parameter binding (`PredefinedReportService.renderSql`)

Parameters are **not** JDBC bind variables. They are validated and then substituted textually as integers:

- References in the SQL use the form `:name` (regex `:([a-z_]+)\b`, so names must be lower-case letters /
  underscores).
- For each declared parameter: value from the request `params` map, or the header default when missing/blank.
- The value must parse as a whole number, otherwise `400 INVALID_PARAMETER` ("must be a whole number").
- The value must be between **1 and 3650**, otherwise `400 INVALID_PARAMETER`.
- PostgreSQL casts such as `::date` are recognised (preceded by another `:`) and left untouched.
- A `:name` reference without a matching `param` header is left unchanged (and would then fail SQL parsing).
- Request params that are not declared in the header are ignored.

---

## 3. Which roles can run which reports

There is **no hard-coded category-to-role mapping**. Availability is derived at runtime:

`ChatService.availableReports(scope)` renders every report with its default parameters and runs it through
`SqlSafetyValidator.validate(...)` for the logged-in user. Any report that fails validation (e.g. it uses a table
the role may not access) is hidden from `GET /api/reports`. `POST /api/reports/{id}/run` performs the same
validation again and returns `403` if a table is not allowed.

The table allow-list per role comes from `AccessPolicyService`:

- Approved tables = all base tables in the schemas `app.reporting.approved-schemas=corp,location,device,txn`
  that have a `customer_id` or `device_id` column (`SchemaMetadataService`; tables without one are skipped,
  "fail closed"). Blocked columns: `txn.payment_transaction.card_last_four`, `corp.customer.email`,
  `corp.customer.phone` (none of the predefined reports use them).
- If `app.reporting.role-tables.<ROLE>` is configured, the role is limited to those tables; otherwise the role
  may use **all** approved tables.
- Currently only `CUSTOMER` is configured:
  `app.reporting.role-tables.CUSTOMER=txn.payment_transaction,device.device,device.device_status_log,location.location`.

Roles allowed by the database (`app_user.role` CHECK constraint): `ADMIN`, `BUSINESS`, `MANAGER`, `CUSTOMER`.

| Role | Allowed tables | Row scope applied to every table | Reports available |
|---|---|---|---|
| `ADMIN` | all approved tables | none (all customers) | all 11 |
| `BUSINESS` | all approved tables (not configured) | `customer_id = <user's customer>` | all 11, own customer only |
| `MANAGER` | all approved tables (not configured) | `customer_id = <user's customer>` | all 11, own customer only |
| `CUSTOMER` | `txn.payment_transaction`, `device.device`, `device.device_status_log`, `location.location` | own customer **and** only devices in `app_user.user_device_access` (`app.reporting.device-restricted-roles`, default `CUSTOMER`) | 7: all **payment** and **device** reports |

Why CUSTOMER loses the other categories: the **settlement** reports read `txn.settlement` and the **customer**
reports read `corp.customer`; neither table is in the CUSTOMER allow-list, so validation fails with
`UNAUTHORIZED_TABLE`.

Additional preconditions (`AccessPolicyService.resolve`): the user must be logged in (`401 NOT_AUTHENTICATED`),
`ACTIVE` (`403 INACTIVE_USER`) and, unless `ADMIN`, linked to a customer (`403 NO_CUSTOMER`). Role, customer and
status are re-read from the database on every request.

---

## 4. Security and execution of a predefined report

What `ChatService.runReport` does, in order:

1. **Lookup** by id – unknown id → `404 REPORT_NOT_FOUND`.
2. **Render** parameters (integers 1–3650, defaults otherwise) – see §2.
3. **Validate and rewrite** with the same `SqlSafetyValidator` used for AI-generated SQL (predefined SQL is not
   trusted blindly):
   - max SQL length (`app.reporting.max-sql-length=6000`), no `$$`, exactly one statement, must be `SELECT`
     (no recursive CTE, `INTO`, `FOR UPDATE/SHARE`);
   - every table must be an approved table **and** in the user's allowed tables
     (`UNAUTHORIZED_TABLE` / `RESTRICTED_TABLE` → returned as `403 "You are not allowed to run this report."`);
   - every column must be an approved column or a query alias; every function must be on the allow-list
     (`count`, `sum`, `coalesce`, `date_trunc`, `now`, …); a lexical safety net blocks write/admin keywords,
     `pg_*` objects and system schemas; non-admins may not compare `customer_id` with another customer's literal.
   - **Scope wrapping**: each table reference (e.g. `txn.payment_transaction pt`) is replaced by a generated CTE
     (`dt_txn_payment_transaction`) that selects only approved columns and only allowed rows:
     - non-admin: `customer_id = <session customer>` (or, for tables without `customer_id`,
       `device_id IN (SELECT device_id FROM device.device WHERE customer_id = <customer>)` – e.g.
       `device.device_status_log`);
     - device-restricted roles (CUSTOMER): additionally `device_id IN (<assigned ids>)` on tables having a
       `device_id` column (`FALSE` if no devices are assigned);
     - UI filters (see below).
     The final SQL is `WITH dt_... AS (...), ... SELECT * FROM (<report SQL>) AS dt_result`.
4. **Execute** (`ReportExecutionService.executePage`, page 1):
   - in a transaction with `SET TRANSACTION READ ONLY`, `SET LOCAL statement_timeout = '30s'`
     (`app.reporting.statement-timeout-seconds`) and `SET LOCAL ROLE datatalk_ro`
     (`app.reporting.read-only-role`, SELECT-only DB role); JDBC query timeout = timeout + 5 s;
   - total rows via `SELECT count(*) FROM (<sql>) AS dt_count`;
   - page via `<sql> LIMIT <pageSize> OFFSET <offset>`; `pageSize` default 25, max 100
     (`app.reporting.default-page-size` / `max-page-size`); values < 1 or missing → 25.
   - Database errors become an `EXECUTION_ERROR` result with a friendly message (e.g. timeout).
5. **History**: the run is stored in `app.chat_history` like a chat answer with `source = PREDEFINED`,
   question = report name, title = report name, explanation = description, chart = header `chart`, and the
   rendered (pre-scope) SQL. No AI call is made.

Later pages, filtered views and exports re-validate the stored SQL with the user's **current** scope
(`ChatService.secureQueryFor`). Exports (`GET /api/chat/{chatId}/export`) stream at most
`app.export.max-rows=50000` rows.

### Date / status filters (`ReportFilters`)

Optional request fields `dateFrom`, `dateTo` (`yyyy-MM-dd`) and `status` are added **inside the scope CTEs**,
in addition to the report's own `:days` / `:hours` / `:weeks` window (both apply):

| Table | Date filter column | Status filter column |
|---|---|---|
| `txn.payment_transaction` | `transaction_time` | `transaction_status` |
| `txn.settlement` | `settlement_date` | – |
| `device.device_status_log` | `event_time` | – |
| `device.device`, `location.location`, `corp.customer` | – | – |

- `dateFrom` → `col >= DATE 'from'`; `dateTo` → `col < DATE 'to' + 1 day` (inclusive end date).
- `status` must be one of `APPROVED`, `DECLINED`, `FAILED`, `REFUNDED` (case-insensitive), otherwise
  `400 INVALID_FILTER`; `dateFrom` after `dateTo` is also `INVALID_FILTER`.
- Note: reports that hard-code a status (e.g. `transaction_status = 'APPROVED'`) return no rows when combined
  with a different status filter, because both conditions apply.

### Does the AI pick a predefined report for a question?

**No.** A chat question (`POST /api/chat/ask`) never runs a predefined report automatically. Predefined reports
are only run explicitly (sidebar / `POST /api/reports/{id}/run`). In the chat flow they are used as **prompt
examples**:

- `PredefinedReportService.relevantExamples` tokenises the question (lower-case, words > 2 chars, stop words
  removed, trailing `s` stripped for words > 4 chars) and scores each report by overlapping words with its
  name + description + examples.
- Only reports whose `corp|location|device|txn` tables are all in the user's allowed tables are considered.
- Up to `app.ai.max-examples=4` reports with score > 0 are added to the system prompt as
  `-- name: description`, `-- Question: <first example>` and the SQL rendered with default parameters.
- The AI then writes its own SQL, which is validated and scoped exactly as above.

---

## 5. How reports are exposed

### `GET /api/reports`

Requires a logged-in session. Returns only the reports the user may run (see §3), **without SQL**:

```json
{
  "success": true,
  "data": [
    {
      "id": "payment-daily-sales-trend",
      "category": "payment",
      "name": "Daily sales trend",
      "description": "Approved sales amount and transaction count per day.",
      "parameters": [ { "name": "days", "type": "integer", "defaultValue": 30, "description": "Number of days to look back" } ],
      "examples": [ "Show daily sales for the last 30 days", "What is the daily revenue trend this month?" ],
      "chartType": "line"
    }
  ]
}
```

(Shape follows the `ReportInfo` record inside the `ApiResponse` envelope: `success`, `message`, `data`, `error`.
On errors `success` is `false` and `error` holds the code, e.g. `REPORT_NOT_FOUND`.)

### `POST /api/reports/{reportId}/run`

Body is optional (all fields optional):

| Field | Type | Meaning |
|---|---|---|
| `params` | object `{ "name": "value" }` | Report parameters (string values parsed as integers 1–3650); missing → header default |
| `dateFrom` | `yyyy-MM-dd` | Date filter start (inclusive) |
| `dateTo` | `yyyy-MM-dd` | Date filter end (inclusive) |
| `status` | string | `APPROVED`, `DECLINED`, `FAILED` or `REFUNDED` |
| `pageSize` | integer | Rows in the first page (default 25, max 100) |

Example:

```json
{ "params": { "days": "7" }, "dateFrom": null, "dateTo": null, "status": null, "pageSize": 50 }
```

Response: a `ChatResult` (`chatId`, `status`, `source = "PREDEFINED"`, `title`, `explanation`, `chartType`, `sql`,
`page` = `{ columns, columnTypes, rows, totalRows, page, pageSize, totalPages }`, `durationMs`, …).
Use `chatId` for further pages (`GET /api/chat/{chatId}/results?page=&pageSize=&dateFrom=&dateTo=&status=`) and
exports (`GET /api/chat/{chatId}/export?format=pdf|...`).

Errors: `401 NOT_AUTHENTICATED`, `403 INACTIVE_USER` / `NO_CUSTOMER` / table not allowed,
`404 REPORT_NOT_FOUND`, `400 INVALID_PARAMETER` / `INVALID_FILTER` / other validation codes. CSRF is disabled for
`/api/**`; all non-public endpoints require authentication (`SecurityConfig`).

### UI (chat page)

- The left sidebar card **"Predefined Reports"** (`#reportList` in `chat.html`) is filled by
  `loadReports()` in `chat.js` from `GET /api/reports`, grouped by category; if the list is empty it shows
  "No reports available for your role."
- Clicking a report calls `runReport(id, name)` → `POST /api/reports/{id}/run` with
  `{ params: {}, dateFrom, dateTo, status }` taken from the filter controls. **The UI always uses the default
  parameter values**; custom `params` are only possible through the API.
- The result is rendered like a chat answer (table/chart, paging, filters, export) and appears in history.

---

## 6. Report details

> SQL below is copied verbatim from the files (header comments omitted). Output column types are as reported
> by the JDBC metadata mapping (`number` / `date` / `text`).

### 6.1 Payments

#### `payment-daily-sales-trend` – Daily sales trend

- **File:** `predefined-reports/payment/01-daily-sales-trend.sql`
- **Category:** payment
- **Business purpose:** Approved sales amount and transaction count per day.
- **Example questions:** "Show daily sales for the last 30 days"; "What is the daily revenue trend this month?"
- **SQL:**

```sql
SELECT date_trunc('day', pt.transaction_time)::date AS sales_day,
       COUNT(*) AS approved_transactions,
       SUM(pt.amount) AS approved_amount
FROM txn.payment_transaction pt
WHERE pt.transaction_status = 'APPROVED'
  AND pt.transaction_time >= CURRENT_DATE - (:days * INTERVAL '1 day')
GROUP BY 1
ORDER BY 1
```

- **Tables:** `txn.payment_transaction`
- **Joins:** none
- **Filters / parameters:** `transaction_status = 'APPROVED'`; `days` (integer, default 30, 1–3650) – transactions
  since `CURRENT_DATE - days`. UI date filter → `transaction_time`; status filter → `transaction_status`.
- **Output columns:** `sales_day` (date), `approved_transactions` (number), `approved_amount` (number)
- **Chart:** line
- **Roles:** ADMIN (all customers), BUSINESS/MANAGER (own customer), CUSTOMER (own customer, assigned devices).

#### `payment-failed-transactions-by-location` – Failed transactions by location

- **File:** `predefined-reports/payment/02-failed-transactions-by-location.sql`
- **Category:** payment
- **Business purpose:** Declined, failed and refunded transactions grouped by location.
- **Example questions:** "Show failed transactions from the last 30 days grouped by location"; "Which locations have the most declined payments?"
- **SQL:**

```sql
SELECT l.location_name,
       l.city,
       COUNT(*) AS failed_transactions,
       SUM(pt.amount) AS failed_amount
FROM txn.payment_transaction pt
JOIN device.device d ON d.device_id = pt.device_id
JOIN location.location l ON l.location_id = d.location_id
WHERE pt.transaction_status IN ('DECLINED', 'FAILED', 'REFUNDED')
  AND pt.transaction_time >= CURRENT_DATE - (:days * INTERVAL '1 day')
GROUP BY l.location_name, l.city
ORDER BY failed_transactions DESC
```

- **Tables:** `txn.payment_transaction`, `device.device`, `location.location`
- **Joins:** `INNER JOIN device.device` on `device_id`; `INNER JOIN location.location` on `d.location_id`
- **Filters / parameters:** `transaction_status IN ('DECLINED','FAILED','REFUNDED')`; `days` (integer, default 30).
  UI date/status filters apply to `txn.payment_transaction`.
- **Output columns:** `location_name` (text), `city` (text), `failed_transactions` (number), `failed_amount` (number)
- **Chart:** bar
- **Roles:** ADMIN, BUSINESS, MANAGER, CUSTOMER (all three tables are in the CUSTOMER allow-list; for CUSTOMER the
  transactions and devices are limited to assigned devices).

#### `payment-sales-by-card-type` – Sales by card type

- **File:** `predefined-reports/payment/03-sales-by-card-type.sql`
- **Category:** payment
- **Business purpose:** Approved transaction count and amount by card brand.
- **Example questions:** "Show sales by card type"; "How much revenue came from Visa versus Mastercard?"
- **SQL:**

```sql
SELECT pt.card_type,
       COUNT(*) AS transactions,
       SUM(pt.amount) AS total_amount
FROM txn.payment_transaction pt
WHERE pt.transaction_status = 'APPROVED'
  AND pt.transaction_time >= CURRENT_DATE - (:days * INTERVAL '1 day')
GROUP BY pt.card_type
ORDER BY total_amount DESC
```

- **Tables:** `txn.payment_transaction`
- **Joins:** none
- **Filters / parameters:** `transaction_status = 'APPROVED'`; `days` (integer, default 30). UI date/status filters apply.
- **Output columns:** `card_type` (text), `transactions` (number), `total_amount` (number)
- **Chart:** pie
- **Roles:** ADMIN, BUSINESS, MANAGER, CUSTOMER.

#### `payment-decline-reasons` – Top decline reasons

- **File:** `predefined-reports/payment/04-decline-reasons.sql`
- **Category:** payment
- **Business purpose:** Most common decline reasons with counts.
- **Example questions:** "What are the top decline reasons?"; "Why are payments being declined this month?"
- **SQL:**

```sql
SELECT COALESCE(pt.decline_reason, 'Unknown') AS decline_reason,
       COUNT(*) AS declined_transactions
FROM txn.payment_transaction pt
WHERE pt.transaction_status = 'DECLINED'
  AND pt.transaction_time >= CURRENT_DATE - (:days * INTERVAL '1 day')
GROUP BY 1
ORDER BY declined_transactions DESC
```

- **Tables:** `txn.payment_transaction`
- **Joins:** none
- **Filters / parameters:** `transaction_status = 'DECLINED'`; `days` (integer, default 30). UI date/status filters apply.
- **Output columns:** `decline_reason` (text, `'Unknown'` when NULL), `declined_transactions` (number)
- **Chart:** bar
- **Roles:** ADMIN, BUSINESS, MANAGER, CUSTOMER.

### 6.2 Devices

#### `device-device-status-summary` – Device status summary

- **File:** `predefined-reports/device/01-device-status-summary.sql`
- **Category:** device
- **Business purpose:** Number of devices in each status by device type.
- **Example questions:** "How many devices are active, inactive or in maintenance?"; "Show device count by status"
- **SQL:**

```sql
SELECT d.status,
       d.device_type,
       COUNT(*) AS devices
FROM device.device d
GROUP BY d.status, d.device_type
ORDER BY d.status, devices DESC
```

- **Tables:** `device.device`
- **Joins:** none
- **Filters / parameters:** none. UI date/status filters have no effect (no filter column for `device.device`).
- **Output columns:** `status` (text), `device_type` (text), `devices` (number)
- **Chart:** bar
- **Roles:** ADMIN, BUSINESS, MANAGER, CUSTOMER (CUSTOMER sees only assigned devices).

#### `device-offline-devices` – Devices not communicating

- **File:** `predefined-reports/device/02-offline-devices.sql`
- **Category:** device
- **Business purpose:** Devices whose last communication is older than the given number of hours.
- **Example questions:** "Which devices have not communicated in the last 24 hours?"; "List offline devices"
- **SQL:**

```sql
SELECT d.serial_number,
       d.device_type,
       d.status,
       l.location_name,
       d.last_communication_time
FROM device.device d
LEFT JOIN location.location l ON l.location_id = d.location_id
WHERE d.last_communication_time < NOW() - (:hours * INTERVAL '1 hour')
   OR d.last_communication_time IS NULL
ORDER BY d.last_communication_time NULLS FIRST
```

- **Tables:** `device.device`, `location.location`
- **Joins:** `LEFT JOIN location.location` on `location_id`
- **Filters / parameters:** `hours` (integer, default 24, 1–3650) – last communication older than `NOW() - hours`,
  or never communicated (NULL). UI date/status filters have no effect.
- **Output columns:** `serial_number` (text), `device_type` (text), `status` (text), `location_name` (text),
  `last_communication_time` (date/timestamp, formatted `yyyy-MM-dd HH:mm:ss`)
- **Chart:** table
- **Roles:** ADMIN, BUSINESS, MANAGER, CUSTOMER (CUSTOMER sees only assigned devices).

#### `device-device-events` – Device events by type

- **File:** `predefined-reports/device/03-device-events.sql`
- **Category:** device
- **Business purpose:** Count of device status events (errors, reboots, offline, ...) by event type.
- **Example questions:** "How many device errors happened in the last 30 days?"; "Show device events grouped by type"
- **SQL:**

```sql
SELECT e.event_type,
       COUNT(*) AS events,
       COUNT(DISTINCT e.device_id) AS devices_affected
FROM device.device_status_log e
WHERE e.event_time >= CURRENT_DATE - (:days * INTERVAL '1 day')
GROUP BY e.event_type
ORDER BY events DESC
```

- **Tables:** `device.device_status_log`
- **Joins:** none in the report SQL. (For non-admins the scope CTE adds
  `device_id IN (SELECT device_id FROM device.device WHERE customer_id = …)` because the table has no `customer_id`.)
- **Filters / parameters:** `days` (integer, default 30). UI date filter → `event_time`; status filter has no effect.
- **Output columns:** `event_type` (text), `events` (number), `devices_affected` (number)
- **Chart:** bar
- **Roles:** ADMIN, BUSINESS, MANAGER, CUSTOMER (CUSTOMER sees only events of assigned devices).

### 6.3 Settlements

#### `settlement-settlement-summary` – Settlement summary by status

- **File:** `predefined-reports/settlement/01-settlement-summary.sql`
- **Category:** settlement
- **Business purpose:** Settlement totals, fees and net amounts grouped by settlement status.
- **Example questions:** "Show settlement totals for the last 30 days"; "How much is pending settlement?"
- **SQL:**

```sql
SELECT s.status,
       COUNT(*) AS settlements,
       SUM(s.total_amount) AS total_amount,
       SUM(s.fees) AS total_fees,
       SUM(s.net_amount) AS net_amount
FROM txn.settlement s
WHERE s.settlement_date >= CURRENT_DATE - :days
GROUP BY s.status
ORDER BY net_amount DESC
```

- **Tables:** `txn.settlement`
- **Joins:** none
- **Filters / parameters:** `days` (integer, default 30) – `settlement_date >= CURRENT_DATE - days` (date minus
  integer). UI date filter → `settlement_date`; status filter has no effect (it only targets payment transactions).
- **Output columns:** `status` (text), `settlements` (number), `total_amount` (number), `total_fees` (number),
  `net_amount` (number)
- **Chart:** bar
- **Roles:** ADMIN, BUSINESS, MANAGER (own customer). **Not CUSTOMER** – `txn.settlement` is not in its allow-list.

#### `settlement-weekly-net-settlement` – Weekly net settlement

- **File:** `predefined-reports/settlement/02-weekly-net-settlement.sql`
- **Category:** settlement
- **Business purpose:** Net settled amount and fees per week.
- **Example questions:** "Show weekly settlement amounts"; "What were the fees per week?"
- **SQL:**

```sql
SELECT date_trunc('week', s.settlement_date)::date AS week_start,
       SUM(s.total_amount) AS total_amount,
       SUM(s.fees) AS fees,
       SUM(s.net_amount) AS net_amount
FROM txn.settlement s
WHERE s.settlement_date >= CURRENT_DATE - (:weeks * 7)
GROUP BY 1
ORDER BY 1
```

- **Tables:** `txn.settlement`
- **Joins:** none
- **Filters / parameters:** `weeks` (integer, default 12, 1–3650) – `settlement_date >= CURRENT_DATE - weeks*7`.
  UI date filter → `settlement_date`; status filter has no effect.
- **Output columns:** `week_start` (date), `total_amount` (number), `fees` (number), `net_amount` (number)
- **Chart:** line
- **Roles:** ADMIN, BUSINESS, MANAGER. **Not CUSTOMER**.

### 6.4 Customers

#### `customer-revenue-by-customer` – Revenue by customer

- **File:** `predefined-reports/customer/01-revenue-by-customer.sql`
- **Category:** customer
- **Business purpose:** Approved sales amount per customer (business users only see their own customer).
- **Example questions:** "Show revenue by customer"; "Which customer has the highest sales?"
- **SQL:**

```sql
SELECT c.customer_name,
       COUNT(*) AS approved_transactions,
       SUM(pt.amount) AS approved_amount
FROM txn.payment_transaction pt
JOIN corp.customer c ON c.customer_id = pt.customer_id
WHERE pt.transaction_status = 'APPROVED'
  AND pt.transaction_time >= CURRENT_DATE - (:days * INTERVAL '1 day')
GROUP BY c.customer_name
ORDER BY approved_amount DESC
```

- **Tables:** `txn.payment_transaction`, `corp.customer`
- **Joins:** `INNER JOIN corp.customer` on `customer_id`
- **Filters / parameters:** `transaction_status = 'APPROVED'`; `days` (integer, default 30). UI date/status
  filters apply to `txn.payment_transaction`.
- **Output columns:** `customer_name` (text), `approved_transactions` (number), `approved_amount` (number)
- **Chart:** bar
- **Roles:** ADMIN (one row per customer), BUSINESS/MANAGER (only their own customer row). **Not CUSTOMER** –
  `corp.customer` is not in its allow-list.

#### `customer-locations-and-devices` – Locations and devices per customer

- **File:** `predefined-reports/customer/02-locations-and-devices.sql`
- **Category:** customer
- **Business purpose:** Number of locations and devices for each customer.
- **Example questions:** "How many locations and devices does each customer have?"; "Show customer footprint"
- **SQL:**

```sql
SELECT c.customer_name,
       c.status,
       COUNT(DISTINCT l.location_id) AS locations,
       COUNT(DISTINCT d.device_id) AS devices
FROM corp.customer c
LEFT JOIN location.location l ON l.customer_id = c.customer_id
LEFT JOIN device.device d ON d.customer_id = c.customer_id
GROUP BY c.customer_name, c.status
ORDER BY devices DESC
```

- **Tables:** `corp.customer`, `location.location`, `device.device`
- **Joins:** `LEFT JOIN location.location` on `customer_id`; `LEFT JOIN device.device` on `customer_id`
- **Filters / parameters:** none. UI date/status filters have no effect.
- **Output columns:** `customer_name` (text), `status` (text), `locations` (number), `devices` (number)
- **Chart:** table
- **Roles:** ADMIN, BUSINESS, MANAGER (own customer only). **Not CUSTOMER**.

---

## 7. How to add a new predefined report

1. **Choose the category folder** under `src/main/resources/predefined-reports/`. The folder name *is* the
   category (shown as a sidebar group and used as id prefix). Existing: `payment`, `device`, `settlement`,
   `customer`. A new folder creates a new category automatically – no Java change is needed.
2. **Create the file** `NN-short-name.sql` (e.g. `payment/05-sales-by-entry-mode.sql`). The optional `NN-`
   numeric prefix is stripped; the id becomes `<category>-<short-name>` (here `payment-sales-by-entry-mode`).
   Keep ids unique – a duplicate silently replaces the other report.
3. **Write the header** as the first lines, each `-- key: value` (before any SQL line):

   ```sql
   -- name: Sales by entry mode
   -- description: Approved transaction count and amount by entry mode.
   -- param: days | integer | 30 | Number of days to look back
   -- example: Show sales by entry mode
   -- example: How many tap payments did we have?
   -- chart: bar
   ```

   - `param` format: `name | integer | <integer default> | description`. Only integer parameters exist; the
     default must be an integer or the application will not start. Runtime values must be 1–3650.
   - Parameter names: lower-case letters and underscores only (referenced as `:name`).
   - `chart`: `table`, `bar`, `line` or `pie` (anything else is shown as `table`).
   - Write good `example` lines – they drive keyword matching when the report is offered as an AI prompt example,
     and the first one is included in the prompt.
4. **Write the SQL** below the header (illustrative example – verify against the schema before use):

   ```sql
   SELECT pt.entry_mode,
          COUNT(*) AS transactions,
          SUM(pt.amount) AS total_amount
   FROM txn.payment_transaction pt
   WHERE pt.transaction_status = 'APPROVED'
     AND pt.transaction_time >= CURRENT_DATE - (:days * INTERVAL '1 day')
   GROUP BY pt.entry_mode
   ORDER BY total_amount DESC
   ```

   Rules imposed by `SqlSafetyValidator` (a report that violates them is hidden from everyone):
   - a single `SELECT` (CTEs allowed, not `RECURSIVE`); no trailing statements; no `$$`; no CTE names starting
     with `dt_`;
   - only schema-qualified approved tables (`corp`, `location`, `device`, `txn` tables with a `customer_id` or
     `device_id` column); use a table alias;
   - only approved columns (not `card_last_four`, `email`, `phone`) and allow-listed functions;
   - **do not** add customer or device filters yourself – scoping is injected automatically;
   - do not add `LIMIT`/`OFFSET` – paging is appended by the executor (`LIMIT … OFFSET …`);
   - the tables used determine which roles see the report (e.g. using `txn.settlement` or `corp.customer` hides
     it from `CUSTOMER`). To change role access, edit `app.reporting.role-tables.<ROLE>` – not the report.
5. **Rebuild and restart.** Reports are read from the classpath once at start-up (`@PostConstruct`), and there is
   no reload endpoint, so a restart (with the resources re-copied by the build) is required. Check the log line
   `Loaded N predefined reports`.
6. **Test via the API** (logged-in session, e.g. via browser or a cookie-aware client):
   - `GET /api/reports` – the new id should appear for the expected roles only (log in as ADMIN, a BUSINESS or
     MANAGER user, and a CUSTOMER user to compare);
   - `POST /api/reports/<id>/run` with `{ "params": { "days": "7" } }` – expect `status: SUCCESS` and
     `source: PREDEFINED`; also try an invalid param (`"0"`, `"abc"`) to see `INVALID_PARAMETER`.
7. **Test in the UI:** open the chat page – the report appears in the **Predefined Reports** sidebar under its
   category; click it (defaults are used), try the date/status filters, paging and export.
8. Optionally add the report to this document and to the summary table.

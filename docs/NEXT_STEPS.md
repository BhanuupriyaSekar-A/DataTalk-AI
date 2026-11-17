# SL Talk AI – Next Steps

**Last updated:** November 4, 2026

The personal POC is functionally complete. The main next step is to hand the idea to the company AI/ML team
as a **requirements proposal**:
[DATATALK_AI_BUSINESS_AND_TECHNICAL_REQUIREMENTS.md](DATATALK_AI_BUSINESS_AND_TECHNICAL_REQUIREMENTS.md).

## 1. Immediate (POC housekeeping)
- [ ] Run `database/phase3-migration.sql` as `neondb_owner` in the Neon SQL Editor, then do one export and check that it appears in `app.export_job` and on the Analytics page.
- [ ] Rotate the Neon database password and any AI API keys that have been shared or committed. Keep them only in `.env` or environment variables.
- [ ] Commit the current work (`.env` is git-ignored).

## 2. Company evaluation (owned by the company AI/ML team)
- Review the requirements proposal and answer its open questions (Section 16).
- Confirm the target database(s), role and permission model, and compliance constraints.
- Choose the LLM and provider strategy, and build an SQL-accuracy evaluation dataset.
- Propose the official architecture, API design and deployment approach.

## 3. Optional POC improvements (only if useful for demos)
- Small evaluation set of question → expected-result pairs to measure provider accuracy.
- Separate AI-time and SQL-time measurements, if more precise analytics are needed.
- Simple user-management and password-reset screens for ADMIN.

## 4. Future enhancements (not planned for the POC)
- Scheduled and saved reports.
- Advanced business insights (trends, anomalies), suggested follow-up questions.
- More data sources and databases.
- Comprehensive integration, E2E, load and security testing; CI/CD; monitoring.

# Architecture

## Overview

A backend system that scans a personal Gmail inbox once a day, identifies emails related to job applications, classifies them by matching the phrases hiring platforms use, persists the results as the source of truth in a PostgreSQL database, mirrors them into a Google Sheet used as a manual control dashboard, and sends a daily WhatsApp summary.

This is a personal study project built to demonstrate backend engineering practices (Java, Spring Boot, PostgreSQL, Docker, CI/CD, hexagonal architecture, automated security checks) as a portfolio piece. It intentionally favors clarity, documented decisions, and complete implementation of standard practices over minimal effort.

## Tech stack

- **Language / framework:** Java 25, Spring Boot
- **Build tool:** Maven
- **Database:** PostgreSQL, hosted on Neon (free tier)
- **Schema migrations:** Flyway
- **Containerization:** Docker Compose, used for the local PostgreSQL instance only (the application itself runs directly via Maven/JVM locally, and inside the GitHub Actions runner in production — containerizing the app adds no benefit in either environment)
- **Scheduling / execution:** GitHub Actions (`schedule` cron trigger), no always-on server
- **External integrations:** Gmail API, Google Sheets API, Meta WhatsApp Cloud API

## Language convention

All code, comments, configuration, commit messages, and documentation are written in English. The one exception is data captured from emails (sender content, extracted summaries) — those are stored and displayed in their original language, since most applications are submitted in pt-BR with occasional en-US emails.

## Architecture style: Hexagonal (Ports & Adapters)

The system is fundamentally an orchestrator around external integrations (Gmail, Postgres, Sheets, WhatsApp), so a plain layered (controller/service/repository) structure would blur business logic with integration details. Hexagonal architecture keeps the domain free of framework and vendor concerns:

- **`domain`** — core models and business rules. No framework dependencies, no knowledge of Gmail/Postgres/Sheets/WhatsApp. Contains the classifier (see below) and the digest-building logic.
- **`application`** — use cases that orchestrate the domain through ports (e.g. `RunDailyScanUseCase`).
- **`ports`** — interfaces the domain/application layer depends on:
  - `EmailSourcePort` — fetch candidate emails from the last 24h
  - `PersistencePort` — read/write `EmailClassification` records
  - `SpreadsheetPort` — sync records to the dashboard
  - `NotificationPort` — send the daily summary / failure alert
- **`adapters`** — concrete implementations of each port:
  - `adapters/gmail` → `GmailApiAdapter implements EmailSourcePort`
  - `adapters/persistence` → `PostgresRepositoryAdapter implements PersistencePort` (Spring Data JPA)
  - `adapters/sheets` → `GoogleSheetsAdapter implements SpreadsheetPort`
  - `adapters/whatsapp` → `MetaWhatsAppAdapter implements NotificationPort`

Swapping an integration (e.g. WhatsApp provider, database host) means writing a new adapter — the domain and application layers are untouched. This also makes the domain trivially testable without mocking frameworks, since it only depends on interfaces.

## Daily pipeline

1. GitHub Actions triggers `daily-run.yml` on a daily cron schedule; a fresh Ubuntu runner is provisioned.
2. Secrets are injected as environment variables from GitHub Secrets.
3. `GmailApiAdapter` fetches emails received in the last 24h (`gmail.readonly` scope only).
4. Each email is checked against `gmail_message_id` in the database — if it already exists, it is skipped (idempotency, see below).
5. `EmailClassifier` reads each email. It answers with a classification, or with nothing when the email is not about a job application — and those are dropped without being recorded.
6. Classifications are persisted in Postgres, the source of truth.
7. New/unsynced records (`sheet_synced_at IS NULL`) are written to the Google Sheet.
8. A WhatsApp message is sent with the daily summary — every day, even when there is nothing new.
9. If any external service is unreachable and prevents completion (not a per-email classification failure), the job fails visibly: GitHub Actions' built-in failure email fires automatically, and a final step sends a separate WhatsApp alert using a distinct message template.
10. The runner is destroyed. Nothing stays running between executions.

Per-item failures (a single email failing classification) are caught and logged individually; they do not abort processing of the remaining emails in that run. Failures connecting to a whole service (e.g. Postgres unreachable) abort the run, since nothing can be persisted.

## Classification

`EmailClassifier` matches phrases against the subject and body, and reads the sender's domain.

This works because the emails are templates: hiring platforms send the same sentences every time — *"recebemos sua candidatura"*, *"infelizmente não seguiremos"*, *"gostaríamos de convidá-lo"*. Matching phrases against templated text is a different proposition from matching them against free-form writing, and this design would be a poor one in another domain.

An email counts as being about an application when it either carries a phrase showing one exists, or comes from a known hiring platform — those systems only write to people already in a process. Being about a job is not the same thing: a newsletter listing openings mentions vacancies on every line and is dropped.

Phrases are read in order of finality — offer, rejection, interview, technical test, information request, acknowledgement — because a rejection almost always names the interview it is rejecting you after.

The classifier never fills in the company or the role. Guessing those from phrases would produce values that look extracted but are not, and a wrong company is worse than an empty one. The platform comes from the sender's domain, which is a fact rather than a guess, and the summary is the subject line.

**Emails that are not about an application are never stored.** The existence of a record is the verdict, so no column says whether it counts. This also keeps unrelated personal mail — invoices, newsletters, private messages — out of the database entirely.

An earlier design ran a cheap rule filter first and sent only its matches to a language model, keeping both answers so that a disagreement between them stayed visible. The filter existed to control the cost of the model. With no model, there is nothing to filter for and no second opinion to compare against, so both were removed (migration `V2`).

## Idempotency

`email_classifications.gmail_message_id` is a unique constraint. Before classifying an email, the pipeline checks whether that message ID already exists. This makes the daily run safe to re-trigger without duplicating records or duplicate-notifying — running twice in a day, or re-running after a partial failure, has no side effects beyond processing whatever wasn't processed yet.

## Database schema

The shape below is what the table holds after both migrations. `V1` created it; `V2` dropped the four columns that supported the two-signal design described above.

```sql
CREATE TABLE email_classifications (
    id                       BIGSERIAL PRIMARY KEY,
    gmail_message_id         VARCHAR(64) NOT NULL UNIQUE,

    received_at              TIMESTAMPTZ NOT NULL,
    sender_domain             VARCHAR(255) NOT NULL,
    platform                  VARCHAR(100),

    company                    VARCHAR(255),
    role_title                 VARCHAR(255),
    update_type                VARCHAR(50) NOT NULL,
    summary                    TEXT,
    is_urgent                  BOOLEAN NOT NULL DEFAULT FALSE,

    manual_status               VARCHAR(50),

    sheet_synced_at             TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_email_classifications_received_at ON email_classifications (received_at);
```

`update_type` is a `VARCHAR`, not a native Postgres `ENUM`, so new categories can be added without an `ALTER TYPE` migration — the allowed set is validated in the application layer instead. `manual_status` is left for the user to fill in manually in the Sheet (e.g. `responded`, `ignored`), building a longitudinal view of the application funnel over time.

## WhatsApp notification

Uses the official Meta WhatsApp Cloud API (chosen over Twilio: one fewer intermediary, no ongoing per-message cost within the free test-number tier, and a more direct integration to demonstrate). Because the daily message is business-initiated (not a reply to a user message), it must be sent via a pre-approved Message Template with dynamic variables.

**v1 (current scope):** plain-text summary filled into the template (counts by update type, companies involved). No hosting dependency required.

**v2 (planned follow-up):** the template's call-to-action button links to a hosted HTML report for a richer view. Deferred because it requires an additional piece of infrastructure (static file hosting with a non-guessable/private URL) not needed for v1.

A separate, distinct template is used for the job-failure alert described in the pipeline steps above.

## Security posture

- **Secrets:** OAuth tokens, service account credentials, the Neon connection string, and the WhatsApp access token are never committed. They live in GitHub Secrets and are injected as environment variables at runtime. GitHub Secret Scanning + push protection is enabled on the repository.
- **Least privilege:** Gmail access is `gmail.readonly` only; the Sheets service account is shared with a single specific spreadsheet, not the whole Drive; the Neon database user has only the permissions this application needs.
- **SQL injection:** all persistence goes through Spring Data JPA / parameterized queries; no manual string concatenation into SQL.
- **Dependency vulnerabilities:** Dependabot is enabled on the repository; OWASP Dependency-Check (or equivalent) runs as a CI step and can fail the build on critical findings.
- **Static analysis:** Semgrep (`semgrep --config auto .`) and SonarCloud run in CI, covering OWASP Top 10 patterns and general code quality/security smells.
- **Container hardening:** the Postgres dev container uses an official minimal image; if the application is ever containerized, it would run as a non-root user from a minimal JRE base image, with a `.dockerignore` excluding any credential files.
- **CI hardening:** third-party GitHub Actions are pinned to specific versions; workflow `permissions` are scoped explicitly (`contents: read` by default) rather than left at the broad default.
- **Transport security:** the Neon connection enforces TLS.
- **Logging:** logs record metadata only (e.g. "processed email from domain X, classified as Y, id Z") — never full email bodies, tokens, or credentials.

## Testing strategy

- **Unit tests (JUnit 5 + AssertJ):** the domain and application layers (the classifier, digest building) are tested with no mocking framework, since they depend on nothing — fakes are enough for the ports. The Gmail adapter's MIME and base64 handling is tested the same way, by building API objects by hand.
- **Integration tests (Testcontainers):** the persistence adapter (`PostgresRepositoryAdapter`) is tested against a real, disposable PostgreSQL container — this is what validates real behavior such as the `gmail_message_id` unique constraint that idempotency depends on.
- **Out of scope for now:** end-to-end tests hitting the real external APIs (Gmail, WhatsApp, Sheets) in CI — this would require production credentials in CI for limited benefit; confidence in the full pipeline comes from the actual daily run instead.

## CI/CD

Two separate GitHub Actions workflows:

- **`ci.yml`** — triggered on `push`/`pull_request`. Runs the build, unit tests, integration tests (with a Testcontainers-managed Postgres), Semgrep, and Dependabot/dependency checks. This is what guards the `main` branch.
- **`daily-run.yml`** — triggered on a daily `schedule` cron. Runs the production pipeline described above against the real external services.

Keeping them separate avoids mixing "code validation" with "operational execution" in the same workflow, and each keeps its own independent run history in the Actions tab.

## License

MIT — the repository is public, and the intent is for the code to be freely readable, forkable, and reusable by others.

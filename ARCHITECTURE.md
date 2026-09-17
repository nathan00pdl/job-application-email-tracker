# Architecture

## Overview

A backend system that scans a personal Gmail inbox once a day, identifies emails related to job applications, classifies them by matching the phrases hiring platforms use, persists the results as the source of truth in a PostgreSQL database, mirrors them into a Google Sheet used as a manual control dashboard, and sends a daily WhatsApp summary.

This is a personal study project built to demonstrate backend engineering practices (Java, Spring Boot, PostgreSQL, Docker, CI/CD, hexagonal architecture, automated security checks) as a portfolio piece. It intentionally favors clarity, documented decisions, and complete implementation of standard practices over minimal effort.

## Tech stack

- **Language / framework:** Java 25, Spring Boot
- **Build tool:** Maven, pinned with the wrapper (`./mvnw`) so every machine and CI use the same version
- **Database:** PostgreSQL 17 — Neon (free tier, São Paulo region) in production, a Docker Compose container locally, and a Testcontainers instance during the integration tests. The three share a schema through Flyway and nothing else: no data moves between them
- **Schema migrations:** Flyway
- **Containerization:** Docker Compose, used for the local PostgreSQL instance only (the application itself runs directly via Maven/JVM locally, and inside the GitHub Actions runner in production — containerizing the app adds no benefit in either environment)
- **Scheduling / execution:** GitHub Actions (`schedule` cron trigger, daily at 03:17 São Paulo time — early, so the digest arrives by 06:00 despite GitHub's delays), no always-on server
- **External integrations:** Gmail API, Google Sheets API, and Meta WhatsApp Cloud API (from Meta's free test number)

## Language convention

All code, comments, configuration, commit messages, and documentation are written in English. Two things are not, because a person reads them rather than a developer: data captured from emails (sender content, extracted summaries), stored and displayed in its original language since most applications are submitted in pt-BR with occasional en-US emails; and the text of the daily WhatsApp digest, written in pt-BR for its reader.

## Architecture style: Hexagonal (Ports & Adapters)

The system is fundamentally an orchestrator around external integrations (Gmail, Postgres, Sheets, WhatsApp), so a plain layered (controller/service/repository) structure would blur business logic with integration details. Hexagonal architecture keeps the domain free of framework and vendor concerns:

- **`domain`** — core models and business rules. No framework dependencies, no knowledge of Gmail/Postgres/Sheets/WhatsApp. Contains the classifier (see below) and the digest-building logic.
- **`application`** — use cases that orchestrate the domain through ports (e.g. `RunDailyScanUseCase`).
- **`ports`** — interfaces the domain/application layer depends on:
  - `EmailSourcePort` — fetch emails received after a given instant (how far back to look is the use case's decision, not the port's)
  - `PersistencePort` — read/write `EmailClassification` records
  - `SpreadsheetPort` — sync records to the dashboard
  - `NotificationPort` — deliver the daily digest; returning means the channel accepted it
- **`adapters`** — concrete implementations of each port:
  - `adapters/runner` → `DailyScanRunner implements ApplicationRunner` — the driving side:
    the only thing that starts a run today
  - `adapters/gmail` → `GmailApiAdapter implements EmailSourcePort`
  - `adapters/persistence` → `PostgresRepositoryAdapter implements PersistencePort` (Spring Data JPA)
  - `adapters/sheets` → `GoogleSheetsAdapter implements SpreadsheetPort`
  - `adapters/whatsapp` → `MetaWhatsAppAdapter implements NotificationPort`, sending the approved template through Meta's Cloud API; `DigestMessage` fills its blanks from a digest

Swapping an integration (e.g. WhatsApp provider, database host) means writing a new adapter — the domain and application layers are untouched. This also makes the domain trivially testable without mocking frameworks, since it only depends on interfaces.

## Daily pipeline

Every step below runs every day. A run can also be started by hand, locally with
`./mvnw spring-boot:run` or on GitHub from the Actions tab.

1. GitHub Actions triggers `daily-run.yml` on a daily cron schedule; a fresh Ubuntu runner is provisioned.
2. Secrets are injected as environment variables from GitHub Secrets — locally they come from `.env`.
3. `GmailApiAdapter` fetches emails received since the last scan finished reading, with an hour of overlap (`gmail.readonly` scope only). The starting point comes from the `scan_runs` table rather than a fixed window, so a gap of any length — a missed run, an expired token — closes itself on the next run. A database that has never completed a scan starts 26 hours back. Re-reading costs nothing, because step 4 discards what was already seen.
4. Each email is checked against `gmail_message_id` in the database — if it already exists, it is skipped (idempotency, see below).
5. `EmailClassifier` reads each email. It answers with a classification, or with nothing when the email is not about a job application — and those are dropped without being recorded.
6. Classifications are persisted in Postgres, the source of truth.
7. New/unsynced records (`sheet_synced_at IS NULL`) are written to the Google Sheet.
8. Everything not yet delivered (`digest_sent_at IS NULL`) is added up into a `DailyDigest` and sent as a WhatsApp template — every day, even when there is nothing new. The rows are marked as delivered only after Meta accepts the message, so a failed send leaves them for the next run's digest.
9. If any external service is unreachable and prevents completion (not a per-email classification failure), the job fails visibly: the run is marked as failed in the Actions tab, and a second job opens a GitHub issue — or comments on the one already open — naming the cause when the run can tell it: an expired Gmail token, or one of the WhatsApp refusals the adapter reports — the token, access to the test number, or a template that is not active.
10. The runner is destroyed. Nothing stays running between executions.

Per-item failures (a single email failing classification) are caught and logged individually; they do not abort processing of the remaining emails in that run. Failures connecting to a whole service (e.g. Postgres unreachable) abort the run, since nothing can be persisted.

## Classification

`EmailClassifier` matches phrases against the subject and body, and reads the sender's domain.

This works because the emails are templates: hiring platforms send the same sentences every time — *"recebemos sua candidatura"*, *"infelizmente não seguiremos"*, *"gostaríamos de convidá-lo"*. Matching phrases against templated text is a different proposition from matching them against free-form writing, and this design would be a poor one in another domain.

An email counts as being about an application when it either carries a phrase showing one exists, or comes from a known hiring platform. Those systems mostly write to people already in a process, but not only — a ninety-day read of real mail found Gupy sending its own marketing from the same domain as its application updates. Two things keep that out: advert phrases veto a message whatever its sender, and a short list of marketing sending addresses, such as `inbound.gupy.com.br`, stop the sender's domain from proving anything on its own. Evidence is also looked for with footers taken out: a sentence explaining why an email was sent — *"you have received this email because you applied for a job on our website"* — goes on everything a company sends to past applicants, so its *"you applied"* proves nothing. Being about a job is not the same as being about an application: a newsletter listing openings mentions vacancies on every line and is dropped.

Phrases are read in order of finality — offer, rejection, interview, technical test, information request, acknowledgement — because a rejection almost always names the interview it is rejecting you after.

The classifier never fills in the company or the role. Guessing those from phrases would produce values that look extracted but are not, and a wrong company is worse than an empty one. The platform comes from the sender's domain, which is a fact rather than a guess, and the subject column holds the subject line, copied as the sender wrote it.

**Emails that are not about an application are never stored.** The existence of a record is the verdict, so no column says whether it counts. This also keeps unrelated personal mail — invoices, newsletters, private messages — out of the database entirely.

An earlier design ran a cheap rule filter first and sent only its matches to a language model, keeping both answers so that a disagreement between them stayed visible. The filter existed to control the cost of the model. With no model, there is nothing to filter for and no second opinion to compare against, so both were removed (migration `V2`).

## Idempotency

`email_classifications.gmail_message_id` is a unique constraint. Before classifying an email, the pipeline checks whether that message ID already exists. This makes the daily run safe to re-trigger without duplicating records or duplicate-notifying — running twice in a day, or re-running after a partial failure, has no side effects beyond processing whatever wasn't processed yet.

## Database schema

The shape below is what the tables hold after all six migrations. `V1` created `email_classifications`; `V2` dropped the four columns that supported the two-signal design described above; `V3` renamed `summary` to `subject`, because it holds the subject line and never held a summary; `V4` dropped `manual_status`, which nothing could fill; `V5` added `digest_sent_at`; and `V6` created `scan_runs`.

```sql
CREATE TABLE email_classifications (
    id                   BIGSERIAL    PRIMARY KEY,
    gmail_message_id     VARCHAR(64)  NOT NULL UNIQUE,

    received_at          TIMESTAMPTZ  NOT NULL,
    sender_domain        VARCHAR(255) NOT NULL,
    platform             VARCHAR(100),

    company              VARCHAR(255),
    role_title           VARCHAR(255),
    update_type          VARCHAR(50)  NOT NULL,
    subject              TEXT,
    is_urgent            BOOLEAN      NOT NULL DEFAULT FALSE,

    sheet_synced_at      TIMESTAMPTZ,
    digest_sent_at       TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_email_classifications_received_at ON email_classifications (received_at);

CREATE TABLE scan_runs (
    id                   BIGSERIAL    PRIMARY KEY,
    completed_at         TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_scan_runs_completed_at ON scan_runs (completed_at DESC);
```

`sheet_synced_at` and `digest_sent_at` are two independent queues: a null means the row has not reached the spreadsheet yet, or has not been delivered in a digest yet, and marking one never moves the other. `scan_runs` is append-only — one row per completed read of the mailbox, never updated — and the next run starts reading from the latest `completed_at`, which is what lets a gap of any length close itself.

`update_type` is a `VARCHAR`, not a native Postgres `ENUM`, so new categories can be added without an `ALTER TYPE` migration — the allowed set is validated in the application layer instead. Notes about what happened next are written by hand in the spreadsheet, from column J onwards, which the sync never touches — the database has no column for them, and `V4` removed the one that tried.

## WhatsApp notification

Uses the official Meta WhatsApp Cloud API (chosen over Twilio: one fewer intermediary, no ongoing per-message cost within the free test-number tier, and a more direct integration to demonstrate). Because the daily message is business-initiated (not a reply to a user message), it must be sent via a pre-approved Message Template with dynamic variables.

The message is built around what needs doing. `UpdateType` says which kinds wait on the reader — an offer, an interview invitation, a technical test, a request for information — and `DailyDigest` carries those emails, plus any urgent one, as `ActionNeeded`: the Gmail id, the kind, the platform or else the sender's domain, the arrival and the urgency, most important first. Confirmations and rejections are counted but not listed. It names no company, because the classifier does not read one out of the email, and it needs no hosting of its own: each item links straight to its email in Gmail.

The template is `resumo_diario_acoes`, in pt-BR, with fifteen blanks: the period, the total, the counts by kind as one line (*"2 entrevistas, 10 confirmações de inscrição"*), ten places for the emails that wait on the reader, how many more there are, and a link to the spreadsheet. `DigestMessage` fills them. A value cannot hold a line break, so each email gets a place of its own in the fixed text; one template serves every day, and an unused place holds a dash. The spreadsheet id is read from the sync's own setting, so the link can never point at a different sheet.

## Security posture

- **Secrets:** OAuth tokens, service account credentials, the Neon connection string, and the WhatsApp access token are never committed. They live in GitHub Secrets and are injected as environment variables at runtime. GitHub Secret Scanning + push protection is enabled on the repository.
- **Least privilege:** Gmail access is `gmail.readonly` only; the Sheets service account is shared with a single specific spreadsheet, not the whole Drive. The application connects to Neon as `jobtracker`, a role created with SQL for this purpose, rather than as `neondb_owner`, the role Neon creates with the project, which belongs to `neon_superuser`. `jobtracker` can log in and use and create tables in the `public` schema — `CREATE` is needed because Flyway runs with the application's own credentials — and nothing else: it cannot create roles or databases. The WhatsApp token belongs to a system user with the Employee role, which sees only the test WhatsApp account and holds only `whatsapp_business_messaging`: it can send messages, and nothing else.
- **SQL injection:** all persistence goes through Spring Data JPA / parameterized queries; no manual string concatenation into SQL.
- **Dependency vulnerabilities:** Dependabot is enabled on the repository, with its alerts and
  automatic fixes turned on. On every pull request, `dependency-review.yml` inspects the
  dependencies the change adds and blocks the merge on anything `moderate` or above; low
  findings are reported in the summary without blocking, so the gate stays about things worth
  acting on.
- **Static analysis:** two tools, both on every pull request.
  - **Semgrep**, in `ci.yml`, against the `p/java` and `p/security-audit` rule sets. They are
    named explicitly rather than using `--config auto`, which needs an account and reports usage
    back. A finding fails the job (`--error`) and is also uploaded to the Security tab.
  - **CodeQL**, in `codeql.yml`, which compiles the project and follows data flow — it finds
    what pattern matching cannot. Both report zero findings today.
- **Container hardening:** the Postgres dev container uses an official minimal image; if the application is ever containerized, it would run as a non-root user from a minimal JRE base image, with a `.dockerignore` excluding any credential files.
- **CI hardening:** every GitHub Action the workflows use is pinned to a full commit SHA, with its version in a comment beside it — a tag can be moved to point at other code, a commit cannot; workflow `permissions` are scoped explicitly (`contents: read` by default) rather than left at the broad default. The one job allowed to write anything — it opens an issue when the daily run fails — runs no project code, so no dependency ever holds a token that can.
- **Transport security:** Neon refuses connections without TLS, and the connection string asks for it as well (`sslmode=require`), so the credentials and the data travel encrypted between the runner and the database.
- **Logging:** logs record counts and times only — how many emails were read, stored and skipped, and the digest's totals by kind and platform and how many emails ask for action — never a subject, a body, a sender, a token, or a credential. The one exception is the id of a message that could not be read, which means nothing without access to the mailbox. This goes beyond good practice: the repository is public, so anyone can read the log of every daily run. GitHub also hides the value of every secret wherever it appears in a log, which is why Flyway's line about the database shows `***` instead of the URL. It hides a value only as a whole, though, and a connection error prints the database's host on its own, so the daily run registers the host as one more value to hide before anything else runs.

## Testing strategy

- **Unit tests (JUnit 5 + AssertJ):** the domain and application layers (the classifier, digest building) are tested with no mocking framework, since they depend on nothing — fakes are enough for the ports. The Gmail adapter's MIME and base64 handling is tested the same way, by building API objects by hand.
- **Integration tests (Testcontainers):** the persistence adapter (`PostgresRepositoryAdapter`) is tested against a real, disposable PostgreSQL container — this is what validates real behavior such as the `gmail_message_id` unique constraint that idempotency depends on.
- **Checks against the real mailbox (opt-in):** two tests read the author's own Gmail and run only when `GMAIL_REFRESH_TOKEN` is set, so CI skips them. `GmailApiManualVerificationTest` confirms the credentials work. `ClassifierAgainstRealMailboxTest` runs the classifier over a chosen number of days of real mail and prints what it kept; a person reads the result, because whether a real email is about an application is exactly what the test cannot know by itself. That is how the rules were measured, and corrected, against 90 days of real mail.
- **Out of scope for now:** end-to-end tests hitting the real external APIs (Gmail, WhatsApp, Sheets) in CI — this would require production credentials in CI for limited benefit; confidence in the full pipeline comes from the actual daily run instead.

## CI/CD

Three GitHub Actions workflows guard `main`, each triggered on `push`/`pull_request`:

- **`ci.yml`** — two independent jobs that run at the same time: the build with unit and integration tests (the integration ones start their own Postgres through Testcontainers), and Semgrep.
- **`codeql.yml`** — compiles the project and runs CodeQL's data-flow analysis.
- **`dependency-review.yml`** — inspects the dependencies a pull request adds.

A fourth runs the pipeline itself rather than checking the code:

- **`daily-run.yml`** — every day at 06:17 UTC (03:17 in São Paulo — early and off the hour, because GitHub starts scheduled runs late, sometimes by hours), and by hand from the Actions tab, against the real external services. The secrets are handed to the step that runs the scan and to nothing else — apart from the database URL, which the first step reads to hide the database's host in the log — and a `concurrency` group keeps a manual run from overlapping the scheduled one. When the scan fails, a second job opens an issue about it; that job is the only one in any workflow allowed to write to the repository, and it runs no project code.

Keeping execution separate from validation is deliberate: it avoids mixing "is this code
correct" with "did today's run work" in one workflow, and each keeps its own run history in
the Actions tab. The three validation workflows are split from each other for a plainer
reason — independent jobs run in parallel, and neither waits on the other.

## License

MIT — the repository is public, and the intent is for the code to be freely readable, forkable, and reusable by others.

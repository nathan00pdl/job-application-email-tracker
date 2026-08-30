# job-application-email-tracker

A daily job that scans a Gmail inbox for emails about job applications, classifies them with a rules filter + Claude, saves the results in PostgreSQL, copies them into a Google Sheet, and sends a daily summary over WhatsApp.

See [`ARCHITECTURE.md`](./ARCHITECTURE.md) for the full design.

## Requirements

- Java 25
- Maven 3.9+
- Docker (for the local PostgreSQL instance, and for Testcontainers during the build)

## Local setup

Create your `.env` from the template and fill in the blank password:

```bash
cp .env.example .env
```

Start the database and wait until it accepts connections:

```bash
docker compose up -d --wait
```

Only PostgreSQL runs in a container. The application runs directly on the JVM, both
locally and in CI — see [`ARCHITECTURE.md`](./ARCHITECTURE.md).

## Build & test

```bash
mvn clean verify
```

The integration tests start their own temporary PostgreSQL container, so
`docker compose` does not have to be running for them.

## Run locally

Flyway applies any pending migrations on startup:

```bash
set -a && source .env && set +a
mvn spring-boot:run
```

The database username and password have no default value: the application will not
start if `DATASOURCE_USERNAME` and `DATASOURCE_PASSWORD` are not set. The same holds for
`GMAIL_CLIENT_ID`, `GMAIL_CLIENT_SECRET` and `GMAIL_REFRESH_TOKEN`.

## Checking the Gmail credentials

One test reads the real mailbox. It runs only when `GMAIL_REFRESH_TOKEN` is set, so CI
skips it:

```bash
set -a && source .env && set +a
mvn test -Dtest=GmailApiManualVerificationTest
```

It prints how many emails were read and their sender domains — no subjects, no bodies.

## Checking the classifier

Another test sends one made-up email to the real Anthropic API and prints the answer. It
runs only when `ANTHROPIC_API_KEY` is set, and it costs a fraction of a cent:

```bash
set -a && source .env && set +a
mvn test -Dtest=ClaudeApiManualVerificationTest
```

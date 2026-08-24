# job-application-email-tracker

A daily job that scans a Gmail inbox for job-application-related emails, classifies them with a rules filter + Claude, persists the results in PostgreSQL, mirrors them into a Google Sheet, and sends a WhatsApp daily summary.

See [`ARCHITECTURE.md`](./ARCHITECTURE.md) for the full design.

## Requirements

- Java 25
- Maven 3.9+
- Docker (for the local PostgreSQL instance)

## Local setup

Create your `.env` from the template and fill in the blank password:

```bash
cp .env.example .env
```

Start the database and wait until it is accepting connections:

```bash
docker compose up -d --wait
```

Only PostgreSQL is containerized. The application runs directly on the JVM, both
locally and in CI — see [`ARCHITECTURE.md`](./ARCHITECTURE.md).

## Build & test

```bash
mvn clean verify
```

## Run locally

```bash
mvn spring-boot:run
```

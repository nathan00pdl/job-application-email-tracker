# job-application-email-tracker

A daily job that scans a Gmail inbox for job-application-related emails, classifies them with a rules filter + Claude, persists the results in PostgreSQL, mirrors them into a Google Sheet, and sends a WhatsApp daily summary.

See [`ARCHITECTURE.md`](./ARCHITECTURE.md) for the full design.

## Requirements

- Java 25
- Maven 3.9+

## Build & test

```bash
mvn clean verify
```

## Run locally

```bash
mvn spring-boot:run
```

# job-application-email-tracker

A daily job that scans a Gmail inbox for emails about job applications, classifies them with a set of rules that runs locally, saves the results in PostgreSQL, and copies them into a Google Sheet. A daily summary over WhatsApp is still to come.

See [`ARCHITECTURE.md`](./ARCHITECTURE.md) for the full design.

## Architecture

![Hexagonal architecture: the domain and the use case inside the hexagon, three ports on its edges, the adapters that implement them outside, and the systems they talk to.](docs/architecture.svg)

The three dashed arrows are the point. They run from the adapters **into** the ports:
the interfaces are declared on the inside, in the language of the problem, and the code
that talks to Gmail, PostgreSQL and Google Sheets adapts itself to them. Nothing in
`domain` or `application` names a vendor, which is why the whole daily run can be tested
against lists in memory.

The dashed slot on the top edge is there on purpose: this project has no driving port yet.
`DailyScanRunner` calls `RunDailyScanUseCase` directly, because a scheduled run is the
only way to start the application. An HTTP endpoint or a CLI command is what would turn
that slot into an interface.

`PipelineConfiguration` sits outside the hexagon because it is the wiring: it builds the
objects and connects them, which is what lets `domain` and `application` carry no
framework annotation at all.

`application` holds the order of the steps and no business rules; `domain` holds the
rules and knows nothing about order, storage or the network.

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

## How emails are classified

The classifier runs locally and matches the phrases hiring platforms use — "recebemos sua
candidatura", "infelizmente não seguiremos", "gostaríamos de convidá-lo" — plus the
sender's domain. There is no external service, no API key and no cost.

Emails that are not about a job application are dropped without being recorded, so
unrelated mail never reaches the database.

## Build & test

```bash
./mvnw clean verify
```

`./mvnw` is the Maven wrapper: a script kept in the repository that downloads and runs
the exact Maven version pinned in `.mvn/wrapper/maven-wrapper.properties`. You do not
need Maven installed, and this machine, anyone else's and CI all build with the same
version. The leading `./` matters — it means the script in this directory, not a command
on your `PATH`.

The integration tests start their own temporary PostgreSQL container, so
`docker compose` does not have to be running for them.

## Run locally

Flyway applies any pending migrations on startup:

```bash
set -a && source .env && set +a
./mvnw spring-boot:run
```

The database username and password have no default value: the application will not
start if `DATASOURCE_USERNAME` and `DATASOURCE_PASSWORD` are not set. The same holds for
`GMAIL_CLIENT_ID`, `GMAIL_CLIENT_SECRET` and `GMAIL_REFRESH_TOKEN`.

## Running the scan

With the database up and the environment loaded, this reads the mailbox and stores what
it finds:

```bash
docker compose up -d --wait
set -a && source .env && set +a
./mvnw spring-boot:run
```

The application runs the scan once and exits — there is no server to leave running. It
logs how many emails it read, how many it stored, how many it had already seen, and how
many were not about a job application.

To start it without scanning, set `RUN_ON_STARTUP=false`.

## The spreadsheet

Classifications are mirrored into a Google Sheet, which is where notes written by hand
live — the database has no column for *what happened next*.

Setting it up:

1. Enable the Google Sheets API in the same Google Cloud project
2. Create a **service account** and download its JSON key
3. Create a spreadsheet, rename the first tab to `classifications`, and **share the
   spreadsheet with the service account's email address** as an editor
4. Add the first row by hand, as headers:
   `gmail id · received at · sender domain · platform · company · role · update · urgent · summary`
5. Encode the key and put it in `.env`:

```bash
base64 -w0 service-account-key.json
```

A service account is an identity of its own rather than something acting on your behalf.
It reaches exactly the spreadsheets shared with it and nothing else, and it has no
consent that expires — so none of the token renewal that the mailbox needs applies here.

Rows are appended, never rewritten. Column J onwards is left alone, which is where notes
belong.

## Checking the Gmail credentials

One test reads the real mailbox. It runs only when `GMAIL_REFRESH_TOKEN` is set, so CI
skips it:

```bash
set -a && source .env && set +a
./mvnw test -Dtest=GmailApiManualVerificationTest
```

It prints how many emails were read and their sender domains — no subjects, no bodies.

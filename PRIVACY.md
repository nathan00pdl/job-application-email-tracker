# Privacy Policy

Last updated: 2026-08-27

**job-application-email-tracker** ("the app") is a personal, open-source project built
and run by one person, for that person's own mailbox. It has no other users, no company
behind it, and no commercial purpose.

## What the app reads

With your permission, the app reads messages from your own Gmail mailbox using the
`https://www.googleapis.com/auth/gmail.readonly` scope. This scope is read-only: the app
**cannot** send, delete, or change anything in your mailbox, and it does not request any
other Google permission.

Each run looks only at messages received in the last 24 hours.

## What it does with them

1. A local rule filter checks the sender's domain and the subject line to decide whether
   a message might be about a job application. Messages that do not match are ignored and
   nothing about them is stored.
2. For the messages that do match, the subject and body are sent to Anthropic's Claude
   API, which classifies them and extracts fields such as the company, the role, and the
   kind of update. **This means the content of those emails is processed by Anthropic**,
   under Anthropic's own terms and privacy policy.
3. The extracted fields are saved so the same email is never processed twice.

## Where the data is stored

- A private PostgreSQL database hosted on Neon, reachable only with credentials held by
  the author.
- A private Google Sheet owned by the same Google account, used as a dashboard.
- A WhatsApp message with a daily summary, sent to the author's own number.

Access credentials are stored as GitHub Actions secrets and are never committed to this
repository.

## What the app does not do

- It does not sell, share, or publish your data.
- It does not use your data for advertising or for training any model.
- It serves no users other than the account that authorised it.
- It collects no analytics and no tracking data of any kind.

## Retention

Records are kept until the author deletes them. Deleting the database, the spreadsheet,
or the whole Google Cloud project removes the stored data.

## Revoking access

You can revoke this app's access to your Gmail account at any time, without changing your
password, at [myaccount.google.com/permissions](https://myaccount.google.com/permissions).
Access stops immediately.

## Contact

Open an issue at
[github.com/nathan00pdl/job-application-email-tracker](https://github.com/nathan00pdl/job-application-email-tracker/issues).

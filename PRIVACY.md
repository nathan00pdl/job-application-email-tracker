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

1. The app reads the subject and body **on your machine, or on the GitHub Actions runner
   that runs the daily job**, and matches them against a list of phrases. Nothing is sent
   to any third party for analysis: there is no external classifier and no AI service
   involved.
2. Emails that are not about a job application are ignored, and **nothing about them is
   stored** — not the subject, not the sender, not the fact that they were read.
3. For the ones that are, a small set of fields is saved: the Gmail message id, the time
   it arrived, the sender's domain, the hiring platform, the kind of update, and the
   subject line as a summary.

## Where the data is stored

- A private PostgreSQL database hosted on Neon, reachable only with credentials held by
  the author.
- A private Google Sheet owned by the same Google account, used as a dashboard.
- A WhatsApp message with a daily summary, sent to the author's own number.

Access credentials are stored as GitHub Actions secrets and are never committed to this
repository.

## What the app does not do

- It does not sell, share, or publish your data.
- It does not use your data for advertising, and no data ever reaches a model of any
  kind.
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

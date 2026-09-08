# Privacy Policy

Last updated: 2026-09-08

**job-application-email-tracker** ("the app") is a personal, open-source project built
and run by one person, for that person's own mailbox. It has no other users, no company
behind it, and no commercial purpose.

## What the app reads

With your permission, the app reads messages from your own Gmail mailbox using the
`https://www.googleapis.com/auth/gmail.readonly` scope. This scope is read-only: the app
**cannot** send, delete, or change anything in your mailbox, and it does not request any
other Google permission.

Each run looks only at messages received in the last 26 hours. The window is slightly
wider than a day so that a late or failed run does not skip messages.

## What it does with them

1. The app reads the subject and body **on the author's own machine**, and matches them
   against a list of phrases. (A scheduled run on a GitHub Actions runner is planned; it
   does not exist yet.) Nothing is sent
   to any third party for analysis: there is no external classifier and no AI service
   involved.
2. Emails that are not about a job application are ignored, and **nothing about them is
   stored** — not the subject, not the sender, not the fact that they were read.
3. For the ones that are, a small set of fields is saved: the Gmail message id, the time
   it arrived, the sender's domain, the hiring platform, the kind of update, and the
   subject line.

## Where the data is stored

- A private PostgreSQL database hosted on Neon, reachable only with credentials held by
  the author.
- A private Google Sheet owned by the same Google account, used as a dashboard.

A daily summary sent to the author's own number over WhatsApp is planned and is not built
yet. No message is sent anywhere today.

Access credentials live in a local `.env` file on the author's machine, which is excluded
from version control and never committed. When the scheduled run exists, they will be held
as GitHub Actions secrets instead.

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

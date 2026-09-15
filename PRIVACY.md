# Privacy Policy

Last updated: 2026-09-15

**job-application-email-tracker** ("the app") is a personal, open-source project built
and run by one person, for that person's own mailbox. It has no other users, no company
behind it, and no commercial purpose.

## What the app reads

With your permission, the app reads messages from your own Gmail mailbox using the
`https://www.googleapis.com/auth/gmail.readonly` scope. This scope is read-only: the app
**cannot** send, delete, or change anything in your mailbox, and it does not request any
other Google permission.

Each run looks only at messages received since the previous run finished reading, with an
hour of overlap. After a gap — a run that failed, or a day the app did not run — the next
run reads the whole gap, so that no message is skipped. The very first run looks back 26
hours.

## What it does with them

1. The app reads the subject and body and matches them against a list of phrases. That
   happens once a day on a GitHub Actions runner — a temporary machine that is destroyed
   when the run ends — or on the author's own machine when run by hand. Nothing is sent
   to any third party for analysis: there is no external classifier and no AI service
   involved.
2. Emails that are not about a job application are ignored, and **nothing about them is
   stored** — not the subject, not the sender, not the fact that they were read.
3. For the ones that are, a small set of fields is saved: the Gmail message id, the time
   it arrived, the sender's domain, the hiring platform, the kind of update, and the
   subject line.

## Where the data is stored

- A private PostgreSQL database hosted on Neon, in its São Paulo region, reachable only
  with credentials held by the author.
- A private Google Sheet owned by the same Google account, used as a dashboard.

Each run also leaves a log on GitHub Actions. Because the repository is public, anyone can
read that log, so it holds only counts, dates and the names of hiring platforms — never a
subject, a sender, the text of a message, or any credential.

## The daily summary

Once a day, a summary is sent to the author's own phone number over WhatsApp, through
Meta's WhatsApp Cloud API. It holds the same kind of information as the log: how many
updates arrived, how many of each kind, how many are urgent, the names of the hiring
platforms, and the dates they cover. It never holds a subject, a sender, or any text from
a message. Meta receives it only to deliver it.

Access credentials are held as GitHub Actions secrets for the scheduled run, and in a local
`.env` file on the author's machine for runs started by hand. Neither is ever committed to
version control.

## What the app does not do

- It does not sell or publish your data. The daily summary above is the only thing that
  leaves for a messaging service, and it goes to the author's own number.
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

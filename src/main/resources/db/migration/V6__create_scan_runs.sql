-- When a scan last finished reading the mailbox.
--
-- Until now each run looked back a fixed 26 hours: a day plus a little slack, enough to
-- cover one late or failed run. It cannot cover two. A gap of more than 26 hours loses
-- the emails inside it permanently and without a sound, because the only thing that
-- looks backwards is the duplicate check, and that only skips what was already seen —
-- nothing ever goes looking for what was missed.
--
-- That was tolerable while runs were started by hand. With a daily schedule it stops
-- being tolerable: the OAuth app is in Testing, so Google expires the refresh token
-- every seven days, and between the expiry and the renewal there are failing runs.
--
-- Append-only, one row per completed read. The answer is MAX(completed_at), so there is
-- no UPDATE, no contention, and no odd constraint to keep a single row single. The
-- history comes free, and answering "did it run yesterday?" will matter once nobody is
-- watching it run.
CREATE TABLE scan_runs (
    id           BIGSERIAL   PRIMARY KEY,
    completed_at TIMESTAMPTZ NOT NULL
);

-- The only query this table serves reads the latest row.
CREATE INDEX idx_scan_runs_completed_at ON scan_runs (completed_at DESC);

-- The column never held a summary. It holds the subject line, copied as the sender
-- wrote it. The name is left over from an earlier design where a language model was
-- meant to write a real summary into it; that step was removed, the behaviour became
-- "copy the subject", and the name stayed.
--
-- RENAME keeps the data: this is not a new column, so nothing is copied and nothing is
-- lost. The spreadsheet is not touched either, because the adapter appends values by
-- position and never writes the header row.
ALTER TABLE email_classifications RENAME COLUMN summary TO subject;

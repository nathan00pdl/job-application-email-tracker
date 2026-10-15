-- Drops company and role_title, which nothing ever filled.
--
-- The classifier does not read the company or the role out of an email, on purpose:
-- guessing them from phrases would produce values that look extracted but are not. So
-- both columns were written as NULL on every row, and copied to the spreadsheet as two
-- empty columns. The subject, kept as the sender wrote it, already names both for a
-- person to read.
--
-- Nothing is lost. The only code that ever filled them was a classifier removed before
-- the production database existed; every row there holds NULL in both.
--
-- The same reasoning as V4, which dropped manual_status for the same reason: a column
-- that nothing can fill is a promise the schema cannot keep.
ALTER TABLE email_classifications
    DROP COLUMN company,
    DROP COLUMN role_title;

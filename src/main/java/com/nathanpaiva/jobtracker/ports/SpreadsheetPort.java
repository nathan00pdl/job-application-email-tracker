package com.nathanpaiva.jobtracker.ports;

import java.util.List;

import com.nathanpaiva.jobtracker.domain.EmailClassification;

/**
 * Where classifications are mirrored for a person to read and annotate.
 *
 * <p>The spreadsheet is a copy, never the source of truth — that stays in the database.
 * What the spreadsheet adds is a column the software never touches: a place to write by
 * hand what happened next, building a view of the funnel over months that no automatic
 * field could produce.
 */
public interface SpreadsheetPort {

    /**
     * Adds these classifications to the end of the sheet, in the order given.
     *
     * <p>Appending rather than rewriting is what keeps hand-written notes safe. A sync
     * that rebuilt the sheet from the database would erase every one of them.
     */
    void append(List<EmailClassification> classifications);
}

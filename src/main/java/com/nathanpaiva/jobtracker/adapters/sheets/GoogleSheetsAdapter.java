package com.nathanpaiva.jobtracker.adapters.sheets;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.nathanpaiva.jobtracker.domain.EmailClassification;
import com.nathanpaiva.jobtracker.ports.SpreadsheetPort;

/**
 * Appends classifications to a Google Sheet.
 *
 * <p>Nine columns, A to I, in a fixed order. Column J onwards is left alone: that is
 * where notes written by hand live, and appending never reaches them.
 */
@Component
class GoogleSheetsAdapter implements SpreadsheetPort {

    private static final Logger log = LoggerFactory.getLogger(GoogleSheetsAdapter.class);

    /**
     * Dates are written as text in a fixed format rather than left to the spreadsheet to
     * interpret. A sheet reading "03/09/2026" guesses the order of day and month from
     * the account's locale, and guesses differently for the next reader.
     */
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss 'UTC'")
                    .withZone(java.time.ZoneOffset.UTC);

    private final Sheets sheets;
    private final String spreadsheetId;
    private final String range;

    GoogleSheetsAdapter(Sheets sheets, GoogleSheetsProperties properties) {
        this.sheets = sheets;
        this.spreadsheetId = properties.spreadsheetId();
        this.range = properties.sheetName() + "!A:I";
    }

    @Override
    public void append(List<EmailClassification> classifications) {
        if (classifications.isEmpty()) {
            return;
        }

        ValueRange rows = new ValueRange()
                .setValues(classifications.stream().map(GoogleSheetsAdapter::asRow).toList());

        try {
            sheets.spreadsheets().values()
                    .append(spreadsheetId, range, rows)
                    // RAW stores what we send. USER_ENTERED would let the sheet reinterpret
                    // it — turning a subject that opens with "=" into a formula, and a
                    // company written as "1-2" into a date.
                    .setValueInputOption("RAW")
                    .setInsertDataOption("INSERT_ROWS")
                    .execute();

            // What was sent, not what came back. Reading the response to log it means a
            // field being absent turns a successful write into a failed run.
            log.info("appended {} rows to {}", classifications.size(), range);
        } catch (IOException e) {
            throw new UncheckedIOException("could not write to the spreadsheet", e);
        }
    }

    /**
     * The Gmail id comes first so a row can always be traced back to the email it came
     * from, and so a duplicate is recognisable at a glance.
     */
    private static List<Object> asRow(EmailClassification classification) {
        return List.of(
                classification.gmailMessageId(),
                TIMESTAMP.format(classification.receivedAt()),
                classification.senderDomain(),
                orEmpty(classification.platform()),
                orEmpty(classification.company()),
                orEmpty(classification.roleTitle()),
                classification.updateType().name(),
                classification.urgent() ? "yes" : "no",
                orEmpty(classification.summary()));
    }

    /** The Sheets API refuses nulls in a row; an empty cell is written as an empty string. */
    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}

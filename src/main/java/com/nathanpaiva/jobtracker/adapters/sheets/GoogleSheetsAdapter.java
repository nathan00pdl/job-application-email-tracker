package com.nathanpaiva.jobtracker.adapters.sheets;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.ZoneId;
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
     * The reader of this sheet is in Brazil, so times are shown in their day.
     *
     * <p>An email that arrived at half past nine on a Thursday evening here is already
     * Friday in UTC. Writing it in UTC would put it on the wrong day for the person
     * reading the sheet.
     *
     * <p>Someone running this elsewhere changes this one line.
     */
    private static final ZoneId READER_ZONE = ZoneId.of("America/Sao_Paulo");

    /**
     * Written as text, not left to the spreadsheet to interpret: with RAW input the cell
     * keeps exactly these characters, instead of the sheet deciding from its own locale
     * whether 04-09 is September or April.
     */
    private static final DateTimeFormatter RECEIVED_ON =
            DateTimeFormatter.ofPattern("dd-MM-uuuu HH:mm").withZone(READER_ZONE);

    /**
     * Opens one message in Gmail on the web, for the account signed in first.
     *
     * <p>The same address the WhatsApp digest links to, written out here as well rather
     * than shared: one adapter reaching into another's package to borrow a string would
     * tie two integrations together over a line that has not changed in years.
     */
    private static final String GMAIL_MESSAGE = "https://mail.google.com/mail/u/0/#all/";

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
     * The link to the email comes first, so a row leads straight to the message it came
     * from, and a duplicate is recognisable at a glance. The Gmail id is the end of the
     * link, so nothing the id column used to hold is lost.
     *
     * <p>It is written as plain text, like everything else here. A {@code HYPERLINK}
     * formula would need {@code USER_ENTERED}, the input option that also turns a subject
     * opening with "=" into a formula; the sheet shows a plain address as a link anyway.
     */
    private static List<Object> asRow(EmailClassification classification) {
        return List.of(
                GMAIL_MESSAGE + classification.gmailMessageId(),
                RECEIVED_ON.format(classification.receivedAt()),
                classification.senderDomain(),
                orEmpty(classification.platform()),
                orEmpty(classification.company()),
                orEmpty(classification.roleTitle()),
                classification.updateType().name(),
                classification.urgent() ? "yes" : "no",
                orEmpty(classification.subject()));
    }

    /** The Sheets API refuses nulls in a row; an empty cell is written as an empty string. */
    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}

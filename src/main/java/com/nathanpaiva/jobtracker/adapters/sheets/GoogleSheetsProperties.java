package com.nathanpaiva.jobtracker.adapters.sheets;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * What is needed to write to the spreadsheet.
 *
 * <p>The credentials arrive base64 encoded, which is not decoration. A service account
 * key is a JSON document with newlines inside it, and both an {@code .env} file read by
 * the shell and a value pasted into a form handle a single line far better than a
 * multi-line blob. One encoding works the same locally and in CI.
 *
 * <p>Each one is checked when the application starts, so a missing one stops it there,
 * naming the variable — rather than as an error about base64 that says nothing about
 * which setting is wrong.
 *
 * @param credentials  the service account key, base64 encoded
 * @param spreadsheetId the id from the sheet's URL
 * @param sheetName     the tab to append to. The default tab is named after the account's
 *                      language — "Sheet1" or "Página1" — so this project uses an
 *                      explicit name instead of guessing which one to expect
 */
@ConfigurationProperties(prefix = "google.sheets")
record GoogleSheetsProperties(String credentials, String spreadsheetId, String sheetName) {

    GoogleSheetsProperties {
        requireSet(credentials, "GOOGLE_SHEETS_CREDENTIALS");
        requireSet(spreadsheetId, "GOOGLE_SHEETS_SPREADSHEET_ID");
        requireSet(sheetName, "GOOGLE_SHEETS_SHEET_NAME");
    }

    /**
     * Refuses a setting that was never given a value.
     *
     * <p>Spring does not do this on its own. A {@code ${GOOGLE_SHEETS_CREDENTIALS}} with no such
     * variable is not an error to it: the property keeps that text, placeholder and all.
     * And GitHub Actions hands a missing secret over as an empty string. Either way the
     * application would start, and only fail later against the service, far from the
     * cause.
     *
     * <p>The message names the variable and never repeats the value: the run's log is
     * public, and a value that is half right is still a secret.
     */
    private static void requireSet(String value, String variable) {
        if (value == null || value.isBlank() || value.startsWith("${")) {
            throw new IllegalArgumentException(variable + " is not set");
        }
    }
}

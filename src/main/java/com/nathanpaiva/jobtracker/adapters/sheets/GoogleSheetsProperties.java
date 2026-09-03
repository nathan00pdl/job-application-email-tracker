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
 * @param credentials  the service account key, base64 encoded
 * @param spreadsheetId the id from the sheet's URL
 * @param sheetName     the tab to append to. The default tab is named after the account's
 *                      language — "Sheet1" or "Página1" — so this project uses an
 *                      explicit name instead of guessing which one to expect
 */
@ConfigurationProperties(prefix = "google.sheets")
record GoogleSheetsProperties(String credentials, String spreadsheetId, String sheetName) {
}

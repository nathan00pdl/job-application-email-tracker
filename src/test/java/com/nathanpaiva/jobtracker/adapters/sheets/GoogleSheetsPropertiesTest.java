package com.nathanpaiva.jobtracker.adapters.sheets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Each setting is checked when the application starts. How a placeholder with no variable
 * behind it reaches the record is shown in {@code GmailPropertiesTest}; here it is enough
 * to see that every field is checked, by its own name.
 */
class GoogleSheetsPropertiesTest {

    @Test
    void namesTheMissingSetting() {
        assertThatThrownBy(() -> new GoogleSheetsProperties("${GOOGLE_SHEETS_CREDENTIALS}", "id", "tab"))
                .hasMessage("GOOGLE_SHEETS_CREDENTIALS is not set");
        assertThatThrownBy(() -> new GoogleSheetsProperties("key", "", "tab"))
                .hasMessage("GOOGLE_SHEETS_SPREADSHEET_ID is not set");
        assertThatThrownBy(() -> new GoogleSheetsProperties("key", "id", " "))
                .hasMessage("GOOGLE_SHEETS_SHEET_NAME is not set");
    }
}

package com.nathanpaiva.jobtracker.adapters.whatsapp;

import java.net.URI;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Each setting is checked when the application starts — before the mailbox is read, so a
 * missing token no longer surfaces at the very end of the run. How a placeholder with no
 * variable behind it reaches the record is shown in {@code GmailPropertiesTest}.
 */
class WhatsAppPropertiesTest {

    private static final URI API = URI.create("https://graph.facebook.com/v25.0");

    @Test
    void namesTheMissingSetting() {
        assertThatThrownBy(() -> new WhatsAppProperties("${WHATSAPP_ACCESS_TOKEN}", "123", "5511900000000", API, "sheet"))
                .hasMessage("WHATSAPP_ACCESS_TOKEN is not set");
        assertThatThrownBy(() -> new WhatsAppProperties("token", "", "5511900000000", API, "sheet"))
                .hasMessage("WHATSAPP_PHONE_NUMBER_ID is not set");
        assertThatThrownBy(() -> new WhatsAppProperties("token", "123", null, API, "sheet"))
                .hasMessage("WHATSAPP_RECIPIENT is not set");
        assertThatThrownBy(() -> new WhatsAppProperties("token", "123", "5511900000000", API, ""))
                .hasMessage("GOOGLE_SHEETS_SPREADSHEET_ID is not set");
    }

    /** The token is a secret and the run's log is public: the error never repeats it. */
    @Test
    void neverRepeatsTheValueInTheError() {
        assertThatThrownBy(() -> new WhatsAppProperties("EAAG-real-token", "123", "", API, "sheet"))
                .hasMessageNotContaining("EAAG-real-token");
    }
}

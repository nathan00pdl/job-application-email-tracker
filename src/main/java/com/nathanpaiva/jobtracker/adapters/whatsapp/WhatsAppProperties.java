package com.nathanpaiva.jobtracker.adapters.whatsapp;

import java.net.URI;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * What is needed to send the digest through the WhatsApp Cloud API.
 *
 * <p>The first three have no default, and each is checked when the application starts. A
 * deployment missing one stops there, naming the variable, the same as the Gmail
 * credentials — instead of starting and then failing at the end of the run, after the
 * mailbox was read and the spreadsheet written.
 *
 * <p>They are secrets — the recipient too, because it is a personal phone number. They
 * live in environment variables locally and in GitHub Actions secrets in production, and
 * never in this repository.
 *
 * @param accessToken   a system user token that does not expire, granted only
 *                      {@code whatsapp_business_messaging}
 * @param phoneNumberId which number sends: Meta's free test number, named by the id Meta
 *                      gives it rather than by the number itself
 * @param recipient     who receives the digest, as digits with country and area code
 * @param apiUrl        the Graph API base, with its version; only tests change it
 * @param spreadsheetId the spreadsheet the digest links to. Not a setting of its own: it
 *                      is read from the one the spreadsheet sync writes to, so the two can
 *                      never point at different sheets
 */
@ConfigurationProperties(prefix = "whatsapp")
record WhatsAppProperties(String accessToken, String phoneNumberId, String recipient, URI apiUrl,
                          String spreadsheetId) {

    WhatsAppProperties {
        requireSet(accessToken, "WHATSAPP_ACCESS_TOKEN");
        requireSet(phoneNumberId, "WHATSAPP_PHONE_NUMBER_ID");
        requireSet(recipient, "WHATSAPP_RECIPIENT");
        Objects.requireNonNull(apiUrl, "whatsapp.api-url must not be null");
        requireSet(spreadsheetId, "GOOGLE_SHEETS_SPREADSHEET_ID");
    }

    /**
     * Refuses a setting that was never given a value.
     *
     * <p>Spring does not do this on its own. A {@code ${WHATSAPP_ACCESS_TOKEN}} with no such
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

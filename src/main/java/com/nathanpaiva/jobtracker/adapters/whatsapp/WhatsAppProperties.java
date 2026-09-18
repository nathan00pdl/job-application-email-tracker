package com.nathanpaiva.jobtracker.adapters.whatsapp;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * What is needed to send the digest through the WhatsApp Cloud API.
 *
 * <p>The first three have no default. A deployment missing one fails at startup, the same
 * as the Gmail credentials, instead of starting and then failing at the end of the run,
 * after the mailbox was read and the spreadsheet written.
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
}

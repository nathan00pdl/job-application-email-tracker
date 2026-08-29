package com.nathanpaiva.jobtracker.adapters.gmail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The three values needed to read the mailbox.
 *
 * <p>None of them has a default. A deployment missing one fails at startup with a clear
 * message about the missing property, instead of starting and then failing on the first
 * call to Google with an authentication error that says nothing useful.
 *
 * <p>They are secrets: they live in environment variables locally and in GitHub Actions
 * secrets in production, and never in this repository. The refresh token in particular
 * is a long-lived credential — the access tokens it produces last about an hour and are
 * never stored.
 *
 * @param clientId     identifies this application to Google, not the user
 * @param clientSecret proves the application is the one it claims to be
 * @param refreshToken the long-lived credential granted once, by hand, with the
 *                     {@code gmail.readonly} scope and nothing else
 */
@ConfigurationProperties(prefix = "gmail")
record GmailProperties(String clientId, String clientSecret, String refreshToken) {
}

package com.nathanpaiva.jobtracker.adapters.gmail;

import java.io.IOException;
import java.security.GeneralSecurityException;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.UserCredentials;

/**
 * Builds the Gmail client the adapter uses.
 *
 * <p>This is the only place in the project that knows how the credential works.
 * {@link UserCredentials} takes the client id, secret and refresh token, and from then
 * on handles the OAuth exchange on its own: it asks Google for an access token, keeps it
 * for the hour it lasts, and renews it when it expires. No code here ever sees a token.
 *
 * <p>Both this class and the adapter are package-private. What leaves the package is the
 * {@code EmailSourcePort} implementation, and nothing else.
 */
@Configuration
@EnableConfigurationProperties(GmailProperties.class)
class GmailClientConfiguration {

    /** Sent to Google on every call; it shows up in the project's API usage reports. */
    private static final String APPLICATION_NAME = "job-application-email-tracker";

    @Bean
    Gmail gmail(GmailProperties properties) throws GeneralSecurityException, IOException {
        UserCredentials credentials = UserCredentials.newBuilder()
                .setClientId(properties.clientId())
                .setClientSecret(properties.clientSecret())
                .setRefreshToken(properties.refreshToken())
                .build();

        return new Gmail.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }
}

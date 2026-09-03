package com.nathanpaiva.jobtracker.adapters.sheets;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;

/**
 * Builds the Sheets client.
 *
 * <p>This uses a service account, not the OAuth flow the mailbox uses, and the
 * difference is worth knowing. A service account is an identity of its own: it does not
 * act on anyone's behalf and it holds no consent that can expire. It reaches exactly the
 * spreadsheets someone has shared with it, and nothing else in the Drive it lives beside.
 *
 * <p>That is why there is no refresh token here and no seven-day clock — the problem
 * that shapes the Gmail side simply does not exist on this one.
 */
@Configuration
@EnableConfigurationProperties(GoogleSheetsProperties.class)
class GoogleSheetsConfiguration {

    private static final String APPLICATION_NAME = "job-application-email-tracker";

    @Bean
    Sheets sheets(GoogleSheetsProperties properties) throws GeneralSecurityException, IOException {
        byte[] key = Base64.getDecoder().decode(properties.credentials());

        GoogleCredentials credentials = ServiceAccountCredentials
                .fromStream(new ByteArrayInputStream(key))
                // The narrowest scope that can write a row. The read-write Drive scope
                // would also work and would also grant this key every file in the Drive.
                .createScoped(List.of(SheetsScopes.SPREADSHEETS));

        return new Sheets.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }
}

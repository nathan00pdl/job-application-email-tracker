package com.nathanpaiva.jobtracker.adapters.gmail;

import java.io.IOException;
import java.security.GeneralSecurityException;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpBackOffUnsuccessfulResponseHandler;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpUnsuccessfulResponseHandler;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.ExponentialBackOff;
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

    /**
     * How long to keep retrying a call Google is throttling, before giving up.
     *
     * <p>Two minutes because the limit that stops this job is measured per minute:
     * waiting out one window is enough, and waiting far longer would only turn a
     * throttled run into a hanging one.
     */
    private static final int GIVE_UP_AFTER_MILLIS = 120_000;

    /** The first pause. Each following one is longer, and slightly randomised. */
    private static final int FIRST_PAUSE_MILLIS = 1_000;

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
                retrying(new HttpCredentialsAdapter(credentials)))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    /**
     * Wraps an initialiser so that calls Google throttles are waited out and tried
     * again, instead of ending the run.
     *
     * <p>The adapter makes one request per email, as fast as it can. Ask for a few
     * hundred and Gmail answers {@code 403 rateLimitExceeded} — which is not a broken
     * mailbox, it is Google asking to be given a moment. Treating it as fatal turns a
     * pause into a failed run, and it is the busiest days, or the first run after the job
     * was down for a while, that reach that point.
     *
     * <p><b>Composed, not replaced.</b> {@link HttpCredentialsAdapter} installs its own
     * handler to catch a 401 and refresh the access token. Overwriting it would trade a
     * rate-limit bug for an authentication one, so the credential handler is asked first
     * and the back-off only runs when it declines.
     *
     * <p><b>Which failures are waited out:</b> 429 and 5xx, which always mean "later",
     * and 403, which for this API is how a rate limit arrives. A 403 that means something
     * else — a scope that was never granted — costs a couple of minutes of retries before
     * failing, which is a fair price for not dying on the failure that actually happens.
     *
     * <p>The retrying itself is the library's, not ours: {@link ExponentialBackOff}
     * already handles the growing pauses and the randomisation that keeps repeated
     * clients from retrying in step.
     *
     * <p>Package-private and taking the delegate as an argument so a test can exercise
     * the retrying without credentials.
     */
    static HttpRequestInitializer retrying(HttpRequestInitializer delegate) {
        return request -> {
            if (delegate != null) {
                delegate.initialize(request);
            }

            HttpUnsuccessfulResponseHandler credentialHandler =
                    request.getUnsuccessfulResponseHandler();

            HttpBackOffUnsuccessfulResponseHandler backOff =
                    new HttpBackOffUnsuccessfulResponseHandler(
                            new ExponentialBackOff.Builder()
                                    .setInitialIntervalMillis(FIRST_PAUSE_MILLIS)
                                    .setMaxElapsedTimeMillis(GIVE_UP_AFTER_MILLIS)
                                    .build())
                            .setBackOffRequired(response -> {
                                int status = response.getStatusCode();
                                return status == 403 || status == 429 || status / 100 == 5;
                            });

            request.setUnsuccessfulResponseHandler((req, response, supportsRetry) ->
                    (credentialHandler != null
                            && credentialHandler.handleResponse(req, response, supportsRetry))
                            || backOff.handleResponse(req, response, supportsRetry));
        };
    }
}

package com.nathanpaiva.jobtracker.adapters.gmail;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The credentials are checked when the application starts.
 *
 * <p>Bound the way Spring binds them at startup, from text with placeholders, because
 * that is where the gap was: a placeholder with no variable behind it is not an error to
 * Spring, and the property would keep the "${...}" text as its value.
 */
class GmailPropertiesTest {

    @Test
    void bindsWhenEveryValueIsSet() {
        GmailProperties properties = bind(Map.of());

        assertThat(properties.refreshToken()).isEqualTo("test-refresh-token");
    }

    /**
     * What a run started by hand sees when a line is missing from .env. The placeholder
     * names a variable that exists nowhere, rather than the real one: run after
     * {@code source .env}, the real one would resolve, and the test would pass for the
     * wrong reason or fail for no reason.
     */
    @Test
    void refusesAPlaceholderWithNoVariableBehindIt() {
        assertThatThrownBy(() -> bind(Map.of("gmail.refresh-token", "${JOBTRACKER_TEST_NEVER_SET}")))
                .hasRootCauseMessage("GMAIL_REFRESH_TOKEN is not set");
    }

    /** What the daily run sees when a secret is missing: GitHub hands it over empty. */
    @Test
    void refusesAnEmptyValue() {
        assertThatThrownBy(() -> bind(Map.of("gmail.client-id", "")))
                .hasRootCauseMessage("GMAIL_CLIENT_ID is not set");
    }

    /** The run's log is public, so the error names the variable and never the value. */
    @Test
    void neverRepeatsTheValueInTheError() {
        assertThatThrownBy(() -> new GmailProperties("  ", "a-real-secret", "a-real-token"))
                .hasMessage("GMAIL_CLIENT_ID is not set")
                .hasMessageNotContaining("a-real-secret");
    }

    private static GmailProperties bind(Map<String, String> overrides) {
        Map<String, Object> values = new HashMap<>(Map.of(
                "gmail.client-id", "test-client-id",
                "gmail.client-secret", "test-client-secret",
                "gmail.refresh-token", "test-refresh-token"));
        values.putAll(overrides);
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", values));
        return Binder.get(environment).bind("gmail", GmailProperties.class).get();
    }
}

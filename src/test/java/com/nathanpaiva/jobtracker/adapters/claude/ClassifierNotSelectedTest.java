package com.nathanpaiva.jobtracker.adapters.claude;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.nathanpaiva.jobtracker.AbstractPostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * With the default settings, nothing about Claude is created.
 *
 * <p>The fact that this test runs at all is half the point: no {@code ANTHROPIC_API_KEY}
 * is set anywhere in the build, and the application still starts. Cloning this project
 * and running it must not require paying anyone.
 */
class ClassifierNotSelectedTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void doesNotCreateTheClaudeAdapterUnlessItIsAskedFor() {
        assertThat(context.getBeansOfType(ClaudeApiAdapter.class)).isEmpty();
    }
}

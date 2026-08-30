package com.nathanpaiva.jobtracker.adapters.claude;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import com.nathanpaiva.jobtracker.AbstractPostgresIntegrationTest;
import com.nathanpaiva.jobtracker.ports.ClassifierPort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asking for the Claude classifier wires it up, and it answers to the port.
 *
 * <p>The key here is fake: the bean is built but never used, and nothing in this test
 * reaches the network.
 */
@TestPropertySource(properties = {
        "classifier.provider=claude",
        "anthropic.api-key=test-api-key"
})
class ClaudeClassifierSelectedTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void createsTheClaudeAdapterWhenSelected() {
        assertThat(context.getBeansOfType(ClaudeApiAdapter.class)).hasSize(1);
    }

    @Test
    void exposesItAsTheClassifierPort() {
        assertThat(context.getBean(ClassifierPort.class)).isInstanceOf(ClaudeApiAdapter.class);
    }
}

package com.nathanpaiva.jobtracker.adapters.rules;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.nathanpaiva.jobtracker.AbstractPostgresIntegrationTest;
import com.nathanpaiva.jobtracker.ports.ClassifierPort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * With no configuration at all, the classifier that costs nothing is the one wired in.
 */
class RulesClassifierWiringTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private ClassifierPort classifier;

    @Test
    void usesTheRuleBasedClassifierByDefault() {
        assertThat(classifier).isInstanceOf(RulesClassifierAdapter.class);
    }
}

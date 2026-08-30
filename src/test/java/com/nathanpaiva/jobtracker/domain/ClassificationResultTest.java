package com.nathanpaiva.jobtracker.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClassificationResultTest {

    @Test
    void exposesTheValuesItWasBuiltWith() {
        ClassificationResult result = new ClassificationResult(
                true, UpdateType.INTERVIEW_INVITE, "Acme Corp", "Backend Engineer",
                "Greenhouse", "Entrevista marcada para terça", true);

        assertThat(result.relevant()).isTrue();
        assertThat(result.updateType()).isEqualTo(UpdateType.INTERVIEW_INVITE);
        assertThat(result.company()).isEqualTo("Acme Corp");
        assertThat(result.roleTitle()).isEqualTo("Backend Engineer");
        assertThat(result.platform()).isEqualTo("Greenhouse");
        assertThat(result.summary()).isEqualTo("Entrevista marcada para terça");
        assertThat(result.urgent()).isTrue();
    }

    /**
     * The common shape for an email that turned out not to be about a job application:
     * nothing was extracted, because there was nothing to extract.
     */
    @Test
    void acceptsAResultWithNothingExtracted() {
        assertThatCode(() -> new ClassificationResult(
                false, UpdateType.OTHER, null, null, null, null, false))
                .doesNotThrowAnyException();
    }

    /**
     * A rejection often names no role, and a platform notification often names no
     * company. Demanding those would mean losing the email or inventing values.
     */
    @Test
    void acceptsARelevantResultWithFieldsTheEmailDidNotMention() {
        assertThatCode(() -> new ClassificationResult(
                true, UpdateType.REJECTION, "Acme Corp", null, null,
                "Seguiram com outro candidato", false))
                .doesNotThrowAnyException();
    }

    @Test
    void refusesAResultWithoutAnUpdateType() {
        assertThatThrownBy(() -> new ClassificationResult(
                true, null, null, null, null, null, false))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("updateType");
    }

    @Test
    void comparesByValueRatherThanByIdentity() {
        ClassificationResult one = new ClassificationResult(
                true, UpdateType.OFFER, "Acme Corp", null, null, null, true);
        ClassificationResult other = new ClassificationResult(
                true, UpdateType.OFFER, "Acme Corp", null, null, null, true);

        assertThat(one).isEqualTo(other);
        assertThat(one).hasSameHashCodeAs(other);
    }
}

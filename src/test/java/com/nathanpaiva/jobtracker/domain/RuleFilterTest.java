package com.nathanpaiva.jobtracker.domain;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class RuleFilterTest {

    private static final Instant RECEIVED_AT = Instant.parse("2026-08-27T09:00:00Z");

    private final RuleFilter filter = new RuleFilter();

    @Test
    void matchesAKnownJobPlatform() {
        assertThat(filter.matches(emailFrom("greenhouse.io", "Update"))).isTrue();
    }

    @Test
    void matchesASubdomainOfAKnownJobPlatform() {
        assertThat(filter.matches(emailFrom("careers.mail.greenhouse.io", "Update"))).isTrue();
    }

    /**
     * The dot boundary is what separates a subdomain from a lookalike. A filter that
     * quietly accepts lookalike domains is worse than one that misses them.
     */
    @ParameterizedTest
    @ValueSource(strings = {"notgreenhouse.io", "greenhouse.io.evil.com", "mygupy.io"})
    void doesNotMatchADomainThatOnlyLooksLikeAKnownOne(String senderDomain) {
        assertThat(filter.matches(emailFrom(senderDomain, "Update"))).isFalse();
    }

    @Test
    void ignoresTheCaseOfTheDomain() {
        assertThat(filter.matches(emailFrom("Careers.Greenhouse.IO", "Update"))).isTrue();
    }

    /**
     * A recruiter writing from an ordinary company address is caught by the subject, not
     * by the domain — which is why either signal alone is enough.
     */
    @Test
    void matchesAKeywordInTheSubjectFromAnUnknownDomain() {
        assertThat(filter.matches(emailFrom("acme.com", "Sua candidatura na Acme"))).isTrue();
    }

    /** One keyword has to cover every way a subject may be written or mistyped. */
    @ParameterizedTest
    @ValueSource(strings = {
            "Processo seletivo - etapa 2",
            "PROCESSO SELETIVO - ETAPA 2",
            "Seleção para a vaga de backend",
            "Selecao para a vaga de backend",
            "Convite para entrevista técnica",
            "Your application to Acme Corp",
            "Interview scheduled"
    })
    void matchesKeywordsWhateverTheCaseOrAccents(String subject) {
        assertThat(filter.matches(emailFrom("acme.com", subject))).isTrue();
    }

    @Test
    void doesNotMatchAnUnrelatedEmail() {
        assertThat(filter.matches(emailFrom("newsletter.example.com", "Sua fatura chegou")))
                .isFalse();
    }

    @Test
    void handlesAnEmptySubject() {
        assertThat(filter.matches(emailFrom("newsletter.example.com", ""))).isFalse();
        assertThat(filter.matches(emailFrom("greenhouse.io", ""))).isTrue();
    }

    private static IncomingEmail emailFrom(String senderDomain, String subject) {
        return new IncomingEmail("gmail-id", RECEIVED_AT, senderDomain, subject, "corpo");
    }
}

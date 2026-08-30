package com.nathanpaiva.jobtracker.domain;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class EmailClassifierTest {

    private static final Instant RECEIVED_AT = Instant.parse("2026-08-30T09:00:00Z");

    private final EmailClassifier classifier = new EmailClassifier();

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource({
            "'Recebemos sua candidatura para a vaga',               APPLICATION_RECEIVED",
            "'Sua candidatura: desafio técnico da próxima etapa',   TECHNICAL_TEST",
            "'Candidatura - convite para entrevista',               INTERVIEW_INVITE",
            "'Sobre sua candidatura: qual sua pretensão salarial?', INFO_REQUEST",
            "'Sua candidatura: infelizmente não seguiremos',        REJECTION",
            "'Sua candidatura - temos o prazer de oferecer a vaga', OFFER"
    })
    void recognisesEachKindOfUpdate(String subject, UpdateType expected) {
        assertThat(classify("acme.com", subject))
                .get()
                .extracting(EmailClassification::updateType)
                .isEqualTo(expected);
    }

    /**
     * A rejection almost always names the interview it is rejecting you after. Reading
     * the phrases in order of finality is what keeps this from being read as an invite.
     */
    @Test
    void readsARejectionThatMentionsAnInterviewAsARejection() {
        Optional<EmailClassification> result = classify("acme.com", "Sua candidatura",
                "Obrigado por participar da entrevista. Infelizmente não seguiremos.");

        assertThat(result).get()
                .extracting(EmailClassification::updateType)
                .isEqualTo(UpdateType.REJECTION);
    }

    @Test
    void ignoresAccentsAndCase() {
        assertThat(classify("acme.com", "RECEBEMOS SUA CANDIDATURA")).isPresent();
        assertThat(classify("acme.com", "Recebemos sua inscricao")).isPresent();
        assertThat(classify("acme.com", "Recebemos sua inscrição")).isPresent();
    }

    /**
     * Being about a job is not the same as being about an application this person made.
     * A newsletter listing openings mentions vagas on every line.
     */
    @Test
    void ignoresAJobAdvert() {
        assertThat(classify("newsletter.example.com", "5 vagas de backend abertas",
                "Confira as oportunidades da semana.")).isEmpty();
    }

    @Test
    void ignoresAnEmailThatHasNothingToDoWithWork() {
        assertThat(classify("banco.example.com", "Sua fatura chegou", "Vence dia 10."))
                .isEmpty();
    }

    /** Hiring platforms only write to people already in a process. */
    @Test
    void keepsAnyEmailFromAHiringPlatform() {
        assertThat(classify("careers.greenhouse.io", "Update", "Sem frases conhecidas."))
                .get()
                .extracting(EmailClassification::platform)
                .isEqualTo("Greenhouse");
    }

    /**
     * The dot boundary is what separates a subdomain from a lookalike. Quietly accepting
     * a lookalike domain is worse than missing it.
     */
    @ParameterizedTest
    @ValueSource(strings = {"notgreenhouse.io", "greenhouse.io.evil.com", "mygupy.io"})
    void doesNotTrustADomainThatOnlyLooksLikeAPlatform(String senderDomain) {
        assertThat(classify(senderDomain, "Update", "Sem frases conhecidas.")).isEmpty();
    }

    @Test
    void carriesTheEmailsOwnDetailsThrough() {
        Optional<EmailClassification> result = classify("gupy.io", "Sua candidatura");

        assertThat(result).get().satisfies(classification -> {
            assertThat(classification.gmailMessageId()).isEqualTo("gmail-id");
            assertThat(classification.receivedAt()).isEqualTo(RECEIVED_AT);
            assertThat(classification.senderDomain()).isEqualTo("gupy.io");
            assertThat(classification.platform()).isEqualTo("Gupy");
            assertThat(classification.summary()).isEqualTo("Sua candidatura");
        });
    }

    /**
     * Guessing these from phrases would produce values that look extracted but are not,
     * and a wrong company is worse than an empty one.
     */
    @Test
    void neverGuessesTheCompanyOrTheRole() {
        assertThat(classify("greenhouse.io", "Sua candidatura para Backend na Acme Corp"))
                .get()
                .satisfies(classification -> {
                    assertThat(classification.company()).isNull();
                    assertThat(classification.roleTitle()).isNull();
                });
    }

    @Test
    void leavesTheSummaryEmptyWhenTheSubjectIs() {
        assertThat(classify("greenhouse.io", "")).get()
                .extracting(EmailClassification::summary)
                .isNull();
    }

    @Test
    void marksAsUrgentOnlyWhenSomethingIsBeingAsked() {
        assertThat(classify("greenhouse.io", "Sua candidatura",
                "Por favor, confirme sua disponibilidade até sexta.")).get()
                .extracting(EmailClassification::urgent).isEqualTo(true);

        assertThat(classify("greenhouse.io", "Sua candidatura",
                "Recebemos sua candidatura e entraremos em contato.")).get()
                .extracting(EmailClassification::urgent).isEqualTo(false);
    }

    private Optional<EmailClassification> classify(String senderDomain, String subject) {
        return classify(senderDomain, subject, "corpo");
    }

    private Optional<EmailClassification> classify(
            String senderDomain, String subject, String body) {
        return classifier.classify(
                new IncomingEmail("gmail-id", RECEIVED_AT, senderDomain, subject, body));
    }
}

package com.nathanpaiva.jobtracker.adapters.rules;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.nathanpaiva.jobtracker.domain.ClassificationResult;
import com.nathanpaiva.jobtracker.domain.IncomingEmail;
import com.nathanpaiva.jobtracker.domain.UpdateType;

import static org.assertj.core.api.Assertions.assertThat;

class RulesClassifierAdapterTest {

    private static final Instant RECEIVED_AT = Instant.parse("2026-08-30T09:00:00Z");

    private final RulesClassifierAdapter classifier = new RulesClassifierAdapter();

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource({
            "'Recebemos sua candidatura para a vaga',            APPLICATION_RECEIVED",
            "'Sua candidatura: desafio técnico da próxima etapa', TECHNICAL_TEST",
            "'Candidatura - convite para entrevista',            INTERVIEW_INVITE",
            "'Sobre sua candidatura: qual sua pretensão salarial?', INFO_REQUEST",
            "'Sua candidatura: infelizmente não seguiremos',     REJECTION",
            "'Sua candidatura - temos o prazer de oferecer a vaga', OFFER"
    })
    void recognisesEachKindOfUpdate(String subject, UpdateType expected) {
        assertThat(classifier.classify(email("acme.com", subject)).updateType())
                .isEqualTo(expected);
    }

    /**
     * A rejection almost always names the interview it is rejecting you after. Reading
     * the phrases in order of finality is what keeps this from being read as an invite.
     */
    @Test
    void readsARejectionThatMentionsAnInterviewAsARejection() {
        ClassificationResult result = classifier.classify(email("acme.com",
                "Sua candidatura",
                "Obrigado por participar da entrevista. Infelizmente não seguiremos."));

        assertThat(result.updateType()).isEqualTo(UpdateType.REJECTION);
    }

    @Test
    void ignoresAccentsAndCase() {
        assertThat(classifier.classify(email("acme.com", "RECEBEMOS SUA CANDIDATURA")).updateType())
                .isEqualTo(UpdateType.APPLICATION_RECEIVED);
        assertThat(classifier.classify(email("acme.com", "Recebemos sua inscricao")).updateType())
                .isEqualTo(UpdateType.APPLICATION_RECEIVED);
    }

    /**
     * Stricter than the pre-filter on purpose. Being about a job is not the same as
     * being about an application this person made.
     */
    @Test
    void doesNotCountAJobAdvertAsAnApplication() {
        ClassificationResult result = classifier.classify(email("newsletter.example.com",
                "5 vagas de backend abertas esta semana",
                "Confira as oportunidades."));

        assertThat(result.relevant()).isFalse();
        assertThat(result.updateType()).isEqualTo(UpdateType.OTHER);
    }

    /** Hiring platforms only write to people already in a process. */
    @Test
    void countsAnyEmailFromAHiringPlatform() {
        ClassificationResult result = classifier.classify(
                email("careers.greenhouse.io", "Update", "Sem palavras conhecidas aqui."));

        assertThat(result.relevant()).isTrue();
        assertThat(result.platform()).isEqualTo("Greenhouse");
    }

    @Test
    void readsThePlatformFromTheSenderDomain() {
        assertThat(classifier.classify(email("gupy.io", "assunto")).platform()).isEqualTo("Gupy");
        assertThat(classifier.classify(email("acme.com", "assunto")).platform()).isNull();
    }

    /**
     * The company and the role are never filled in. Guessing them from phrases would
     * produce values that look extracted but are not, and a wrong company is worse than
     * an empty one.
     */
    @Test
    void neverGuessesTheCompanyOrTheRole() {
        ClassificationResult result = classifier.classify(email("greenhouse.io",
                "Sua candidatura para Backend Engineer na Acme Corp"));

        assertThat(result.company()).isNull();
        assertThat(result.roleTitle()).isNull();
    }

    @Test
    void usesTheSubjectAsTheSummary() {
        assertThat(classifier.classify(email("greenhouse.io", "  Sua candidatura  ")).summary())
                .isEqualTo("Sua candidatura");
        assertThat(classifier.classify(email("greenhouse.io", "")).summary()).isNull();
    }

    @Test
    void marksAsUrgentOnlyWhenSomethingIsBeingAsked() {
        assertThat(classifier.classify(email("greenhouse.io", "Sua candidatura",
                "Por favor, confirme sua disponibilidade até sexta.")).urgent()).isTrue();

        assertThat(classifier.classify(email("greenhouse.io", "Sua candidatura",
                "Recebemos sua candidatura e entraremos em contato.")).urgent()).isFalse();
    }

    private static IncomingEmail email(String senderDomain, String subject) {
        return email(senderDomain, subject, "corpo");
    }

    private static IncomingEmail email(String senderDomain, String subject, String body) {
        return new IncomingEmail("gmail-id", RECEIVED_AT, senderDomain, subject, body);
    }
}

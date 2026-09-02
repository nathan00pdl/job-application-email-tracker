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
            "'Sua candidatura para a vaga: desafio técnico',        TECHNICAL_TEST",
            "'Sobre sua candidatura: convite para entrevista',      INTERVIEW_INVITE",
            "'Sobre sua candidatura: qual sua pretensão salarial?', INFO_REQUEST",
            "'Sua candidatura foi analisada: infelizmente não seguiremos', REJECTION",
            "'Sua candidatura foi aprovada - temos o prazer de oferecer a vaga', OFFER"
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
        Optional<EmailClassification> result = classify("acme.com", "Sobre sua candidatura",
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
     * Four emails a real run stored as job applications, none of which was one. They are
     * kept here so they can never quietly come back.
     *
     * <p>The bodies are shortened, but they carry the phrases that caused each mistake:
     * "processo seletivo" and "sua inscrição" used to count as evidence, and any message
     * from LinkedIn counted because job boards were treated like applicant tracking
     * systems.
     */
    @Test
    void ignoresTheAdvertsThatWereOnceStoredAsApplications() {
        assertThat(classify("matchbox.digital",
                "Comece sua jornada em uma líder global: Inscreva-se no Trainee 2027!",
                "As inscrições para o processo seletivo estão abertas. Inscreva-se até 30/09."))
                .isEmpty();

        assertThat(classify("mbauspesalq.com", "30% OFF | Cadeias do Agronegócio",
                "Garanta sua inscrição no MBA com desconto. Inscreva-se agora."))
                .isEmpty();

        assertThat(classify("reservatoriodedopamina.com.br",
                "Última semana para ganhar essa aula", "Confira as vagas abertas da turma."))
                .isEmpty();

        assertThat(classify("linkedin.com",
                "suas publicações receberam 67 impressões na semana passada",
                "Veja quem interagiu com você."))
                .isEmpty();
    }

    /** A job recommendation is not news about an application. */
    @Test
    void ignoresAJobRecommendationFromACompanyCareersAddress() {
        assertThat(classify("deere.com", "This job is a match",
                "We found a role that matches your profile. Apply now."))
                .isEmpty();
    }

    /**
     * A job board is recorded by name when an email does come from one, but its presence
     * is not evidence of anything: they write to everybody.
     */
    @Test
    void namesAJobBoardWithoutTrustingIt() {
        assertThat(classify("linkedin.com", "Novidades da semana", "Veja as novidades."))
                .isEmpty();

        assertThat(classify("linkedin.com", "Sobre sua candidatura para Backend",
                "A empresa respondeu.")).get()
                .extracting(EmailClassification::platform)
                .isEqualTo("LinkedIn");
    }

    /**
     * The advert veto must not swallow real news. An offer that mentions applying is
     * still an offer.
     */
    @Test
    void keepsRealNewsThatMentionsApplying() {
        assertThat(classify("greenhouse.io", "Sobre sua candidatura",
                "Temos o prazer de oferecer a vaga a você.")).get()
                .extracting(EmailClassification::updateType)
                .isEqualTo(UpdateType.OFFER);
    }

    /**
     * Being about a job is not the same as being about an application this person made.
     * A newsletter listing openings mentions vagas on every line.
     */
    @Test
    void ignoresAJobAdvert() {
        assertThat(classify("newsletter.example.com", "5 vagas abertas de backend",
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
        Optional<EmailClassification> result = classify("gupy.io", "Sua candidatura para backend");

        assertThat(result).get().satisfies(classification -> {
            assertThat(classification.gmailMessageId()).isEqualTo("gmail-id");
            assertThat(classification.receivedAt()).isEqualTo(RECEIVED_AT);
            assertThat(classification.senderDomain()).isEqualTo("gupy.io");
            assertThat(classification.platform()).isEqualTo("Gupy");
            assertThat(classification.summary()).isEqualTo("Sua candidatura para backend");
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

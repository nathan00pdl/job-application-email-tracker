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
            assertThat(classification.subject()).isEqualTo("Sua candidatura para backend");
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
    void leavesTheSubjectEmptyWhenTheEmailHasNone() {
        assertThat(classify("greenhouse.io", "")).get()
                .extracting(EmailClassification::subject)
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

    /**
     * Real subjects from a ninety-day read of the mailbox, every one of them missed
     * before these domains were known.
     *
     * <p>Gupy writes from {@code gupy.com.br}, not from the {@code gupy.io} the map had.
     * None of these subjects carries a phrase the evidence list recognises — "retorno do
     * processo seletivo" and "obrigada pelo interesse" are not in it — so with the wrong
     * domain there was nothing left to catch them by.
     */
    @Test
    void recognisesTheDomainsTheseSystemsActuallyWriteFrom() {
        assertThat(classify("gupy.com.br", "CAPPTA | Retorno do processo seletivo",
                "Agradecemos sua participação.")).get()
                .extracting(EmailClassification::platform).isEqualTo("Gupy");

        assertThat(classify("reply.gupy.com.br", "Atualização Processo Seletivo",
                "Houve uma atualização.")).get()
                .extracting(EmailClassification::platform).isEqualTo("Gupy");

        assertThat(classify("ses-mail.inhire.app", "Devolutiva - Desenvolvedor Back-end Java Jr",
                "Seguimos com outros perfis.")).get()
                .extracting(EmailClassification::platform).isEqualTo("inHire");

        assertThat(classify("gupy.com.br",
                "Embrasil - Obrigada pelo interesse em fazer parte do nosso time!",
                "Recebemos seu cadastro.")).isPresent();
    }

    /**
     * Knowing the domain must not hand the advert veto a free pass. Both of these were
     * kept when the domains were first added, with the bodies the senders actually use —
     * neither carries a phrase the old advert list recognised.
     */
    @Test
    void stillIgnoresTheMarketingThoseSameSystemsSend() {
        assertThat(classify("gupy.com.br",
                "Programas de Talentos tem interesse em seu perfil para a vaga Trainee 2027",
                "Conheça o programa e participe."))
                .isEmpty();

        assertThat(classify("inbound.gupy.com.br", "Convite | Batalha de Agentes",
                "Um evento para desenvolvedores. Garanta seu lugar."))
                .isEmpty();
    }

    /**
     * Two real subjects that no domain can reach: one from the company itself, one from
     * a job board. A job board proves nothing by design — it writes to everybody — so
     * both depend entirely on the words.
     */
    @Test
    void recognisesAnApplicationFromWordsAloneWhenTheSenderProvesNothing() {
        assertThat(classify("btgpactual.com",
                "BTG Pactual | Retorno do Processo Seletivo da Vaga Desenvolvedor(a) Backend",
                "Agradecemos sua participação.")).isPresent();

        assertThat(classify("indeed.com", "Inscrição via Indeed: Junior Backend Engineer - Java",
                "Sua candidatura foi enviada.")).get()
                .extracting(EmailClassification::platform)
                .isEqualTo("Indeed");
    }

    /**
     * The new phrases must not undo the veto. An advert announcing that a selection
     * process is open is still an advert, whatever words surround it.
     */
    @Test
    void doesNotLetTheNewPhrasesReopenTheDoorToAdverts() {
        assertThat(classify("matchbox.digital",
                "Inscrições abertas para o Trainee 2027 - retorno do processo seletivo em outubro",
                "Inscreva-se até 30/09.")).isEmpty();
    }

    /**
     * A sending subdomain used for marketing stops the address proving anything, but it
     * is not a veto: a real message from there is still kept on its own evidence, and the
     * platform is still recorded, because where an email came from is a fact either way.
     */
    @Test
    void keepsRealNewsEvenFromAMarketingAddress() {
        assertThat(classify("inbound.gupy.com.br", "Recebemos sua candidatura",
                "Em breve retornamos.")).get()
                .extracting(EmailClassification::platform)
                .isEqualTo("Gupy");
    }

    /**
     * The first footer is the one two real marketing emails ended with, word for word
     * apart from the company. "You applied" is evidence, but here it only explains why
     * the email was sent — and a company adds that to everything it sends to people who
     * once applied.
     */
    @Test
    void ignoresAFooterThatOnlySaysWhyTheEmailWasSent() {
        assertThat(classify("acme.com", "What do people say about Acme? 👀",
                "Read the reviews. You have received this email, because you applied for "
                        + "a job on Acme website."))
                .isEmpty();

        assertThat(classify("acme.com", "Life at Acme",
                "See what we have been up to. You are receiving this email because you "
                        + "applied to a position at Acme."))
                .isEmpty();
    }

    /**
     * Taking the footer out must not take the email with it: when the body reports on the
     * application, that is still evidence, whatever the footer says.
     */
    @Test
    void keepsRealNewsThatCarriesTheSameFooter() {
        assertThat(classify("acme.com", "Your application",
                "We received your application for the Backend role. You have received this "
                        + "email, because you applied for a job on Acme website."))
                .get()
                .extracting(EmailClassification::updateType)
                .isEqualTo(UpdateType.APPLICATION_RECEIVED);
    }

    /**
     * Only the footer's own wording is taken out. A recruiter who writes "because you
     * applied" in the message itself is reporting on the application, and must still be
     * heard.
     */
    @Test
    void stillHearsARecruiterWritingBecauseYouApplied() {
        assertThat(classify("acme.com", "Java Developer role",
                "Hi, I am reaching out because you applied for our Java Developer role. "
                        + "Could we schedule a call this week?"))
                .get()
                .extracting(EmailClassification::updateType)
                .isEqualTo(UpdateType.INTERVIEW_INVITE);
    }

    /**
     * Real confirmations from a morning of applications. Every one was kept, but as
     * {@code OTHER}: the words proved an application existed without naming what kind of
     * news it was, so eighteen of twenty-three went out in the digest as "sem categoria".
     */
    @ParameterizedTest(name = "{0}: \"{1}\"")
    @CsvSource({
            "indeed.com,          'Inscrição via Indeed: Desenvolvedor Java'",
            "ses-mail.inhire.app, 'Confirmação de Inscrição - Desenvolvedor(a) Júnior'",
            "pandape.com.br,      'Mantenha-se informado sobre sua candidatura para Desenvolvedor Java'",
            "ats.bizneo.com,      'Sua candidatura para Desenvolvedor(a) Backend Java'",
            "ats.bizneo.com,      'Sauter Digital | Inscrição recebida – Vaga Desenvolvedor(a) Backend Java'"
    })
    void namesAConfirmationAsOne(String senderDomain, String subject) {
        assertThat(classify(senderDomain, subject)).get()
                .extracting(EmailClassification::updateType)
                .isEqualTo(UpdateType.APPLICATION_RECEIVED);
    }

    /**
     * Three emails from the same morning that each asked for something. Two were dropped,
     * because neither the sender nor the words proved an application; the third was kept
     * as a confirmation, because its body thanked for the application before asking to
     * finish it. A digest that lists what needs doing would have listed none of them.
     */
    @ParameterizedTest(name = "{0}: \"{1}\"")
    @CsvSource({
            "pandape.com.br, 'Responda o questionário para avançar no processo', 'Acesse o link.'",
            "ats.bizneo.com, 'Sauter convidou você para responder a um formulário', 'Acesse o link.'",
            "ats.bizneo.com, 'Complete sua inscrição para Desenvolvedor(a) Backend Java', 'Recebemos sua inscrição, falta pouco.'"
    })
    void recognisesARequestToDoSomethingAsOne(String senderDomain, String subject, String body) {
        assertThat(classify(senderDomain, subject, body)).get()
                .extracting(EmailClassification::updateType)
                .isEqualTo(UpdateType.INFO_REQUEST);
    }

    /**
     * Two ways the same system ends a process: closed by the company, and closed for the
     * candidate. Both were dropped — the system was not on the list and neither wording
     * was either — and a closed process is exactly the kind of news worth keeping.
     */
    @ParameterizedTest(name = "\"{0}\"")
    @CsvSource({
            "'[CLIN] Retorno sobre a oportunidade', 'Agradecemos seu interesse em nossa oportunidade. Devido a mudanças internas e decisões de negócio, o processo seletivo foi descontinuado.'",
            "'[GRUPOSUPERABC] Retorno sobre a oportunidade', 'Agradecemos seu interesse em se candidatar em nossa oportunidade. Nesse momento, você não seguirá conosco no processo seletivo, mas não desanime!'"
    })
    void recognisesAClosedProcessAsARejection(String subject, String body) {
        assertThat(classify("job.recrut.ai", subject, body))
                .get()
                .satisfies(classification -> {
                    assertThat(classification.updateType()).isEqualTo(UpdateType.REJECTION);
                    assertThat(classification.platform()).isEqualTo("Recrut.AI");
                });
    }

    /**
     * "Sua candidatura para" now names a confirmation, but it opens rejections and
     * invitations just as often. Those are read first, so the new phrase cannot swallow
     * them.
     */
    @Test
    void doesNotLetAConfirmationPhraseSwallowMoreSpecificNews() {
        assertThat(classify("pandape.com.br", "Sua candidatura para Desenvolvedor Java",
                "Infelizmente não seguiremos com o seu perfil.")).get()
                .extracting(EmailClassification::updateType).isEqualTo(UpdateType.REJECTION);

        assertThat(classify("pandape.com.br", "Sua candidatura para Desenvolvedor Java",
                "Gostaríamos de agendar uma entrevista.")).get()
                .extracting(EmailClassification::updateType)
                .isEqualTo(UpdateType.INTERVIEW_INVITE);
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

package com.nathanpaiva.jobtracker.domain;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Decides whether an email is about a job application, and what it says.
 *
 * <p>An empty result means the email has nothing to do with a job application. Those are
 * never turned into an {@link EmailClassification} and never stored, so the existence of
 * one is the verdict — there is no flag to check.
 *
 * <p>It works by matching phrases, which is a reasonable thing to do here and would not
 * be elsewhere: these emails are templates. Greenhouse, Gupy and Lever send the same
 * sentences every time — "recebemos sua candidatura", "infelizmente não seguiremos",
 * "gostaríamos de convidá-lo". Matching phrases against templated text is very different
 * from matching them against free-form writing.
 *
 * <p>What it cannot do is read. It never fills in the company or the role, because
 * guessing those from phrases would produce values that look extracted but are not, and
 * a wrong company is worse than an empty one. The platform comes from the sender's
 * domain, which is a fact rather than a guess.
 *
 * <p>It depends on nothing: no framework, no network, no clock. Which is why every case
 * below can be checked in a plain unit test.
 */
public final class EmailClassifier {

    /**
     * Applicant tracking systems: software a company runs to manage its hiring.
     *
     * <p>An email from one of these counts as being about an application on its own,
     * because that is all they send. They do not have newsletters.
     */
    private static final Map<String, String> ATS_BY_DOMAIN = Map.ofEntries(
            Map.entry("greenhouse.io", "Greenhouse"), Map.entry("lever.co", "Lever"),
            Map.entry("ashbyhq.com", "Ashby"), Map.entry("workable.com", "Workable"),
            Map.entry("recruitee.com", "Recruitee"), Map.entry("breezy.hr", "Breezy"),
            Map.entry("jobvite.com", "Jobvite"), Map.entry("icims.com", "iCIMS"),
            Map.entry("taleo.net", "Taleo"), Map.entry("bamboohr.com", "BambooHR"),
            Map.entry("teamtailor.com", "Teamtailor"),
            Map.entry("smartrecruiters.com", "SmartRecruiters"),
            Map.entry("successfactors.com", "SuccessFactors"),
            Map.entry("workday.com", "Workday"), Map.entry("myworkdayjobs.com", "Workday"),
            Map.entry("gupy.io", "Gupy"), Map.entry("kenoby.com", "Kenoby"),
            Map.entry("solides.com", "Sólides"));

    /**
     * Job boards: sites where openings are advertised.
     *
     * <p>Their name is worth recording when an email does come from one, but their
     * presence proves nothing. They send job alerts, newsletters and social
     * notifications to everyone, most of it to people who have applied for nothing —
     * which is exactly how a LinkedIn message about post impressions was once stored as
     * a job application.
     */
    private static final Map<String, String> JOB_BOARD_BY_DOMAIN = Map.ofEntries(
            Map.entry("linkedin.com", "LinkedIn"), Map.entry("indeed.com", "Indeed"),
            Map.entry("glassdoor.com", "Glassdoor"), Map.entry("vagas.com.br", "Vagas.com"),
            Map.entry("catho.com.br", "Catho"), Map.entry("infojobs.com.br", "InfoJobs"),
            Map.entry("99jobs.com", "99Jobs"),
            Map.entry("programathor.com.br", "ProgramaThor"));

    /**
     * Phrases showing an application already exists.
     *
     * <p>These are deliberately written in the past or the possessive. An earlier
     * version accepted "processo seletivo" and "sua inscricao", and both turned out to
     * be everywhere: an advert for a trainee programme and a discount on an MBA course
     * were stored as job applications because of them.
     *
     * <p>Being about a job is not the same as being about an application this person
     * made. Almost every phrase describing the act of applying also appears in adverts
     * inviting people to apply — so what is matched here is the answer, not the offer.
     */
    private static final Set<String> APPLICATION_EVIDENCE = Set.of(
            "recebemos sua candidatura", "recebemos sua inscricao",
            "sua candidatura foi", "sua candidatura para", "sobre sua candidatura",
            "status da sua candidatura", "andamento da sua candidatura",
            "candidatura recebida", "candidatura registrada",
            "obrigado por se candidatar", "agradecemos sua candidatura",
            "agradecemos seu interesse na vaga",
            "we received your application", "thank you for applying",
            "your application for", "your application has", "you applied",
            "regarding your application", "application status");

    /**
     * Phrases that mean the email is inviting someone to apply, not reporting on an
     * application.
     *
     * <p>These win over everything else. An advert can easily contain a phrase from the
     * list above — "faça sua candidatura para" — and without a way to say no, the only
     * options would be to accept the advert or to drop the phrase and lose real emails
     * with it.
     *
     * <p>The list is kept narrow for the same reason it exists. "Venha fazer parte" was
     * considered and rejected: it reads like an advert, but an offer letter says it too.
     */
    private static final Set<String> ADVERT_PHRASES = Set.of(
            "inscreva-se", "inscreva se", "candidate-se", "candidate se",
            "vagas abertas", "confira as vagas", "conheca as vagas",
            "veja as vagas", "oportunidades abertas", "estamos contratando",
            "apply now", "we are hiring", "we're hiring", "job alert",
            "jobs for you", "this job is a match", "recommended for you");

    /**
     * Read in this order, first match wins, and the order carries meaning. A rejection
     * almost always names the interview it is rejecting you after, and an offer often
     * names both. Reading them in order of finality keeps the specific outcomes from
     * being swallowed by the general ones.
     */
    private static final Map<UpdateType, Set<String>> PHRASES_BY_TYPE = phrasesByType();

    private static final Set<String> URGENCY_PHRASES = Set.of(
            "ate o dia", "ate sexta", "prazo", "confirme sua", "confirmar ate",
            "o quanto antes", "urgente", "expira", "deadline", "as soon as possible",
            "by friday", "confirm your", "expires");

    public Optional<EmailClassification> classify(IncomingEmail email) {
        String text = normalize(email.subject() + " " + email.body());
        String platform = platformOf(email.senderDomain());

        if (!isAboutAnApplication(text, email.senderDomain())) {
            return Optional.empty();
        }

        return Optional.of(new EmailClassification(
                email.gmailMessageId(),
                email.receivedAt(),
                email.senderDomain(),
                platform,
                null,
                null,
                updateTypeOf(text),
                summaryOf(email),
                isUrgent(text)));
    }

    /**
     * An advert is never about an application, whatever else it says. Otherwise, an
     * email counts when it carries evidence of an application, or when it comes from an
     * applicant tracking system — those only write to people already in a process.
     */
    private static boolean isAboutAnApplication(String text, String senderDomain) {
        if (ADVERT_PHRASES.stream().anyMatch(text::contains)) {
            return false;
        }
        return nameFrom(ATS_BY_DOMAIN, senderDomain) != null
                || APPLICATION_EVIDENCE.stream().anyMatch(text::contains);
    }

    private static UpdateType updateTypeOf(String text) {
        return PHRASES_BY_TYPE.entrySet().stream()
                .filter(entry -> entry.getValue().stream().anyMatch(text::contains))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(UpdateType.OTHER);
    }

    private static boolean isUrgent(String text) {
        return URGENCY_PHRASES.stream().anyMatch(text::contains);
    }

    /**
     * Matches the domain and any subdomain of it. The dot is part of the comparison on
     * purpose: without it, {@code notgreenhouse.io} would match too, and a classifier
     * that quietly accepts lookalike domains is worse than one that misses them.
     */
    private static String platformOf(String senderDomain) {
        String ats = nameFrom(ATS_BY_DOMAIN, senderDomain);
        return ats != null ? ats : nameFrom(JOB_BOARD_BY_DOMAIN, senderDomain);
    }

    private static String nameFrom(Map<String, String> byDomain, String senderDomain) {
        String domain = normalize(senderDomain);
        return byDomain.entrySet().stream()
                .filter(entry -> domain.equals(entry.getKey())
                        || domain.endsWith("." + entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    /**
     * The subject, as written. Not a summary of the email, and not pretending to be one:
     * it is the line the sender already chose to describe the message.
     */
    private static String summaryOf(IncomingEmail email) {
        String subject = email.subject().strip();
        return subject.isEmpty() ? null : subject;
    }

    /**
     * Lower case without accents, so each phrase above is written once.
     *
     * <p>These emails arrive mostly in pt-BR, where "Seleção", "selecao" and "SELEÇÃO"
     * are the same word to a reader and three different strings to a computer. NFD
     * splits an accented character into the letter plus a combining mark, and
     * {@code \p{M}} removes the marks.
     *
     * <p>{@code Locale.ROOT} is not decoration: under a Turkish locale, lower-casing "I"
     * gives a dotless "ı", and every phrase containing an i would stop matching.
     */
    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    private static Map<UpdateType, Set<String>> phrasesByType() {
        Map<UpdateType, Set<String>> phrases = new LinkedHashMap<>();
        phrases.put(UpdateType.OFFER, Set.of(
                "proposta", "oferta de trabalho", "carta oferta", "job offer",
                "offer letter", "we are pleased to offer", "temos o prazer de oferecer"));
        phrases.put(UpdateType.REJECTION, Set.of(
                "infelizmente", "nao seguiremos", "nao foi selecionado",
                "seguimos com outro", "outro candidato", "nao teremos como avancar",
                "unfortunately", "not moving forward", "will not be proceeding",
                "decided to move forward with other"));
        phrases.put(UpdateType.INTERVIEW_INVITE, Set.of(
                "entrevista", "conversa com", "bate-papo", "agendar um horario",
                "interview", "schedule a call", "meet the team"));
        phrases.put(UpdateType.TECHNICAL_TEST, Set.of(
                "teste tecnico", "desafio tecnico", "desafio de codigo", "code challenge",
                "technical test", "take-home", "assessment", "hackerrank", "codility"));
        phrases.put(UpdateType.INFO_REQUEST, Set.of(
                "pretensao salarial", "sua disponibilidade", "envie os documentos",
                "precisamos de algumas informacoes", "preencha o formulario",
                "salary expectation", "your availability", "fill out the form",
                "we need some information"));
        phrases.put(UpdateType.APPLICATION_RECEIVED, Set.of(
                "recebemos sua candidatura", "recebemos sua inscricao",
                "sua candidatura foi recebida", "candidatura registrada",
                "we received your application", "thank you for applying",
                "application received", "obrigado por se candidatar"));
        return phrases;
    }
}

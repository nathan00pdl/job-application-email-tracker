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
     * Hiring platforms, mapped to the name worth recording.
     *
     * <p>One map serves both questions — "is this a hiring platform?" and "what is it
     * called?" — on purpose. An earlier design kept a set of domains in one class and a
     * map of names in another, and the two could drift apart without anyone noticing.
     */
    private static final Map<String, String> PLATFORM_BY_DOMAIN = Map.ofEntries(
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
            Map.entry("solides.com", "Sólides"), Map.entry("99jobs.com", "99Jobs"),
            Map.entry("linkedin.com", "LinkedIn"), Map.entry("indeed.com", "Indeed"),
            Map.entry("glassdoor.com", "Glassdoor"), Map.entry("vagas.com.br", "Vagas.com"),
            Map.entry("catho.com.br", "Catho"), Map.entry("infojobs.com.br", "InfoJobs"),
            Map.entry("programathor.com.br", "ProgramaThor"));

    /**
     * Phrases showing an application already exists.
     *
     * <p>Being about a job is not the same as being about an application this person
     * made. A newsletter listing openings mentions vagas on every line and belongs
     * nowhere near the day's numbers.
     */
    private static final Set<String> APPLICATION_EVIDENCE = Set.of(
            "candidatura", "sua inscricao", "sua aplicacao", "processo seletivo",
            "sua participacao", "seu curriculo", "your application", "you applied",
            "application for", "hiring process", "recruitment process");

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

        if (!isAboutAnApplication(text, platform)) {
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
     * A message from a hiring platform counts on its own: those systems only write to
     * people who are already in a process.
     */
    private static boolean isAboutAnApplication(String text, String platform) {
        return platform != null || APPLICATION_EVIDENCE.stream().anyMatch(text::contains);
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
        String domain = normalize(senderDomain);
        return PLATFORM_BY_DOMAIN.entrySet().stream()
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

package com.nathanpaiva.jobtracker.adapters.rules;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.nathanpaiva.jobtracker.domain.ClassificationResult;
import com.nathanpaiva.jobtracker.domain.IncomingEmail;
import com.nathanpaiva.jobtracker.domain.TextNormalizer;
import com.nathanpaiva.jobtracker.domain.UpdateType;
import com.nathanpaiva.jobtracker.ports.ClassifierPort;

/**
 * Classifies an email by looking for phrases, with no model and no cost.
 *
 * <p>This works better here than it would in general, because the emails it reads are
 * templates. Greenhouse, Gupy and Lever send the same sentences every time — "recebemos
 * sua candidatura", "infelizmente não seguiremos", "gostaríamos de convidá-lo". Matching
 * phrases against templated text is a reasonable thing to do; matching them against
 * free-form writing would not be.
 *
 * <p>What it cannot do is read. It never guesses the company or the role, because
 * guessing those from phrases would produce values that look extracted but are not. The
 * platform comes from the sender's domain, which is a fact rather than a guess, and the
 * summary is the subject line, which is the best single line available without
 * understanding the text.
 *
 * <p>This is the default classifier. The one backed by a language model is more accurate
 * and costs money per email; this one costs nothing and runs anywhere.
 */
@Component
@ConditionalOnProperty(name = "classifier.provider", havingValue = "rules", matchIfMissing = true)
class RulesClassifierAdapter implements ClassifierPort {

    /**
     * Phrases that show an application already exists.
     *
     * <p>This is stricter than the pre-filter on purpose. The pre-filter is permissive —
     * it only decides what is worth looking at, and a subject saying "vaga" is enough.
     * Being about a job is not the same as being about an application this person made,
     * and only the second one belongs in the day's numbers.
     */
    private static final Set<String> APPLICATION_EVIDENCE = Set.of(
            "candidatura", "sua inscricao", "sua aplicacao", "processo seletivo",
            "sua participacao", "seu curriculo", "your application", "you applied",
            "application for", "hiring process", "recruitment process");

    /**
     * Checked in this order, first match wins, and the order carries meaning. A
     * rejection often mentions the interview it is rejecting you after, and an offer
     * often mentions both. Reading them in order of finality keeps the specific ones
     * from being swallowed by the general ones.
     */
    private static final Map<UpdateType, Set<String>> PHRASES_BY_TYPE = phrasesByType();

    private static final Set<String> URGENCY_PHRASES = Set.of(
            "ate o dia", "ate sexta", "prazo", "confirme sua", "confirmar ate",
            "o quanto antes", "urgente", "expira", "deadline", "as soon as possible",
            "by friday", "confirm your", "expires");

    /** Domains whose name is worth reporting as the platform the email came through. */
    private static final Map<String, String> PLATFORM_BY_DOMAIN = Map.ofEntries(
            Map.entry("greenhouse.io", "Greenhouse"), Map.entry("lever.co", "Lever"),
            Map.entry("ashbyhq.com", "Ashby"), Map.entry("workable.com", "Workable"),
            Map.entry("recruitee.com", "Recruitee"), Map.entry("gupy.io", "Gupy"),
            Map.entry("kenoby.com", "Kenoby"), Map.entry("solides.com", "Sólides"),
            Map.entry("smartrecruiters.com", "SmartRecruiters"),
            Map.entry("workday.com", "Workday"), Map.entry("myworkdayjobs.com", "Workday"),
            Map.entry("linkedin.com", "LinkedIn"), Map.entry("indeed.com", "Indeed"),
            Map.entry("glassdoor.com", "Glassdoor"), Map.entry("vagas.com.br", "Vagas.com"),
            Map.entry("catho.com.br", "Catho"), Map.entry("infojobs.com.br", "InfoJobs"));

    @Override
    public ClassificationResult classify(IncomingEmail email) {
        String text = TextNormalizer.normalize(email.subject() + " " + email.body());
        String platform = platformOf(email.senderDomain());
        boolean relevant = mentionsAnApplication(text) || platform != null;

        return new ClassificationResult(
                relevant,
                relevant ? updateTypeOf(text) : UpdateType.OTHER,
                null,
                null,
                platform,
                summaryOf(email),
                relevant && isUrgent(text));
    }

    /**
     * A message from a hiring platform counts on its own: those systems only write to
     * people who are already in a process.
     */
    private static boolean mentionsAnApplication(String text) {
        return APPLICATION_EVIDENCE.stream().anyMatch(text::contains);
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

    /** Matches the domain and any subdomain of it, with the dot as the boundary. */
    private static String platformOf(String senderDomain) {
        String domain = TextNormalizer.normalize(senderDomain);
        return PLATFORM_BY_DOMAIN.entrySet().stream()
                .filter(entry -> domain.equals(entry.getKey())
                        || domain.endsWith("." + entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    /**
     * The subject, as written. It is not a summary of the email, and it does not pretend
     * to be one — it is the one line the sender already chose to describe the message.
     */
    private static String summaryOf(IncomingEmail email) {
        String subject = email.subject().strip();
        return subject.isEmpty() ? null : subject;
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

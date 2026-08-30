package com.nathanpaiva.jobtracker.domain;

import java.util.Locale;
import java.util.Set;

/**
 * The cheap first pass that decides which emails are worth sending to the classifier.
 *
 * <p>Classifying costs money and time, and most of a mailbox has nothing to do with job
 * applications. This filter runs first, for free, and only what it matches goes on to
 * the LLM.
 *
 * <p><strong>It is deliberately permissive.</strong> The two mistakes it can make do not
 * cost the same: a false positive spends one API call on an email that turns out to be
 * irrelevant, and the LLM corrects it a second later. A false negative means the email
 * is never looked at and never recorded — it disappears. So when in doubt, this filter
 * says yes and lets the LLM decide, which is also why the two answers are kept apart and
 * compared (see {@link EmailClassification#hasDisagreement()}).
 *
 * <p>The lists live here as constants rather than in configuration. That
 * {@code greenhouse.io} is a hiring platform is knowledge about the problem, not a
 * setting that changes per environment, and keeping it here is what lets this class stay
 * free of any framework. If the lists ever start changing weekly, moving them to
 * configuration is a small change.
 */
public final class RuleFilter {

    /**
     * Applicant tracking systems and job boards. Ordinary company domains are not listed
     * and do not need to be: an email from {@code acme.com} about a job still reaches the
     * classifier through its subject line.
     */
    private static final Set<String> JOB_PLATFORM_DOMAINS = Set.of(
            "greenhouse.io", "lever.co", "ashbyhq.com", "workable.com", "recruitee.com",
            "smartrecruiters.com", "jobvite.com", "icims.com", "taleo.net", "breezy.hr",
            "successfactors.com", "bamboohr.com", "teamtailor.com", "myworkdayjobs.com",
            "workday.com", "gupy.io", "kenoby.com", "solides.com", "linkedin.com",
            "indeed.com", "glassdoor.com", "vagas.com.br", "catho.com.br",
            "infojobs.com.br", "99jobs.com", "programathor.com.br");

    /**
     * Words that suggest a subject line is about a job application, in the two languages
     * these emails arrive in. Stored without accents, because the subject is stripped of
     * accents before the comparison.
     */
    private static final Set<String> SUBJECT_KEYWORDS = Set.of(
            "candidatura", "vaga", "processo seletivo", "selecao", "entrevista",
            "curriculo", "recrutamento", "oportunidade", "inscricao", "teste tecnico",
            "application", "position", "job", "interview", "recruit", "hiring",
            "opportunity", "candidate", "resume", "role");

    /**
     * Whether this email is worth classifying.
     *
     * <p>Either signal is enough. The domain alone catches platform emails whose subject
     * says nothing useful, and the subject alone catches a recruiter writing from an
     * ordinary company address.
     */
    public boolean matches(IncomingEmail email) {
        return isKnownJobPlatform(email.senderDomain()) || hasJobKeyword(email.subject());
    }

    /**
     * Matches the domain itself and any subdomain of it, so {@code careers.greenhouse.io}
     * counts. The dot is part of the comparison on purpose: without it,
     * {@code notgreenhouse.io} would match too, and a filter that quietly accepts
     * lookalike domains is worse than one that misses them.
     */
    private static boolean isKnownJobPlatform(String senderDomain) {
        String domain = senderDomain.toLowerCase(Locale.ROOT);
        return JOB_PLATFORM_DOMAINS.stream()
                .anyMatch(known -> domain.equals(known) || domain.endsWith("." + known));
    }

    /**
     * Only the subject is searched, not the body. The body would match almost anything
     * that mentions work in passing, and every extra match is an API call spent.
     */
    private static boolean hasJobKeyword(String subject) {
        String normalized = TextNormalizer.normalize(subject);
        return SUBJECT_KEYWORDS.stream().anyMatch(normalized::contains);
    }
}

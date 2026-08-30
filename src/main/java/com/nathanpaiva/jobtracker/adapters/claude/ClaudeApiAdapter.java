package com.nathanpaiva.jobtracker.adapters.claude;

import org.springframework.stereotype.Component;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.nathanpaiva.jobtracker.domain.ClassificationResult;
import com.nathanpaiva.jobtracker.domain.IncomingEmail;
import com.nathanpaiva.jobtracker.ports.ClassifierPort;

/**
 * Classifies an email by asking Claude.
 *
 * <p>The request carries three things: a fixed system prompt with the rules, the email
 * itself, and the shape the answer must take. The shape comes from
 * {@link ClaudeClassificationResponse}, which the SDK turns into a JSON schema, so the
 * reply arrives as a typed object and there is nothing to parse.
 */
@Component
class ClaudeApiAdapter implements ClassifierPort {

    /**
     * Enough room for the answer plus the model's own reasoning. The reply itself is a
     * handful of short fields, so this is not a limit anything realistic will reach.
     */
    private static final long MAX_TOKENS = 4096L;

    /**
     * The same on every call, which is what makes it a candidate for prompt caching
     * later on, once the pipeline is running.
     */
    private static final String SYSTEM_PROMPT = """
            You read one email at a time and report what it says about a job \
            application, filling in the fields you are given.

            The person receiving these emails is applying for backend developer roles. \
            An email is relevant only when it is about an application they have already \
            submitted. Job adverts, newsletters and invitations to apply are not \
            relevant: they are about jobs, but no application exists yet.

            Fill in a field only with what the email actually says. If the email does \
            not name the company, the role or the platform, leave that field empty. Do \
            not guess from the sender's domain, and do not carry information over from \
            what a similar email usually contains.

            The company is the one doing the hiring. It is not the platform the email \
            was sent through: an email from Greenhouse about a role at Acme has Acme as \
            the company and Greenhouse as the platform.

            Write the summary in the same language as the email.
            """;

    private final AnthropicClient client;
    private final String model;

    ClaudeApiAdapter(AnthropicClient client, ClaudeProperties properties) {
        this.client = client;
        this.model = properties.model();
    }

    @Override
    public ClassificationResult classify(IncomingEmail email) {
        StructuredMessageCreateParams<ClaudeClassificationResponse> params =
                MessageCreateParams.builder()
                        .model(model)
                        .maxTokens(MAX_TOKENS)
                        .system(SYSTEM_PROMPT)
                        .addUserMessage(asPrompt(email))
                        .outputConfig(ClaudeClassificationResponse.class)
                        .build();

        return client.messages().create(params).content().stream()
                .flatMap(block -> block.text().stream())
                .findFirst()
                .map(answer -> answer.text().toDomain())
                .orElseThrow(() -> new IllegalStateException(
                        "no answer returned for message " + email.gmailMessageId()));
    }

    /**
     * The email as the model sees it.
     *
     * <p>The sender domain is included because it is often the only clue about the
     * platform, and the subject because a lot of emails say everything there and repeat
     * nothing in the body.
     */
    private static String asPrompt(IncomingEmail email) {
        return """
                Sender domain: %s
                Subject: %s

                %s""".formatted(email.senderDomain(), email.subject(), email.body());
    }
}

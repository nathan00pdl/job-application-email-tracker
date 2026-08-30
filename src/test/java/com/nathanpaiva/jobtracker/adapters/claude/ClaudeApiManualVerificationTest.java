package com.nathanpaiva.jobtracker.adapters.claude;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.nathanpaiva.jobtracker.domain.ClassificationResult;
import com.nathanpaiva.jobtracker.domain.IncomingEmail;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sends one made-up email to the real API, to see what the model actually answers.
 *
 * <p>It runs only when {@code ANTHROPIC_API_KEY} is set, so CI skips it. This one costs
 * money — a fraction of a cent per run — which is another reason it is not automatic.
 *
 * <pre>
 * set -a &amp;&amp; source .env &amp;&amp; set +a
 * mvn test -Dtest=ClaudeApiManualVerificationTest
 * </pre>
 *
 * <p>The email is written here rather than read from the mailbox: the point is to see
 * the classification, and a fixed input makes the answer comparable between runs.
 */
class ClaudeApiManualVerificationTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
    void classifiesAnInterviewInvitation() {
        ClaudeApiAdapter adapter = new ClaudeApiAdapter(
                AnthropicOkHttpClient.builder()
                        .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                        .build(),
                new ClaudeProperties(System.getenv("ANTHROPIC_API_KEY"), "claude-opus-5"));

        IncomingEmail email = new IncomingEmail(
                "manual-check", Instant.now(), "greenhouse.io",
                "Próxima etapa - Acme Corp",
                """
                Olá Nathan,

                Obrigado pelo seu interesse na vaga de Desenvolvedor Backend Java na
                Acme Corp. Gostaríamos de convidá-lo para uma entrevista técnica.

                Por favor, confirme sua disponibilidade até sexta-feira.

                Atenciosamente,
                Equipe de Recrutamento
                """);

        ClassificationResult result = adapter.classify(email);

        System.out.println("relevant   : " + result.relevant());
        System.out.println("updateType : " + result.updateType());
        System.out.println("company    : " + result.company());
        System.out.println("roleTitle  : " + result.roleTitle());
        System.out.println("platform   : " + result.platform());
        System.out.println("summary    : " + result.summary());
        System.out.println("urgent     : " + result.urgent());

        assertThat(result.relevant()).isTrue();
    }
}

package com.nathanpaiva.jobtracker.adapters.claude;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.nathanpaiva.jobtracker.domain.ClassificationResult;
import com.nathanpaiva.jobtracker.domain.IncomingEmail;
import com.nathanpaiva.jobtracker.domain.UpdateType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the adapter, with Anthropic replaced by a local HTTP server.
 *
 * <p>Answering real HTTP rather than mocking the SDK means the request the adapter
 * actually sends is exercised — the model id, the system prompt, the email, and the
 * schema derived from the response record. One of the tests reads that request back and
 * checks what went out, which no mock of the client could show.
 */
class ClaudeApiAdapterTest {

    private HttpServer server;
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();
    private String responseBody;
    private int responseStatus = 200;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            try (InputStream body = exchange.getRequestBody()) {
                lastRequestBody.set(new String(body.readAllBytes(), StandardCharsets.UTF_8));
            }
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void turnsTheModelAnswerIntoADomainResult() {
        responseBody = answerWith("""
                {"relevant": true, "updateType": "INTERVIEW_INVITE", \
                "company": "Acme Corp", "roleTitle": "Backend Engineer", \
                "platform": "Greenhouse", "summary": "Entrevista na terça", \
                "urgent": true}""");

        ClassificationResult result = adapter().classify(email("Convite para entrevista"));

        assertThat(result.relevant()).isTrue();
        assertThat(result.updateType()).isEqualTo(UpdateType.INTERVIEW_INVITE);
        assertThat(result.company()).isEqualTo("Acme Corp");
        assertThat(result.roleTitle()).isEqualTo("Backend Engineer");
        assertThat(result.platform()).isEqualTo("Greenhouse");
        assertThat(result.summary()).isEqualTo("Entrevista na terça");
        assertThat(result.urgent()).isTrue();
    }

    /** An email that names no company or role must come back with nulls, not guesses. */
    @Test
    void keepsEmptyWhatTheEmailDidNotSay() {
        responseBody = answerWith("""
                {"relevant": true, "updateType": "REJECTION", "company": null, \
                "roleTitle": null, "platform": null, "summary": "Seguiram com outro", \
                "urgent": false}""");

        ClassificationResult result = adapter().classify(email("Retorno do processo"));

        assertThat(result.company()).isNull();
        assertThat(result.roleTitle()).isNull();
        assertThat(result.platform()).isNull();
        assertThat(result.updateType()).isEqualTo(UpdateType.REJECTION);
    }

    @Test
    void sendsTheEmailAndTheSchemaToTheModel() {
        responseBody = answerWith("""
                {"relevant": false, "updateType": "OTHER", "company": null, \
                "roleTitle": null, "platform": null, "summary": null, "urgent": false}""");

        adapter().classify(email("Sua fatura chegou"));

        String request = lastRequestBody.get();
        assertThat(request).contains("claude-opus-5");
        assertThat(request).contains("Sua fatura chegou");
        assertThat(request).contains("greenhouse.io");
        assertThat(request).contains("corpo do email");
        // the seven categories travel with the request, which is what keeps the answer
        // inside the enum
        assertThat(request).contains("INTERVIEW_INVITE").contains("REJECTION");
    }

    @Test
    void failsWhenTheModelReturnsNothing() {
        responseBody = """
                {"id": "msg_1", "type": "message", "role": "assistant", \
                "model": "claude-opus-5", "content": [], "stop_reason": "end_turn", \
                "usage": {"input_tokens": 10, "output_tokens": 0}}""";

        assertThatThrownBy(() -> adapter().classify(email("assunto")))
                .isInstanceOf(IllegalStateException.class);
    }

    // --- helpers ---

    private ClaudeApiAdapter adapter() {
        return new ClaudeApiAdapter(
                AnthropicOkHttpClient.builder()
                        .apiKey("test-api-key")
                        .baseUrl("http://localhost:" + server.getAddress().getPort())
                        .build(),
                new ClaudeProperties("test-api-key", "claude-opus-5"));
    }

    /** The Messages API shape: the structured answer arrives as the text of one block. */
    private static String answerWith(String json) {
        String escaped = json.replace("\\", "\\\\").replace("\"", "\\\"");
        return """
                {"id": "msg_1", "type": "message", "role": "assistant", \
                "model": "claude-opus-5", \
                "content": [{"type": "text", "text": "%s"}], \
                "stop_reason": "end_turn", \
                "usage": {"input_tokens": 10, "output_tokens": 20}}""".formatted(escaped);
    }

    private static IncomingEmail email(String subject) {
        return new IncomingEmail("gmail-id", Instant.parse("2026-08-30T10:00:00Z"),
                "greenhouse.io", subject, "corpo do email");
    }
}

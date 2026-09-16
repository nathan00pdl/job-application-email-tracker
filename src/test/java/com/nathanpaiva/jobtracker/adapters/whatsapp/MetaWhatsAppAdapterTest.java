package com.nathanpaiva.jobtracker.adapters.whatsapp;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.nathanpaiva.jobtracker.domain.DailyDigest;
import com.nathanpaiva.jobtracker.domain.UpdateType;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the adapter that sends the digest to Meta.
 *
 * <p>Meta is played by a real HTTP server from the JDK, listening on this machine only.
 * The adapter makes a real request to it, so what is checked is the request as it leaves —
 * address, headers, body — and what the adapter does with each kind of answer. No network
 * beyond this machine, no token, no Meta.
 */
class MetaWhatsAppAdapterTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final String TOKEN = "test-token-EAAG";
    private static final String PHONE_NUMBER_ID = "123456789012345";

    /** A made-up number, written the way the Meta panel shows one. */
    private static final String RECIPIENT = "+55-11-90000-0000";
    private static final String RECIPIENT_DIGITS = "5511900000000";

    private static final Instant SENT_AT = Instant.parse("2026-09-15T09:00:00Z");

    private static final String ACCEPTED =
            "{\"messaging_product\":\"whatsapp\",\"messages\":[{\"id\":\"wamid.TEST\"}]}";

    private final AtomicReference<Request> received = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String answer = ACCEPTED;
    private HttpServer meta;

    private record Request(String method, String path, String authorization,
                           String contentType, String body) {
    }

    @BeforeEach
    void startMeta() throws IOException {
        meta = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        meta.createContext("/", exchange -> {
            received.set(new Request(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            byte[] bytes = answer.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        meta.start();
    }

    @AfterEach
    void stopMeta() {
        meta.stop(0);
    }

    @Test
    void postsToTheTestNumberWithTheToken() {
        adapter().send(digest());

        Request request = received.get();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/v25.0/" + PHONE_NUMBER_ID + "/messages");
        assertThat(request.authorization()).isEqualTo("Bearer " + TOKEN);
        assertThat(request.contentType()).isEqualTo("application/json");
    }

    /** The approved template, by name and language, with the five values in order. */
    @Test
    void sendsTheTemplateWithTheFiveValuesInOrder() {
        adapter().send(digest());

        JsonNode body = JSON.readTree(received.get().body());
        assertThat(body.path("messaging_product").asString()).isEqualTo("whatsapp");
        assertThat(body.path("type").asString()).isEqualTo("template");
        assertThat(body.at("/template/name").asString()).isEqualTo("resumo_diario");
        assertThat(body.at("/template/language/code").asString()).isEqualTo("pt_BR");
        assertThat(body.at("/template/components/0/type").asString()).isEqualTo("body");

        List<String> values = new ArrayList<>();
        body.at("/template/components/0/parameters")
                .forEach(parameter -> values.add(parameter.path("text").asString()));
        assertThat(values).containsExactlyElementsOf(
                DigestMessage.forDigest(digest(), SENT_AT).values());
    }

    /** A day with no news goes out through its own template, with the date as its one value. */
    @Test
    void sendsTheEmptyTemplateOnADayWithNoNews() {
        adapter().send(DailyDigest.empty());

        JsonNode body = JSON.readTree(received.get().body());
        assertThat(body.at("/template/name").asString()).isEqualTo("resumo_diario_vazio");
        assertThat(body.at("/template/language/code").asString()).isEqualTo("pt_BR");

        List<String> values = new ArrayList<>();
        body.at("/template/components/0/parameters")
                .forEach(parameter -> values.add(parameter.path("text").asString()));
        assertThat(values).containsExactly("15/09");
    }

    /** A number pasted the way the Meta panel shows it still goes out as digits. */
    @Test
    void sendsTheRecipientAsDigitsOnly() {
        adapter().send(digest());

        assertThat(JSON.readTree(received.get().body()).path("to").asString())
                .isEqualTo(RECIPIENT_DIGITS);
    }

    @Test
    void failsWithMetasCodeAndMessageWhenRefused() {
        status = 401;
        answer = "{\"error\":{\"message\":\"Error validating access token: Session has expired.\","
                + "\"type\":\"OAuthException\",\"code\":190,\"fbtrace_id\":\"A1\"}}";

        assertThatThrownBy(() -> adapter().send(digest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("WhatsApp refused the digest: HTTP 401, code 190: "
                        + "Error validating access token: Session has expired.");
    }

    /**
     * The run's log is public. If Meta ever repeats the token or the number in an error,
     * the adapter must not pass either of them on.
     */
    @Test
    void neverRepeatsTheTokenOrTheNumberInAnError() {
        status = 400;
        answer = "{\"error\":{\"message\":\"Recipient " + RECIPIENT_DIGITS + " refused for "
                + TOKEN + "\",\"code\":131030}}";

        assertThatThrownBy(() -> adapter().send(digest()))
                .hasMessageContaining("code 131030")
                .hasMessageNotContaining(TOKEN)
                .hasMessageNotContaining(RECIPIENT_DIGITS);
    }

    /** A proxy's HTML page is not repeated: the body could hold anything. */
    @Test
    void reportsOnlyTheStatusWhenTheAnswerIsNotMetas() {
        status = 502;
        answer = "<html><body>Bad Gateway</body></html>";

        assertThatThrownBy(() -> adapter().send(digest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("WhatsApp refused the digest: HTTP 502");
    }

    /** Port 1 on this machine: nothing listens there, so the connection is refused. */
    @Test
    void failsWhenMetaCannotBeReached() {
        MetaWhatsAppAdapter unreachable = adapter(URI.create("http://127.0.0.1:1/v25.0"));

        assertThatThrownBy(() -> unreachable.send(digest()))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessage("could not reach WhatsApp");
    }

    private MetaWhatsAppAdapter adapter() {
        return adapter(URI.create("http://127.0.0.1:" + meta.getAddress().getPort() + "/v25.0"));
    }

    private static MetaWhatsAppAdapter adapter(URI apiUrl) {
        return new MetaWhatsAppAdapter(
                HttpClient.newHttpClient(),
                new WhatsAppProperties(TOKEN, PHONE_NUMBER_ID, RECIPIENT, apiUrl),
                Clock.fixed(SENT_AT, ZoneOffset.UTC));
    }

    private static DailyDigest digest() {
        return new DailyDigest(3, Map.of(UpdateType.INTERVIEW_INVITE, 1, UpdateType.REJECTION, 2),
                1, List.of("Gupy"),
                Instant.parse("2026-09-14T12:00:00Z"), Instant.parse("2026-09-14T20:00:00Z"));
    }
}

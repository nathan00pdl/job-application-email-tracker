package com.nathanpaiva.jobtracker.adapters.whatsapp;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.nathanpaiva.jobtracker.domain.DailyDigest;
import com.nathanpaiva.jobtracker.ports.NotificationPort;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sends the daily digest as a WhatsApp template, through Meta's Cloud API.
 *
 * <p>One request per digest: a POST to {@code /{phone-number-id}/messages} naming the
 * approved template and carrying the ten values {@link DigestMessage} fills — the
 * counts, the emails that wait on the reader with a link to each, and a link to the
 * spreadsheet. Meta answers with a 2xx when it accepts the message, and that is all this
 * class waits for. Whether the message reached the phone is reported later, through a
 * webhook this project does not run.
 *
 * <p><b>Nothing is retried.</b> A refusal, or a Meta that cannot be reached, ends the run,
 * and the failure opens an issue. Stopping loses nothing: the classifications stay marked
 * as not sent, and the next run sends them again.
 *
 * <p><b>Nothing secret leaves in an error.</b> The run's log is public, so a failure names
 * Meta's error code and message, never the token or the recipient. Meta's message is
 * scrubbed of both before it is repeated, in case it ever echoes one back.
 */
@Component
class MetaWhatsAppAdapter implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(MetaWhatsAppAdapter.class);

    /** How long to wait for Meta's answer once connected. */
    private static final Duration ANSWER_TIMEOUT = Duration.ofSeconds(30);

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final HttpClient http;
    private final Clock clock;
    private final String accessToken;
    private final String recipient;
    private final String spreadsheetId;
    private final URI messagesEndpoint;

    MetaWhatsAppAdapter(HttpClient whatsAppHttpClient, WhatsAppProperties properties,
                        Clock clock) {
        this.http = whatsAppHttpClient;
        this.clock = clock;
        this.accessToken = properties.accessToken();
        this.recipient = digitsOf(properties.recipient());
        this.spreadsheetId = properties.spreadsheetId();
        String base = properties.apiUrl().toString().replaceAll("/+$", "");
        this.messagesEndpoint = URI.create(base + "/" + properties.phoneNumberId() + "/messages");
    }

    @Override
    public void send(DailyDigest digest) {
        DigestMessage.Template template =
                DigestMessage.forDigest(digest, clock.instant(), spreadsheetId);

        HttpRequest request = HttpRequest.newBuilder(messagesEndpoint)
                .timeout(ANSWER_TIMEOUT)
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        JSON.writeValueAsString(templateMessage(template))))
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException("could not reach WhatsApp", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while sending the digest", e);
        }

        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("WhatsApp refused the digest: " + refusal(response));
        }

        // What was sent, not what came back: reading the answer to log it would let an
        // absent field turn a message Meta accepted into a failed run.
        log.info("sent the digest over WhatsApp: {} updates, {} urgent, {} ask for action",
                digest.total(), digest.urgent(), digest.actions().size());
    }

    /**
     * The body Meta expects for a template: who receives it, the template's name and
     * language, and the values of its body, in the order of its blanks.
     */
    private Map<String, Object> templateMessage(DigestMessage.Template template) {
        return Map.of(
                "messaging_product", "whatsapp",
                "to", recipient,
                "type", "template",
                "template", Map.of(
                        "name", template.name(),
                        "language", Map.of("code", DigestMessage.LANGUAGE),
                        "components", List.of(Map.of(
                                "type", "body",
                                "parameters", template.values().stream()
                                        .map(value -> Map.of("type", "text", "text", value))
                                        .toList()))));
    }

    /**
     * "HTTP 401, code 190: Error validating access token" — enough to know what to fix.
     *
     * <p>Meta's errors are JSON, with a code and a message. When the answer is anything
     * else — a proxy's HTML page, say — only the status is reported, because the body could
     * hold anything.
     */
    private String refusal(HttpResponse<String> response) {
        String status = "HTTP " + response.statusCode();
        try {
            JsonNode error = JSON.readTree(response.body()).path("error");
            if (error.isMissingNode()) {
                return status;
            }
            return status + ", code " + error.path("code").asString("?") + ": "
                    + scrubbed(error.path("message").asString(""));
        } catch (JacksonException e) {
            return status;
        }
    }

    /** Takes the token and the recipient out of text that is about to reach the log. */
    private String scrubbed(String text) {
        String result = text;
        if (!accessToken.isBlank()) {
            result = result.replace(accessToken, "***");
        }
        if (!recipient.isBlank()) {
            result = result.replace(recipient, "***");
        }
        return result;
    }

    /**
     * Meta wants the number as digits, with the country code. Anything else pasted into
     * the setting — a plus sign, spaces, dashes — is dropped here, so a number copied the
     * way the Meta panel shows it still works.
     */
    private static String digitsOf(String number) {
        return number == null ? "" : number.replaceAll("\\D", "");
    }
}

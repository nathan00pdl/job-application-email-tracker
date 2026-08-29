package com.nathanpaiva.jobtracker.adapters.gmail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.nathanpaiva.jobtracker.domain.IncomingEmail;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the adapter, with Google replaced by a stub transport.
 *
 * <p>The stub answers HTTP, not Java calls. That matters: mocking the {@code Gmail}
 * class would only prove the adapter calls the methods it was written to call. Answering
 * at the transport level exercises the request the adapter actually builds — the query
 * string, the page token, the parsing of the reply — which is where the mistakes are.
 */
class GmailApiAdapterTest {

    private static final Instant SINCE = Instant.parse("2026-08-29T00:00:00Z");
    private static final long RECEIVED_AT_MILLIS = 1_756_000_000_000L;

    private final List<String> requestedUrls = new ArrayList<>();

    @Test
    void readsEveryPageAndReturnsTheEmailsOldestFirst() {
        Map<String, Message> mailbox = new LinkedHashMap<>();
        mailbox.put("m1", message("m1", RECEIVED_AT_MILLIS + 2_000, "greenhouse.io", "terceiro"));
        mailbox.put("m2", message("m2", RECEIVED_AT_MILLIS, "lever.co", "primeiro"));
        mailbox.put("m3", message("m3", RECEIVED_AT_MILLIS + 1_000, "gupy.io", "segundo"));

        GmailApiAdapter adapter = adapterFor(mailbox, List.of(List.of("m1", "m2"), List.of("m3")));

        List<IncomingEmail> emails = adapter.fetchReceivedAfter(SINCE);

        assertThat(emails).extracting(IncomingEmail::body)
                .containsExactly("primeiro", "segundo", "terceiro");
        assertThat(emails).extracting(IncomingEmail::senderDomain)
                .containsExactly("lever.co", "gupy.io", "greenhouse.io");
    }

    /** Without following nextPageToken, a busy day would silently lose the extra pages. */
    @Test
    void followsThePageTokenUntilThereIsNoneLeft() {
        Map<String, Message> mailbox = new LinkedHashMap<>();
        mailbox.put("m1", message("m1", RECEIVED_AT_MILLIS, "greenhouse.io", "um"));
        mailbox.put("m2", message("m2", RECEIVED_AT_MILLIS, "greenhouse.io", "dois"));

        GmailApiAdapter adapter = adapterFor(mailbox, List.of(List.of("m1"), List.of("m2")));

        assertThat(adapter.fetchReceivedAfter(SINCE)).hasSize(2);
        assertThat(listRequests()).hasSize(2);
        assertThat(listRequests().get(1)).contains("pageToken=");
    }

    @Test
    void asksGmailOnlyForMessagesReceivedAfterTheGivenInstant() {
        GmailApiAdapter adapter = adapterFor(Map.of(), List.of(List.of()));

        adapter.fetchReceivedAfter(SINCE);

        assertThat(listRequests().getFirst()).contains(String.valueOf(SINCE.getEpochSecond()));
    }

    /**
     * One unreadable email must not cost the whole day. The pipeline records per-item
     * failures and steps over them.
     */
    @Test
    void skipsAMessageItCannotUnderstandAndKeepsTheRest() {
        Message withoutSender = message("m2", RECEIVED_AT_MILLIS, "greenhouse.io", "corpo");
        withoutSender.getPayload().setHeaders(List.of(header("Subject", "sem remetente")));

        Map<String, Message> mailbox = new LinkedHashMap<>();
        mailbox.put("m1", message("m1", RECEIVED_AT_MILLIS, "greenhouse.io", "válido"));
        mailbox.put("m2", withoutSender);

        GmailApiAdapter adapter = adapterFor(mailbox, List.of(List.of("m1", "m2")));

        assertThat(adapter.fetchReceivedAfter(SINCE))
                .extracting(IncomingEmail::body)
                .containsExactly("válido");
    }

    @Test
    void returnsNothingWhenTheMailboxHasNoNewEmail() {
        GmailApiAdapter adapter = adapterFor(Map.of(), List.of(List.of()));

        assertThat(adapter.fetchReceivedAfter(SINCE)).isEmpty();
    }

    // --- the stub ---

    /**
     * Answers the two calls the adapter makes: a list of ids, page by page, and one
     * message per id.
     */
    private GmailApiAdapter adapterFor(Map<String, Message> mailbox, List<List<String>> pages) {
        MockHttpTransport transport = new MockHttpTransport() {
            @Override
            public LowLevelHttpRequest buildRequest(String method, String url) {
                requestedUrls.add(url);
                String body = url.contains("/messages/")
                        ? json(mailbox.get(messageIdIn(url)))
                        : json(pageFor(url, pages));
                return new MockLowLevelHttpRequest() {
                    @Override
                    public LowLevelHttpResponse execute() {
                        return new MockLowLevelHttpResponse()
                                .setStatusCode(200)
                                .setContentType("application/json")
                                .setContent(body);
                    }
                };
            }
        };

        Gmail gmail = new Gmail.Builder(transport, GsonFactory.getDefaultInstance(), null)
                .setApplicationName("test")
                .build();
        return new GmailApiAdapter(gmail);
    }

    private static ListMessagesResponse pageFor(String url, List<List<String>> pages) {
        int pageNumber = url.contains("pageToken=") ? pageIndexIn(url) : 0;
        ListMessagesResponse response = new ListMessagesResponse();
        List<String> ids = pages.get(pageNumber);
        if (!ids.isEmpty()) {
            response.setMessages(ids.stream().map(id -> new Message().setId(id)).toList());
        }
        if (pageNumber + 1 < pages.size()) {
            response.setNextPageToken("page-" + (pageNumber + 1));
        }
        return response;
    }

    private static int pageIndexIn(String url) {
        String token = url.substring(url.indexOf("pageToken=") + "pageToken=".length());
        int end = token.indexOf('&');
        return Integer.parseInt((end < 0 ? token : token.substring(0, end)).replace("page-", ""));
    }

    private static String messageIdIn(String url) {
        String tail = url.substring(url.indexOf("/messages/") + "/messages/".length());
        int end = tail.indexOf('?');
        return end < 0 ? tail : tail.substring(0, end);
    }

    private static String json(Object value) {
        try {
            return GsonFactory.getDefaultInstance().toString(value);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<String> listRequests() {
        return requestedUrls.stream().filter(url -> !url.contains("/messages/")).toList();
    }

    private static Message message(String id, long receivedAt, String senderDomain, String body) {
        MessagePart payload = new MessagePart()
                .setMimeType("text/plain")
                .setBody(new MessagePartBody().setData(Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(body.getBytes(StandardCharsets.UTF_8))))
                .setHeaders(List.of(
                        header("Subject", "Sua candidatura"),
                        header("From", "Careers <no-reply@" + senderDomain + ">")));
        return new Message().setId(id).setInternalDate(receivedAt).setPayload(payload);
    }

    private static MessagePartHeader header(String name, String value) {
        return new MessagePartHeader().setName(name).setValue(value);
    }
}

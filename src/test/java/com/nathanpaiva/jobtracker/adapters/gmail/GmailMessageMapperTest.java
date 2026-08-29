package com.nathanpaiva.jobtracker.adapters.gmail;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.nathanpaiva.jobtracker.domain.IncomingEmail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the Gmail to domain translation.
 *
 * <p>No network and no credentials: every message here is built by hand, which is only
 * possible because the mapper is a pure function kept apart from the adapter that calls
 * Google. These are the awkward cases a real mailbox produces.
 */
class GmailMessageMapperTest {

    private static final long RECEIVED_AT_MILLIS = 1_756_000_000_000L;

    @Test
    void mapsASimpleTextMessage() {
        Message message = message(part("text/plain", "Podemos conversar amanhã?"));

        IncomingEmail email = GmailMessageMapper.toIncomingEmail(message);

        assertThat(email.gmailMessageId()).isEqualTo("18f2a9c3d4e5b6a7");
        assertThat(email.receivedAt()).isEqualTo(Instant.ofEpochMilli(RECEIVED_AT_MILLIS));
        assertThat(email.senderDomain()).isEqualTo("greenhouse.io");
        assertThat(email.subject()).isEqualTo("Sua candidatura");
        assertThat(email.body()).isEqualTo("Podemos conversar amanhã?");
    }

    /**
     * Accented text is exactly what breaks when the standard base64 decoder is used
     * instead of the URL-safe one: the bytes differ only where - and _ appear.
     */
    @Test
    void decodesAccentedTextCorrectly() {
        Message message = message(part("text/plain", "Seleção, inscrição e avaliação — ação"));

        assertThat(GmailMessageMapper.toIncomingEmail(message).body())
                .isEqualTo("Seleção, inscrição e avaliação — ação");
    }

    @Test
    void prefersThePlainTextPartOverTheHtmlOne() {
        Message message = message(multipart("multipart/alternative",
                part("text/html", "<p>versão HTML</p>"),
                part("text/plain", "versão texto")));

        assertThat(GmailMessageMapper.toIncomingEmail(message).body()).isEqualTo("versão texto");
    }

    /** A real message often nests multipart inside multipart, with an attachment beside. */
    @Test
    void findsThePlainTextPartNestedDeeperInTheTree() {
        Message message = message(multipart("multipart/mixed",
                multipart("multipart/alternative",
                        part("text/html", "<p>ignorado</p>"),
                        part("text/plain", "texto no fundo da árvore")),
                part("application/pdf", "conteudo-binario")));

        assertThat(GmailMessageMapper.toIncomingEmail(message).body())
                .isEqualTo("texto no fundo da árvore");
    }

    /**
     * Note what survives in the expected value: {@code &aacute;} and {@code &eacute;} are
     * left as written. The stripper only undoes the handful of entities that show up in
     * practice, and this test pins that limit down rather than hiding it. The output is
     * read by the classifier, which understands the words either way.
     */
    @Test
    void fallsBackToTheHtmlPartWithTagsRemoved() {
        Message message = message(multipart("multipart/alternative",
                part("text/html",
                        "<html><style>p{color:red}</style><body><p>Ol&aacute;</p>"
                                + "<p>Voc&#39;&nbsp;foi <b>aprovado</b> &amp; parab&eacute;ns</p>"
                                + "<script>track()</script></body></html>")));

        assertThat(GmailMessageMapper.toIncomingEmail(message).body())
                .isEqualTo("Ol&aacute; Voc' foi aprovado & parab&eacute;ns");
    }

    @ParameterizedTest
    @CsvSource({
            "'Acme Careers <no-reply@greenhouse.io>', greenhouse.io",
            "'no-reply@greenhouse.io',                greenhouse.io",
            "'\"Careers, Acme\" <talent@ACME.CO.UK>',  acme.co.uk",
            "'Recrutamento <rh@sub.dominio-com-hifen.com.br>', sub.dominio-com-hifen.com.br"
    })
    void extractsTheDomainFromEveryShapeOfFromHeader(String fromHeader, String expectedDomain) {
        Message message = message(part("text/plain", "corpo"));
        message.getPayload().setHeaders(List.of(
                header("Subject", "Sua candidatura"),
                header("From", fromHeader)));

        assertThat(GmailMessageMapper.toIncomingEmail(message).senderDomain())
                .isEqualTo(expectedDomain);
    }

    @Test
    void usesAnEmptySubjectWhenTheHeaderIsMissing() {
        Message message = message(part("text/plain", "corpo"));
        message.getPayload().setHeaders(List.of(header("From", "a@greenhouse.io")));

        assertThat(GmailMessageMapper.toIncomingEmail(message).subject()).isEmpty();
    }

    @Test
    void usesAnEmptyBodyWhenNoReadablePartExists() {
        Message message = message(part("application/pdf", "so-anexo"));

        assertThat(GmailMessageMapper.toIncomingEmail(message).body()).isEmpty();
    }

    /**
     * Without a sender there is no domain, and the domain is what the rule filter runs
     * on. Failing here is better than inventing a value; the adapter logs the message
     * and moves on to the next one.
     */
    @Test
    void refusesAMessageWithoutAFromHeader() {
        Message message = message(part("text/plain", "corpo"));
        message.getPayload().setHeaders(List.of(header("Subject", "Sua candidatura")));

        assertThatThrownBy(() -> GmailMessageMapper.toIncomingEmail(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("From");
    }

    @Test
    void refusesAMessageFetchedWithoutItsPayload() {
        Message message = new Message().setId("18f2a9c3d4e5b6a7").setInternalDate(RECEIVED_AT_MILLIS);

        assertThatThrownBy(() -> GmailMessageMapper.toIncomingEmail(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payload");
    }

    // --- helpers that build the shapes the Gmail API returns ---

    private static Message message(MessagePart payload) {
        payload.setHeaders(List.of(
                header("Subject", "Sua candidatura"),
                header("From", "Acme Careers <no-reply@greenhouse.io>")));
        return new Message()
                .setId("18f2a9c3d4e5b6a7")
                .setInternalDate(RECEIVED_AT_MILLIS)
                .setPayload(payload);
    }

    private static MessagePart part(String mimeType, String content) {
        return new MessagePart()
                .setMimeType(mimeType)
                .setBody(new MessagePartBody().setData(base64Url(content)));
    }

    private static MessagePart multipart(String mimeType, MessagePart... children) {
        return new MessagePart().setMimeType(mimeType).setParts(List.of(children));
    }

    private static MessagePartHeader header(String name, String value) {
        return new MessagePartHeader().setName(name).setValue(value);
    }

    /** Gmail uses the URL-safe alphabet, without padding. */
    private static String base64Url(String content) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(content.getBytes(StandardCharsets.UTF_8));
    }
}

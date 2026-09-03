package com.nathanpaiva.jobtracker.adapters.sheets;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.sheets.v4.Sheets;
import com.nathanpaiva.jobtracker.domain.EmailClassification;
import com.nathanpaiva.jobtracker.domain.UpdateType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the spreadsheet adapter, with Google replaced by a stub transport.
 *
 * <p>Answering HTTP rather than mocking the client means the request that actually goes
 * out is what gets checked — the target range, the write options, and the shape of every
 * row. Those are the parts that are wrong when a sheet ends up looking strange.
 */
class GoogleSheetsAdapterTest {

    private static final Instant RECEIVED_AT = Instant.parse("2026-09-03T13:45:07Z");

    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastUrl = new AtomicReference<>();
    private final AtomicInteger requests = new AtomicInteger();

    @Test
    void writesOneRowPerClassificationInAFixedOrder() {
        adapter("classifications").append(List.of(new EmailClassification(
                "gmail-id-1", RECEIVED_AT, "greenhouse.io", "Greenhouse", "Acme Corp",
                "Backend Engineer", UpdateType.INTERVIEW_INVITE, "Entrevista terça", true)));

        assertThat(lastBody.get())
                .contains("gmail-id-1")
                .contains("2026-09-03 13:45:07 UTC")
                .contains("greenhouse.io")
                .contains("Greenhouse")
                .contains("Acme Corp")
                .contains("Backend Engineer")
                .contains("INTERVIEW_INVITE")
                .contains("yes")
                .contains("Entrevista terça");
    }

    /** The API refuses a null inside a row, so an unknown value becomes an empty cell. */
    @Test
    void writesEmptyCellsForWhatTheEmailDidNotSay() {
        adapter("classifications").append(List.of(new EmailClassification(
                "gmail-id-2", RECEIVED_AT, "gupy.io", null, null, null,
                UpdateType.REJECTION, null, false)));

        assertThat(lastBody.get()).doesNotContain("null");
        assertThat(requests.get()).isEqualTo(1);
    }

    /**
     * The timestamp is written as text in a fixed format. Left to the spreadsheet, the
     * order of day and month is read from whoever opens it.
     */
    @Test
    void writesTheTimestampInAnUnambiguousFormat() {
        adapter("classifications").append(List.of(classification()));

        assertThat(lastBody.get()).contains("2026-09-03 13:45:07 UTC");
    }

    @Test
    void appendsToTheConfiguredTab() {
        adapter("minha-aba").append(List.of(classification()));

        assertThat(lastUrl.get()).contains("minha-aba");
        assertThat(lastUrl.get()).contains("valueInputOption=RAW");
        assertThat(lastUrl.get()).contains("insertDataOption=INSERT_ROWS");
    }

    /** Nothing to append is not a reason to call Google. */
    @Test
    void doesNotCallGoogleWhenThereIsNothingToWrite() {
        adapter("classifications").append(List.of());

        assertThat(requests.get()).isZero();
    }

    private GoogleSheetsAdapter adapter(String sheetName) {
        MockHttpTransport transport = new MockHttpTransport() {
            @Override
            public LowLevelHttpRequest buildRequest(String method, String url) {
                requests.incrementAndGet();
                lastUrl.set(url);
                return new MockLowLevelHttpRequest(url) {
                    @Override
                    public LowLevelHttpResponse execute() throws java.io.IOException {
                        lastBody.set(getContentAsString());
                        return new MockLowLevelHttpResponse()
                                .setStatusCode(200)
                                .setContentType("application/json")
                                .setContent("{}");
                    }
                };
            }
        };

        Sheets sheets = new Sheets.Builder(transport, GsonFactory.getDefaultInstance(), null)
                .setApplicationName("test")
                .build();
        return new GoogleSheetsAdapter(sheets,
                new GoogleSheetsProperties("unused", "test-spreadsheet-id", sheetName));
    }

    private static EmailClassification classification() {
        return new EmailClassification("gmail-id", RECEIVED_AT, "greenhouse.io", null,
                null, null, UpdateType.OTHER, null, false);
    }
}

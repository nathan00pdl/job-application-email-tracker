package com.nathanpaiva.jobtracker;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for tests that need a real database.
 *
 * <p>The container is a singleton: it starts once in a static block and is never
 * stopped by a test class. Testcontainers' own JUnit extension ({@code @Testcontainers}
 * plus {@code @Container}) would stop it after each test class and start a new one for
 * the next. The new container gets a different random port, but Spring reuses the
 * application context it cached, which still points at the old one. Letting the JVM own
 * the container avoids this: Testcontainers removes it when the JVM exits.
 *
 * <p>It is public so integration tests can live in the package of the code they
 * cover, instead of all being pulled into this one.
 *
 * <p>{@code @ServiceConnection} passes the container's real URL, username and password
 * to the application's DataSource. Flyway then runs the same migrations used in
 * production, and the tests use the same configuration as the application.
 */
@SpringBootTest(properties = {
        // The real configuration gives the database username and password no default
        // value on purpose, so a badly configured deployment fails at startup instead of
        // connecting as some hardcoded user. Tests still have to provide something for
        // those two keys, and @ServiceConnection then replaces it with the container's
        // real values before any connection is opened. Overriding the keys here, instead
        // of adding a test application.yml, keeps the real file as the only source of
        // configuration: Spring loads only one application.yml, and the one on the test
        // classpath would hide everything else in it.
        "spring.datasource.username=replaced-by-service-connection",
        "spring.datasource.password=replaced-by-service-connection",
        // Same reasoning for the Gmail credentials: they have no default so that a real
        // deployment fails fast, which means the context needs values here to start.
        // Nothing in these tests calls Google, so any value does.
        "gmail.client-id=test-client-id",
        "gmail.client-secret=test-client-secret",
        "gmail.refresh-token=test-refresh-token",
        // Without this, booting a context here would start the daily scan and
        // reach for a real mailbox with fake credentials.
        "jobtracker.run-on-startup=false",
        "google.sheets.spreadsheet-id=test-spreadsheet-id"
})
public abstract class AbstractPostgresIntegrationTest {

    /** Must match the image pinned in docker-compose.yml, so tests and local development
        use the same version. */
    private static final DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse("postgres:17.11-alpine");

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE);

    static {
        POSTGRES.start();
    }

    /**
     * A throwaway service account key, generated fresh in this JVM.
     *
     * <p>Building the Sheets client parses the key, so a made-up string will not do. The
     * obvious alternative — committing a fake key — is worse than it looks: a private key
     * in the repository trips secret scanning, and teaches whoever reads it that keys in
     * source control are sometimes acceptable. Generating one here costs about a tenth of
     * a second and leaves nothing behind.
     *
     * <p>It grants nothing. There is no account on the other side of it.
     */
    @DynamicPropertySource
    static void throwawayServiceAccountKey(DynamicPropertyRegistry registry) {
        registry.add("google.sheets.credentials",
                AbstractPostgresIntegrationTest::generateServiceAccountKey);
    }

    private static String generateServiceAccountKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            String pem = "-----BEGIN PRIVATE KEY-----\\n"
                    + Base64.getEncoder().encodeToString(generator.generateKeyPair()
                            .getPrivate().getEncoded())
                    + "\\n-----END PRIVATE KEY-----\\n";

            String json = """
                    {"type":"service_account","project_id":"test","private_key_id":"test",\
                    "private_key":"%s",\
                    "client_email":"test@test.iam.gserviceaccount.com","client_id":"1",\
                    "token_uri":"https://oauth2.googleapis.com/token"}"""
                    .formatted(pem);

            return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA is required by every JVM", e);
        }
    }
}

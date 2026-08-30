package com.nathanpaiva.jobtracker;

import org.springframework.boot.test.context.SpringBootTest;
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
        "anthropic.api-key=test-api-key"
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
}

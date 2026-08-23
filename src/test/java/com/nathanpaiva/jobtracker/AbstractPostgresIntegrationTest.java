package com.nathanpaiva.jobtracker;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for tests that need a real database.
 *
 * <p>The container is a singleton: it is started once in a static initializer and
 * deliberately never stopped by a test-class lifecycle. Testcontainers' own JUnit
 * extension ({@code @Testcontainers} + {@code @Container}) would stop it after each
 * test class and start a fresh one for the next, which changes the randomly mapped
 * port while Spring's cached application context still points at the old one. Letting
 * the JVM own the container avoids that: Testcontainers' reaper removes it on exit.
 *
 * <p>{@code @ServiceConnection} feeds the container's real URL, username and password
 * into the application's DataSource, so tests exercise the same configuration the
 * application uses in production rather than a test-only substitute.
 */
@SpringBootTest(properties = {
        // The production configuration deliberately gives the datasource credentials no
        // default, so a misconfigured deployment fails at startup instead of connecting
        // as some hardcoded user. Tests therefore have to supply *something* for the
        // placeholders to bind against; @ServiceConnection then replaces all of it with
        // the container's real values before any connection is opened. Overriding these
        // two keys — rather than shipping a test application.yml, which would shadow the
        // real one entirely — keeps that file as the single source of truth.
        "spring.datasource.username=replaced-by-service-connection",
        "spring.datasource.password=replaced-by-service-connection"
})
abstract class AbstractPostgresIntegrationTest {

    /** Must match the image pinned in docker-compose.yml, so tests and local dev agree. */
    private static final DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse("postgres:17.11-alpine");

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE);

    static {
        POSTGRES.start();
    }
}

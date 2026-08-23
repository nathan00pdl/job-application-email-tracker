package com.nathanpaiva.jobtracker;

import org.junit.jupiter.api.Test;

/**
 * Smoke test: the Spring context starts. Now that Flyway runs on startup, that also
 * means every migration applied cleanly against a real PostgreSQL server.
 */
class JobApplicationEmailTrackerApplicationTests extends AbstractPostgresIntegrationTest {

    @Test
    void contextLoads() {
    }

}

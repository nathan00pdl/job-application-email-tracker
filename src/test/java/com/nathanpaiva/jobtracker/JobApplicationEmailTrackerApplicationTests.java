package com.nathanpaiva.jobtracker;

import org.junit.jupiter.api.Test;

/**
 * Smoke test: the Spring context starts. Now that the application owns a DataSource
 * and runs Flyway on startup, "the context loads" also means the migrations applied
 * cleanly against a real PostgreSQL.
 */
class JobApplicationEmailTrackerApplicationTests extends AbstractPostgresIntegrationTest {

    @Test
    void contextLoads() {
    }

}

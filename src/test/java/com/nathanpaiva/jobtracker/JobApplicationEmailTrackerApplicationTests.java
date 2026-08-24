package com.nathanpaiva.jobtracker;

import org.junit.jupiter.api.Test;

/**
 * Smoke test: the Spring context starts. Now that the application owns a DataSource,
 * that also means it successfully connected to a real PostgreSQL server.
 */
class JobApplicationEmailTrackerApplicationTests extends AbstractPostgresIntegrationTest {

    @Test
    void contextLoads() {
    }

}

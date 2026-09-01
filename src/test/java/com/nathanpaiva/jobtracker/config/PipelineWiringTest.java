package com.nathanpaiva.jobtracker.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.nathanpaiva.jobtracker.AbstractPostgresIntegrationTest;
import com.nathanpaiva.jobtracker.application.RunDailyScanUseCase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The pipeline is assembled: the use case exists, and Spring found the adapters to give
 * it. Nothing here runs a scan — the runner is off in tests.
 *
 * <p>Its value is catching the wiring mistakes that unit tests cannot see: a port with
 * no implementation, or two, or a bean that was never declared. Those only show up when
 * the whole context is put together.
 */
class PipelineWiringTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private RunDailyScanUseCase dailyScan;

    @Test
    void assemblesTheDailyScan() {
        assertThat(dailyScan).isNotNull();
    }
}

package com.nathanpaiva.jobtracker.adapters.runner;

import org.junit.jupiter.api.Test;

import com.nathanpaiva.jobtracker.application.RunDailyScanUseCase;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * The runner has one job: start the run.
 *
 * <p>This is the first test in the project to use Mockito, and the reason is worth
 * naming. Everywhere else the tests check a <em>result</em> — what was stored, what was
 * returned — and a fake built by hand is the clearer tool for that. Here there is no
 * result: the whole behaviour is "it calls the use case". Checking an interaction is
 * exactly what a mock is for, and writing a fake to record one boolean would be more
 * code saying less.
 */
class DailyScanRunnerTest {

    @Test
    void startsTheDailyScan() {
        RunDailyScanUseCase dailyScan = mock(RunDailyScanUseCase.class);

        new DailyScanRunner(dailyScan).run(null);

        verify(dailyScan).run();
    }
}

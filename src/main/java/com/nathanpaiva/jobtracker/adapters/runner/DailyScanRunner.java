package com.nathanpaiva.jobtracker.adapters.runner;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.nathanpaiva.jobtracker.application.RunDailyScanUseCase;

/**
 * Starts the daily run when the application starts.
 *
 * <p>This is the only adapter that drives the application rather than being driven by
 * it. Everything else exists because the use case asked for something; this one exists
 * because something outside — a person, or a cron in GitHub Actions — asked for the run.
 *
 * <p>There is no web server here, so without this class the application would start,
 * apply its migrations and exit having done nothing. Once
 * {@link ApplicationRunner#run(ApplicationArguments)} returns, the application exits on
 * its own, which is the right shape for a job.
 *
 * <p>Failures are not caught. If the mailbox or the database cannot be reached, the
 * exception ends the run and the process exits with a non-zero status, which is what
 * makes a failed nightly job show up as failed instead of quietly reporting an empty
 * day.
 */
@Component
@ConditionalOnProperty(name = "jobtracker.run-on-startup", havingValue = "true",
        matchIfMissing = true)
class DailyScanRunner implements ApplicationRunner {

    private final RunDailyScanUseCase dailyScan;

    DailyScanRunner(RunDailyScanUseCase dailyScan) {
        this.dailyScan = dailyScan;
    }

    @Override
    public void run(ApplicationArguments args) {
        dailyScan.run();
    }
}

package com.nathanpaiva.jobtracker.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.nathanpaiva.jobtracker.application.RunDailyScanUseCase;
import com.nathanpaiva.jobtracker.domain.EmailClassifier;
import com.nathanpaiva.jobtracker.ports.EmailSourcePort;
import com.nathanpaiva.jobtracker.ports.PersistencePort;

/**
 * Builds the objects that make up the pipeline.
 *
 * <p>This class exists because the classes it creates carry no Spring annotations, and
 * that is deliberate: {@code domain} and {@code application} must not depend on a
 * framework. Something still has to construct them, so the wiring lives out here, where
 * the framework is allowed.
 *
 * <p>The adapters do not appear below. They are annotated with {@code @Component} and
 * found on their own, and Spring passes them in wherever a port is asked for — which is
 * how {@link RunDailyScanUseCase} ends up talking to Gmail and PostgreSQL without ever
 * naming either.
 */
@Configuration
class PipelineConfiguration {

    /**
     * The clock the use case reads to work out its window.
     *
     * <p>A bean rather than a call to {@code Instant.now()} inside the use case: that is
     * what lets a test freeze time and check the exact window that was asked for.
     */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    EmailClassifier emailClassifier() {
        return new EmailClassifier();
    }

    @Bean
    RunDailyScanUseCase runDailyScanUseCase(EmailSourcePort emailSource,
                                            EmailClassifier classifier,
                                            PersistencePort persistence,
                                            Clock clock) {
        return new RunDailyScanUseCase(emailSource, classifier, persistence, clock);
    }
}

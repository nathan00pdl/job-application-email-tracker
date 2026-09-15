package com.nathanpaiva.jobtracker.adapters.whatsapp;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the HTTP client the WhatsApp adapter sends with.
 *
 * <p>The client is Java's own ({@link HttpClient}, part of the JDK). Meta publishes no
 * Java SDK for the Cloud API, and sending a template is a single POST: a library would
 * bring more code than the call it wraps.
 */
@Configuration
@EnableConfigurationProperties(WhatsAppProperties.class)
class WhatsAppConfiguration {

    /**
     * How long to wait for Meta to accept a connection. Without a limit, a network that
     * swallows packets holds the run until the step's timeout kills it, and the failure
     * then names the timeout instead of the cause.
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    @Bean
    HttpClient whatsAppHttpClient() {
        return HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    }
}

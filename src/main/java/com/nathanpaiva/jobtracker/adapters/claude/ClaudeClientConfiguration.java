package com.nathanpaiva.jobtracker.adapters.claude;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;

/**
 * Builds the Anthropic client the adapter uses.
 *
 * <p>The key is read from configuration rather than from the environment by the SDK
 * itself, so it follows the same path as every other credential in this project and
 * fails at startup when it is missing.
 *
 * <p>Nothing here is created unless {@code classifier.provider} is {@code claude}. That
 * matters more than it looks: because this class is skipped, {@link ClaudeProperties} is
 * never registered, so {@code ${ANTHROPIC_API_KEY}} is never resolved and the
 * application starts fine without one. The fail-fast behaviour stays exactly where it is
 * wanted — for someone who did ask for this classifier and forgot the key.
 */
@Configuration
@ConditionalOnProperty(name = "classifier.provider", havingValue = "claude")
@EnableConfigurationProperties(ClaudeProperties.class)
class ClaudeClientConfiguration {

    @Bean
    AnthropicClient anthropicClient(ClaudeProperties properties) {
        return AnthropicOkHttpClient.builder()
                .apiKey(properties.apiKey())
                .build();
    }
}

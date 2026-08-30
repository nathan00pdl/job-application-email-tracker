package com.nathanpaiva.jobtracker.adapters.claude;

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
 */
@Configuration
@EnableConfigurationProperties(ClaudeProperties.class)
class ClaudeClientConfiguration {

    @Bean
    AnthropicClient anthropicClient(ClaudeProperties properties) {
        return AnthropicOkHttpClient.builder()
                .apiKey(properties.apiKey())
                .build();
    }
}

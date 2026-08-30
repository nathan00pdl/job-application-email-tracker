package com.nathanpaiva.jobtracker.adapters.claude;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * What is needed to reach the Anthropic API.
 *
 * <p>The key has no default, like every other credential here: a deployment missing it
 * fails at startup rather than on the first email of the day.
 *
 * <p>The model does have one. It is not a secret, and having it in configuration means
 * trying a cheaper or newer model is an environment variable, not a code change.
 *
 * @param apiKey the Anthropic API key
 * @param model  the model id, for example {@code claude-opus-5}
 */
@ConfigurationProperties(prefix = "anthropic")
record ClaudeProperties(String apiKey, String model) {
}

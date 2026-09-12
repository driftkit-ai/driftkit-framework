package ai.driftkit.clients.claude.client;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Claude 5 (and later) rejects the {@code temperature} parameter with
 * {@code 400 invalid_request_error: "temperature is deprecated for this model"}, so the client
 * must omit it for those models and keep sending it for Claude 3.x / 4.x.
 */
class ClaudeTemperatureSupportTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "claude-sonnet-5",
            "claude-sonnet-5-20260401",
            "claude-opus-5",
            "claude-haiku-5-1",
            "claude-fable-5-1",
            "claude-opus-12-20301231"
    })
    void claude5AndLaterDoNotAcceptTemperature(String model) {
        assertFalse(ClaudeModelClient.supportsTemperature(model), model);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "claude-sonnet-4-20250514",
            "claude-sonnet-4-5-20250929",
            "claude-opus-4-1-20250805",
            "claude-opus-4-8",
            "claude-3-5-haiku-20241022",
            "claude-3-opus-20240229",
            "claude-haiku-4-5"
    })
    void olderGenerationsStillAcceptTemperature(String model) {
        assertTrue(ClaudeModelClient.supportsTemperature(model), model);
    }

    @org.junit.jupiter.api.Test
    void nullModelKeepsTheDefaultBehaviour() {
        assertTrue(ClaudeModelClient.supportsTemperature(null));
    }
}

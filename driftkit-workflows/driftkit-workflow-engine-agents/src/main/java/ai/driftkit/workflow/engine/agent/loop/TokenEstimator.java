package ai.driftkit.workflow.engine.agent.loop;

import ai.driftkit.common.domain.client.ModelMessage;

import java.util.List;

/**
 * Pessimistic token estimation (Claude Code pattern: overestimate so compaction
 * fires earlier than a hard provider error). Standard heuristics assume ~4 chars
 * per token; we use 3 chars per token, i.e. a 4/3 safety multiplier.
 */
public final class TokenEstimator {

    private static final int CHARS_PER_TOKEN_PESSIMISTIC = 3;

    private TokenEstimator() {
    }

    public static long estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (text.length() + CHARS_PER_TOKEN_PESSIMISTIC - 1) / CHARS_PER_TOKEN_PESSIMISTIC;
    }

    public static long estimateTokens(ModelMessage message) {
        if (message == null) {
            return 0;
        }
        long tokens = estimateTokens(message.getContent());
        if (message.getToolCalls() != null) {
            // Rough per-call overhead: name + serialized arguments
            for (var call : message.getToolCalls()) {
                if (call.getFunction() != null) {
                    tokens += estimateTokens(call.getFunction().getName());
                    if (call.getFunction().getArguments() != null) {
                        tokens += estimateTokens(call.getFunction().getArguments().toString());
                    }
                }
            }
        }
        return tokens + 4; // message framing overhead
    }

    public static long estimateTokens(List<ModelMessage> messages) {
        if (messages == null) {
            return 0;
        }
        long total = 0;
        for (ModelMessage message : messages) {
            total += estimateTokens(message);
        }
        return total;
    }
}

package ai.driftkit.workflow.engine.agent.loop;

import ai.driftkit.common.domain.client.ModelTextResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Aggregated token usage across all turns of an agentic loop run
 * (and, when sub-agents report into the same accumulator, across the tree).
 * Jackson-friendly: part of the serializable {@link LoopState}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenUsageAccumulator {

    private long promptTokens;
    private long completionTokens;
    private long totalTokens;
    private long reasoningTokens;
    private long cacheHitTokens;
    private int modelCalls;

    public void add(ModelTextResponse response) {
        if (response == null || response.getUsage() == null) {
            return;
        }
        ModelTextResponse.Usage usage = response.getUsage();
        modelCalls++;
        if (usage.getPromptTokens() != null) {
            promptTokens += usage.getPromptTokens();
        }
        if (usage.getCompletionTokens() != null) {
            completionTokens += usage.getCompletionTokens();
        }
        if (usage.getTotalTokens() != null) {
            totalTokens += usage.getTotalTokens();
        } else if (usage.getPromptTokens() != null || usage.getCompletionTokens() != null) {
            totalTokens = promptTokens + completionTokens;
        }
        if (usage.getReasoningTokens() != null) {
            reasoningTokens += usage.getReasoningTokens();
        }
        if (usage.getCacheUsage() != null && usage.getCacheUsage().getCacheHitTokens() != null) {
            cacheHitTokens += usage.getCacheUsage().getCacheHitTokens();
        }
    }

    public void addAll(TokenUsageAccumulator other) {
        if (other == null) {
            return;
        }
        promptTokens += other.promptTokens;
        completionTokens += other.completionTokens;
        totalTokens += other.totalTokens;
        reasoningTokens += other.reasoningTokens;
        cacheHitTokens += other.cacheHitTokens;
        modelCalls += other.modelCalls;
    }
}

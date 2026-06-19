package ai.driftkit.workflow.engine.agent.loop;

import ai.driftkit.common.domain.client.ModelMessage;
import ai.driftkit.common.domain.client.Role;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Mutable, fully Jackson-serializable state of an agentic loop run.
 *
 * <p>Serializability is a day-one invariant: a suspended loop (HITL) must survive
 * a round-trip through the workflow engine's persistence — see plan section D7.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LoopState {

    /** Full conversation of this run, including assistant tool-call turns and tool results. */
    @Builder.Default
    private List<ModelMessage> messages = new ArrayList<>();

    /** Turns already consumed (model calls that produced an assistant message). */
    @Builder.Default
    private int turnCount = 0;

    /** Consecutive model-call failures; reset on success. Drives the circuit breaker. */
    @Builder.Default
    private int consecutiveFailures = 0;

    /** Whether the single reactive-compaction attempt of this run was used. */
    @Builder.Default
    private boolean attemptedReactiveCompact = false;

    /** Aggregated token usage across all turns. */
    @Builder.Default
    private TokenUsageAccumulator usage = new TokenUsageAccumulator();

    /** Non-null only while suspended: tool calls awaiting an {@link ApprovalDecision}. */
    private List<PendingToolCall> pendingToolCalls;

    /** Reason of the first ASK decision that caused the suspension. */
    private String approvalReason;

    public boolean isSuspended() {
        return pendingToolCalls != null && !pendingToolCalls.isEmpty();
    }

    public void addMessage(ModelMessage message) {
        messages.add(message);
    }

    /** Last assistant text content, or null if none yet. */
    public String lastAssistantText() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ModelMessage m = messages.get(i);
            if (m.getRole() == Role.assistant
                    && m.getContent() != null && !m.getContent().isEmpty()) {
                return m.getContent();
            }
        }
        return null;
    }
}

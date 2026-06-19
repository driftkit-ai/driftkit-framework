package ai.driftkit.workflow.engine.agent.loop;

import ai.driftkit.common.tools.ToolCall;
import ai.driftkit.workflow.engine.agent.ToolExecutionResult;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Outcome of an agentic loop run. Always returned — never thrown — so callers
 * receive the accumulated text, tool results and usage together with the
 * {@link StopReason}, including for partial completions.
 */
@Value
@Builder
public class AgentLoopResult {

    /** Why the loop stopped. */
    StopReason stopReason;

    /** Final (or last) assistant text; may be null for tool-only partial runs. */
    String text;

    /** Full serializable state — required to resume a {@link StopReason#PENDING_APPROVAL} run. */
    LoopState state;

    /** All tool executions of this run (in execution order), including denied ones. */
    List<ToolExecutionResult> toolResults;

    /** Turns consumed. */
    int turns;

    /** Error detail for CIRCUIT_BREAKER / CONTEXT_OVERFLOW / ERROR stops. */
    String error;

    public TokenUsageAccumulator getUsage() {
        return state != null ? state.getUsage() : new TokenUsageAccumulator();
    }

    public boolean isSuspended() {
        return stopReason == StopReason.PENDING_APPROVAL;
    }

    /** Tool calls awaiting approval when suspended; empty otherwise. */
    public List<ToolCall> getPendingApprovals() {
        if (state == null || state.getPendingToolCalls() == null) {
            return List.of();
        }
        return state.getPendingToolCalls().stream()
                .filter(p -> p.getBehavior() == HookDecision.Behavior.ASK)
                .map(PendingToolCall::getCall)
                .toList();
    }

    /** Reason supplied by the hook that requested approval. */
    public String getApprovalReason() {
        return state != null ? state.getApprovalReason() : null;
    }

    public boolean isCompleted() {
        return stopReason == StopReason.END_TURN;
    }
}

package ai.driftkit.workflow.engine.agent.loop;

import ai.driftkit.common.utils.JsonUtils;
import ai.driftkit.workflow.engine.core.StepResult;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * Bridge between a suspended agentic loop and the workflow engine's HITL
 * mechanism (plan, D7): a {@link StopReason#PENDING_APPROVAL} result becomes a
 * {@link StepResult.Suspend} whose metadata carries the serialized
 * {@link LoopState}; on workflow resume the state is restored and fed back to
 * {@code LLMAgent.resumeAgentic} together with the human {@link ApprovalDecision}.
 */
public final class AgentLoopSuspensions {

    /** Metadata key under which the serialized loop state travels through the workflow engine. */
    public static final String METADATA_LOOP_STATE = "agentLoopState";

    private AgentLoopSuspensions() {
    }

    /**
     * Human-readable approval request shown to the operator while the loop is suspended.
     */
    public record ApprovalRequest(String reason, List<PendingCallView> pendingCalls) {

        public record PendingCallView(String toolName, Map<String, JsonNode> arguments) {
        }
    }

    /**
     * Convert a suspended loop result into a workflow suspension. The next input
     * expected on resume is an {@link ApprovalDecision}.
     *
     * @throws IllegalArgumentException if the result is not suspended
     */
    public static StepResult.Suspend<ApprovalRequest> toSuspend(AgentLoopResult result) {
        if (!result.isSuspended()) {
            throw new IllegalArgumentException("Loop result is not suspended: " + result.getStopReason());
        }

        List<ApprovalRequest.PendingCallView> views = result.getPendingApprovals().stream()
                .map(call -> new ApprovalRequest.PendingCallView(
                        call.getFunction() != null ? call.getFunction().getName() : "<unknown>",
                        call.getFunction() != null ? call.getFunction().getArguments() : Map.of()))
                .toList();
        ApprovalRequest request = new ApprovalRequest(result.getApprovalReason(), views);

        String serializedState;
        try {
            serializedState = JsonUtils.toJson(result.getState());
        } catch (Exception e) {
            throw new IllegalStateException("LoopState must stay Jackson-serializable (plan, section 7)", e);
        }

        return StepResult.suspend(request, ApprovalDecision.class, Map.of(METADATA_LOOP_STATE, serializedState));
    }

    /**
     * Restore the loop state from suspension metadata on workflow resume.
     */
    public static LoopState restoreState(Map<String, Object> metadata) {
        Object serialized = metadata != null ? metadata.get(METADATA_LOOP_STATE) : null;
        if (!(serialized instanceof String json) || json.isBlank()) {
            throw new IllegalArgumentException("No serialized loop state under key '" + METADATA_LOOP_STATE + "'");
        }
        try {
            return JsonUtils.fromJson(json, LoopState.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize loop state", e);
        }
    }
}

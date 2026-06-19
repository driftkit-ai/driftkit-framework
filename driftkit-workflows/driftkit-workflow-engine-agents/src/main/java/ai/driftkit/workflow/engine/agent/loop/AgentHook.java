package ai.driftkit.workflow.engine.agent.loop;

import ai.driftkit.common.domain.client.ModelTextRequest;
import ai.driftkit.common.tools.ToolCall;
import ai.driftkit.workflow.engine.agent.ToolExecutionResult;

/**
 * Extension point of the agentic loop (Claude Code hook pattern, minimal subset).
 *
 * <p>All methods have no-op defaults — implement only what you need. Hooks run in
 * registration order; for {@link #preToolUse} the first non-ALLOW decision wins,
 * and argument rewrites of consecutive ALLOW decisions are chained.</p>
 *
 * <p>Contract: a DENY decision is converted into a tool result visible to the model
 * (with the reason), never into an exception — the model must be able to adapt.</p>
 *
 * <p>Threading: all hook methods are invoked sequentially from the loop's thread,
 * never concurrently. Hooks must NOT mutate {@code state.getMessages()} — message
 * assembly is owned by the loop; use {@link HookDecision#updatedArguments()} to
 * influence tool input and {@link #postToolUse} for observation only.</p>
 */
public interface AgentHook {

    /** Called before each tool execution. */
    default HookDecision preToolUse(ToolCall call, LoopState state) {
        return HookDecision.allow();
    }

    /** Called after each tool execution (including failed ones). */
    default void postToolUse(ToolCall call, ToolExecutionResult result, LoopState state) {
    }

    /** Called before each model call with the fully built request. */
    default void preModelCall(ModelTextRequest request, LoopState state) {
    }

    /** Called once when the loop finishes (any {@link StopReason}). */
    default void onLoopFinish(AgentLoopResult result) {
    }
}

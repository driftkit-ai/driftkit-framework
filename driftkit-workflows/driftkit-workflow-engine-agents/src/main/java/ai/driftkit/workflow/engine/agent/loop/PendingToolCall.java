package ai.driftkit.workflow.engine.agent.loop;

import ai.driftkit.common.tools.ToolCall;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A tool call captured at suspension time together with the hook decision it
 * received. Part of the serializable {@link LoopState} so a suspended loop can
 * be resumed in another process.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PendingToolCall {

    private ToolCall call;
    private HookDecision.Behavior behavior;
    private String reason;
}

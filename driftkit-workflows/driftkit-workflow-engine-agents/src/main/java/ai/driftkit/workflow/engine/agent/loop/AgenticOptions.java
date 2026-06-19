package ai.driftkit.workflow.engine.agent.loop;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Per-call configuration of {@code LLMAgent.executeAgentic}.
 */
@Value
@Builder
public class AgenticOptions {

    /** Loop limits and recovery; defaults to {@link LoopPolicy#defaults()}. */
    LoopPolicy policy;

    /** Hooks applied to every tool call and model call of the loop. */
    List<AgentHook> hooks;

    /** Context window management; defaults to {@link ContextManager#NOOP}. */
    ContextManager contextManager;

    public static AgenticOptions defaults() {
        return AgenticOptions.builder().build();
    }

    public LoopPolicy effectivePolicy() {
        return policy != null ? policy : LoopPolicy.defaults();
    }

    public List<AgentHook> effectiveHooks() {
        return hooks != null ? hooks : List.of();
    }

    public ContextManager effectiveContextManager() {
        return contextManager != null ? contextManager : ContextManager.NOOP;
    }
}

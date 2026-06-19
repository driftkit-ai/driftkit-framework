package ai.driftkit.workflow.engine.agent.loop;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Decision of an {@link AgentHook} about a pending tool call.
 *
 * <ul>
 *   <li>{@code ALLOW} — execute, optionally with rewritten arguments;</li>
 *   <li>{@code DENY} — do not execute; the refusal (with reason) is fed back to the
 *       model as the tool result so it can adapt. A denial never aborts the loop;</li>
 *   <li>{@code ASK} — suspend the loop for human approval (HITL); the run returns
 *       {@link StopReason#PENDING_APPROVAL} with a resumable state snapshot.</li>
 * </ul>
 */
public record HookDecision(Behavior behavior,
                           String reason,
                           Map<String, JsonNode> updatedArguments) {

    public enum Behavior { ALLOW, DENY, ASK }

    public static HookDecision allow() {
        return new HookDecision(Behavior.ALLOW, null, null);
    }

    /** Allow with rewritten tool arguments (masking, normalization, safety flags). */
    public static HookDecision allow(Map<String, JsonNode> updatedArguments) {
        return new HookDecision(Behavior.ALLOW, null, updatedArguments);
    }

    public static HookDecision deny(String reason) {
        return new HookDecision(Behavior.DENY, reason, null);
    }

    public static HookDecision ask(String reason) {
        return new HookDecision(Behavior.ASK, reason, null);
    }

    public boolean isAllow() {
        return behavior == Behavior.ALLOW;
    }

    public boolean isDeny() {
        return behavior == Behavior.DENY;
    }

    public boolean isAsk() {
        return behavior == Behavior.ASK;
    }
}

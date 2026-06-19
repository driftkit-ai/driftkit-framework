package ai.driftkit.workflow.engine.agent.loop;

/**
 * Human decision applied to a suspended loop on resume. Affects only the calls
 * that were suspended with {@link HookDecision.Behavior#ASK}; calls that were
 * already allowed or denied keep their original decision.
 */
public record ApprovalDecision(boolean approved, String reason) {

    public static ApprovalDecision approve() {
        return new ApprovalDecision(true, null);
    }

    public static ApprovalDecision reject(String reason) {
        return new ApprovalDecision(false, reason);
    }
}

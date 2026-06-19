package ai.driftkit.workflow.engine.agent.loop;

/**
 * Why the agentic loop stopped. Returned with the result instead of throwing,
 * so callers always receive the accumulated state and usage.
 */
public enum StopReason {
    /** Model finished without requesting tools — normal completion. */
    END_TURN,
    /** Turn limit reached; partial result returned. */
    MAX_TURNS,
    /** Token budget exhausted; partial result returned. */
    BUDGET,
    /** Wall-clock deadline exceeded; partial result returned. */
    DEADLINE,
    /** Too many consecutive model-call failures; partial result returned. */
    CIRCUIT_BREAKER,
    /** Context exceeded the window and compaction could not recover. */
    CONTEXT_OVERFLOW,
    /** A hook requested human approval; loop is suspended and resumable. */
    PENDING_APPROVAL,
    /** Non-recoverable programming/configuration error. */
    ERROR
}

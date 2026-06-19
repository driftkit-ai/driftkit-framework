package ai.driftkit.workflow.engine.agent.loop;

/**
 * Manages the context window of an agentic loop. Called before every model turn
 * and reactively on context-overflow errors.
 *
 * <p>Invariants any implementation must keep (plan, section 7):
 * never drop the system message, never drop the most recent messages, and never
 * break an assistant tool-call / tool-result pair across a compaction boundary.</p>
 */
public interface ContextManager {

    /** No-op manager: the default when compaction is not configured. */
    ContextManager NOOP = new ContextManager() {
        @Override
        public void manageBeforeTurn(LoopState state) {
        }

        @Override
        public boolean reactiveCompact(LoopState state) {
            return false;
        }
    };

    /**
     * Inspect (and possibly compact) the conversation before the next model call.
     */
    void manageBeforeTurn(LoopState state);

    /**
     * Last-resort compaction after a provider context-overflow error.
     *
     * @return true if the state changed and the request is worth retrying
     */
    boolean reactiveCompact(LoopState state);
}

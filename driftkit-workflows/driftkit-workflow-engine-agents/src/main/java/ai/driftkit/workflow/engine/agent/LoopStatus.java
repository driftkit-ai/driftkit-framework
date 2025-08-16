package ai.driftkit.workflow.engine.agent;

/**
 * Status enumeration for loop execution results.
 */
public enum LoopStatus {
    
    /**
     * Loop should continue with the current result.
     */
    CONTINUE,
    
    /**
     * Loop should stop - condition has been met.
     */
    COMPLETE,
    
    /**
     * Loop should continue but with revision based on feedback.
     */
    REVISE,
    
    /**
     * Loop should retry the current iteration.
     */
    RETRY,
    
    /**
     * Loop failed due to an error.
     */
    FAILED
}
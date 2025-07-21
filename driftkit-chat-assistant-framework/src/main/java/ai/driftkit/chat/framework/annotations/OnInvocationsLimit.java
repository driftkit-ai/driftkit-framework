package ai.driftkit.chat.framework.annotations;

/**
 * Enum defining actions to take when a step's invocation limit is reached
 */
public enum OnInvocationsLimit {
    /**
     * Throw an error when the limit is reached
     */
    ERROR,
    
    /**
     * Stop the workflow and return the current result
     */
    STOP,
    
    /**
     * Continue to the next step
     */
    CONTINUE
}
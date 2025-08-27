package ai.driftkit.workflow.test.assertions.strategy;

import ai.driftkit.workflow.test.core.ExecutionTracker;

/**
 * Strategy for verifying execution behavior.
 */
public abstract class AssertionStrategy {
    
    protected final String description;
    
    protected AssertionStrategy(String description) {
        this.description = description;
    }
    
    /**
     * Verifies the execution history against expected behavior.
     * 
     * @param history the execution history
     * @param expectedBehavior the expected behavior
     * @throws AssertionError if verification fails
     */
    public abstract void verify(ExecutionTracker.ExecutionHistory history, ExpectedBehavior expectedBehavior);
    
    /**
     * Gets the strategy description.
     * 
     * @return description
     */
    public String getDescription() {
        return description;
    }
}
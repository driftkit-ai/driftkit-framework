package ai.driftkit.workflow.engine.core;

import ai.driftkit.workflow.engine.graph.WorkflowGraph;

/**
 * Interface for routing logic between workflow steps.
 * Determines the next step to execute based on current state and step outputs.
 */
public interface StepRouter {
    
    /**
     * Finds the next step to execute after the current step.
     * 
     * @param graph The workflow graph
     * @param currentStepId The ID of the current step
     * @param data The output data from the current step
     * @return The ID of the next step to execute, or null if no suitable step found
     */
    String findNextStep(WorkflowGraph<?, ?> graph, String currentStepId, Object data);
    
    /**
     * Finds the target step for a branch based on the event type.
     * 
     * @param graph The workflow graph  
     * @param currentStepId The ID of the current step
     * @param event The branch event that determines the path
     * @return The ID of the target step for the branch, or null if no match found
     */
    String findBranchTarget(WorkflowGraph<?, ?> graph, String currentStepId, Object event);
    
    /**
     * Finds a step that can accept the given input type.
     * Used for type-based routing when no explicit edge exists.
     * 
     * @param graph The workflow graph
     * @param inputType The type of input to match
     * @param excludeStepId Optional step ID to exclude from search
     * @return The ID of a step that can accept the input type, or null if none found
     */
    String findStepForInputType(WorkflowGraph<?, ?> graph, Class<?> inputType, String excludeStepId);
}
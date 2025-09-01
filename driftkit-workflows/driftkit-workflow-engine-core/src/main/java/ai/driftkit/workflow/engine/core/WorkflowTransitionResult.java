package ai.driftkit.workflow.engine.core;

import java.util.Map;

/**
 * Special result type for transitioning from one workflow to another.
 * This allows workflows to trigger other workflows, similar to WorkflowTransitionEvent
 * in the old chat-framework.
 * 
 * Usage example:
 * <pre>
 * return StepResult.finish(new WorkflowTransitionResult(
 *     "target-workflow-id",
 *     Map.of("key", "value")
 * ));
 * </pre>
 * 
 * The workflow engine or controller should check if the finish result is of this type
 * and handle the transition accordingly.
 */
public record WorkflowTransitionResult(
    String targetWorkflowId,
    Map<String, Object> transitionData
) {
    public WorkflowTransitionResult {
        if (targetWorkflowId == null || targetWorkflowId.isBlank()) {
            throw new IllegalArgumentException("targetWorkflowId cannot be null or blank");
        }
        if (transitionData == null) {
            transitionData = Map.of();
        }
    }
    
    /**
     * Creates a transition result with empty transition data.
     */
    public WorkflowTransitionResult(String targetWorkflowId) {
        this(targetWorkflowId, Map.of());
    }
}
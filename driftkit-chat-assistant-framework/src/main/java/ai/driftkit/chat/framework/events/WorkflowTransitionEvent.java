package ai.driftkit.chat.framework.events;

import ai.driftkit.common.domain.Language;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Special event that signals a transition to another workflow.
 * When this event is returned from a workflow step, the framework will:
 * 1. Save the current session state
 * 2. Switch to the target workflow
 * 3. Initialize the new workflow with the provided context
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class WorkflowTransitionEvent extends StepEvent {
    
    private String sourceWorkflowId;
    private String targetWorkflowId;
    private String targetStepId; // Optional: specific step ID in target workflow
    private String initialMessage;
    private Language language;
    private String transitionReason;
    
    @Builder(builderMethodName = "transitionBuilder")
    public WorkflowTransitionEvent(String sourceWorkflowId, String targetWorkflowId, 
                                  String targetStepId, String initialMessage, 
                                  Map<String, String> contextData, 
                                  Language language, String transitionReason) {
        super();
        this.sourceWorkflowId = sourceWorkflowId;
        this.targetWorkflowId = targetWorkflowId;
        this.targetStepId = targetStepId;
        this.initialMessage = initialMessage;
        this.language = language;
        this.transitionReason = transitionReason;
        
        // Only add the essential marker and context data
        Map<String, String> props = new HashMap<>();
        props.put("workflowTransition", "true");
        if (contextData != null) {
            props.putAll(contextData);
        }
        
        this.setProperties(props);
        this.setCompleted(true);
        this.setPercentComplete(100);
    }
    
    /**
     * Create a workflow transition event
     */
    public static WorkflowTransitionEvent to(String targetWorkflowId) {
        return transitionBuilder()
                .targetWorkflowId(targetWorkflowId)
                .build();
    }
    
    /**
     * Create a workflow transition event with initial message
     */
    public static WorkflowTransitionEvent to(String targetWorkflowId, String initialMessage) {
        return transitionBuilder()
                .targetWorkflowId(targetWorkflowId)
                .initialMessage(initialMessage)
                .build();
    }
    
    /**
     * Create a workflow transition event with context data
     */
    public static WorkflowTransitionEvent to(String sourceWorkflowId, String targetWorkflowId, 
                                           Map<String, String> contextData) {
        return transitionBuilder()
                .sourceWorkflowId(sourceWorkflowId)
                .targetWorkflowId(targetWorkflowId)
                .contextData(contextData)
                .build();
    }
    
    /**
     * Create a workflow transition event with full context
     */
    public static WorkflowTransitionEvent to(String sourceWorkflowId, String targetWorkflowId, 
                                           String initialMessage, Map<String, String> contextData) {
        return transitionBuilder()
                .sourceWorkflowId(sourceWorkflowId)
                .targetWorkflowId(targetWorkflowId)
                .initialMessage(initialMessage)
                .contextData(contextData)
                .build();
    }
}
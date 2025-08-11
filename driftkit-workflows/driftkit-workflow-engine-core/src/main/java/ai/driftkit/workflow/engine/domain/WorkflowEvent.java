package ai.driftkit.workflow.engine.domain;

import ai.driftkit.workflow.engine.schema.AIFunctionSchema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Event class for workflow execution with schema and progress support.
 * Inspired by StepEvent from driftkit-chat-assistant-framework
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowEvent {
    
    @Accessors(chain = true)
    private String nextStepId;
    
    @Accessors(chain = true)
    private List<String> possibleNextStepIds = new ArrayList<>();
    
    @Accessors(chain = true)
    private Map<String, String> properties;
    
    @Accessors(chain = true)
    private AIFunctionSchema currentSchema;
    
    @Accessors(chain = true)
    private AIFunctionSchema nextInputSchema;
    
    @Accessors(chain = true)
    private boolean completed;
    
    @Accessors(chain = true)
    private int percentComplete;
    
    @Accessors(chain = true)
    @Builder.Default
    private boolean required = true;
    
    @Accessors(chain = true)
    private String messageId;
    
    @Accessors(chain = true)
    private String error;
    
    @Accessors(chain = true)
    private boolean async;
    
    @Accessors(chain = true)
    private String asyncTaskId;
    
    // Factory methods for common scenarios
    
    public static WorkflowEvent nextStep(String nextStepId) {
        return WorkflowEvent.builder()
                .nextStepId(nextStepId)
                .completed(true)
                .percentComplete(100)
                .build();
    }
    
    public static WorkflowEvent completed(Map<String, String> properties) {
        return WorkflowEvent.builder()
                .properties(properties)
                .completed(true)
                .percentComplete(100)
                .build();
    }
    
    public static WorkflowEvent withProgress(int percentComplete, String message) {
        Map<String, String> props = new HashMap<>();
        props.put("progressMessage", message);
        return WorkflowEvent.builder()
                .properties(props)
                .completed(false)
                .percentComplete(percentComplete)
                .build();
    }
    
    public static WorkflowEvent withSchema(AIFunctionSchema currentSchema, AIFunctionSchema nextSchema) {
        return WorkflowEvent.builder()
                .currentSchema(currentSchema)
                .nextInputSchema(nextSchema)
                .completed(true)
                .percentComplete(100)
                .build();
    }
    
    public static WorkflowEvent withError(String error) {
        return WorkflowEvent.builder()
                .error(error)
                .completed(true)
                .percentComplete(100)
                .build();
    }
    
    public static WorkflowEvent asyncStarted(String asyncTaskId, String messageId) {
        return WorkflowEvent.builder()
                .async(true)
                .asyncTaskId(asyncTaskId)
                .messageId(messageId)
                .completed(false)
                .percentComplete(0)
                .build();
    }
    
    // Builder enhancement methods
    
    public WorkflowEvent addPossibleNextStep(String stepId) {
        if (possibleNextStepIds == null) {
            possibleNextStepIds = new ArrayList<>();
        }
        if (stepId != null && !possibleNextStepIds.contains(stepId)) {
            possibleNextStepIds.add(stepId);
        }
        return this;
    }
    
    public WorkflowEvent addProperty(String key, String value) {
        if (properties == null) {
            properties = new HashMap<>();
        } else if (!(properties instanceof HashMap)) {
            // Convert immutable map to mutable
            properties = new HashMap<>(properties);
        }
        properties.put(key, value);
        return this;
    }
    
    public WorkflowEvent updateProgress(int percentComplete, String message) {
        this.percentComplete = percentComplete;
        return addProperty("progressMessage", message);
    }
}
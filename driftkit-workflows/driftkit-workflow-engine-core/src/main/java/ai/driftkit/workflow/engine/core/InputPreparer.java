package ai.driftkit.workflow.engine.core;

import ai.driftkit.workflow.engine.graph.StepNode;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Prepares input data for workflow steps.
 * Handles the logic of finding appropriate input based on step requirements,
 * context state, and execution history.
 */
@Slf4j
public class InputPreparer {
    
    /**
     * Prepares input for a step execution.
     * 
     * @param instance The workflow instance
     * @param step The step to prepare input for
     * @return The prepared input, or null if no suitable input found
     */
    public Object prepareStepInput(WorkflowInstance instance, StepNode step) {
        log.debug("Preparing input for step: {} (expected type: {})", 
            step.id(), 
            step.executor().getInputType() != null ? step.executor().getInputType().getSimpleName() : "any");
        
        // For initial step, use trigger data
        if (step.isInitial()) {
            return instance.getContext().getTriggerData();
        }
        
        WorkflowContext ctx = instance.getContext();
        Class<?> expectedInputType = step.executor().getInputType();
        
        // Priority 1: Check if we're resuming from suspension with user input
        if (ctx.hasStepResult(WorkflowContext.Keys.USER_INPUT)) {
            Object userInput = retrieveUserInput(ctx, expectedInputType);
            if (userInput != null && isInputCompatible(step, userInput)) {
                log.debug("Using user input of type {} for step {}", 
                    userInput.getClass().getSimpleName(), step.id());
                clearUserInput(instance);
                return userInput;
            }
        }
        
        // Priority 2: Find the most recent compatible output
        Object recentOutput = findRecentCompatibleOutput(instance, step);
        if (recentOutput != null) {
            return recentOutput;
        }
        
        // Priority 3: If step has specific input type requirement, search all outputs for exact match
        if (expectedInputType != null && expectedInputType != Object.class) {
            Map<String, Object> allResults = ctx.getStepOutputs();
            
            // First pass: look for exact type match
            for (Map.Entry<String, Object> entry : allResults.entrySet()) {
                if (entry.getKey().equals(step.id()) || entry.getValue() == null) {
                    continue;
                }
                
                if (expectedInputType.equals(entry.getValue().getClass())) {
                    log.debug("Found exact type match from step {} for input type {}",
                        entry.getKey(), expectedInputType.getSimpleName());
                    return entry.getValue();
                }
            }
            
            // Second pass: look for compatible type (assignable)
            for (Map.Entry<String, Object> entry : allResults.entrySet()) {
                if (entry.getKey().equals(step.id()) || entry.getValue() == null) {
                    continue;
                }
                
                if (expectedInputType.isAssignableFrom(entry.getValue().getClass())) {
                    log.debug("Found compatible type from step {} for input type {}",
                        entry.getKey(), expectedInputType.getSimpleName());
                    return entry.getValue();
                }
            }
        }
        
        // Priority 4: Check trigger data if it matches the expected type
        Object triggerData = ctx.getTriggerData();
        if (triggerData != null && step.canAcceptInput(triggerData.getClass())) {
            log.debug("Using trigger data of type {} for step {}",
                triggerData.getClass().getSimpleName(), step.id());
            return triggerData;
        }
        
        // No suitable input available
        log.error("No suitable input found for step {} (expected type: {})",
            step.id(), 
            expectedInputType != null ? expectedInputType.getSimpleName() : "any");
        return null;
    }
    
    private Object retrieveUserInput(WorkflowContext ctx, Class<?> expectedInputType) {
        String userInputTypeName = ctx.getStepResultOrDefault(
            WorkflowContext.Keys.USER_INPUT_TYPE, String.class, null);
        
        if (userInputTypeName != null && expectedInputType != null) {
            try {
                Class<?> savedType = Class.forName(userInputTypeName);
                if (expectedInputType.isAssignableFrom(savedType)) {
                    return ctx.getStepResult(WorkflowContext.Keys.USER_INPUT, savedType);
                }
            } catch (ClassNotFoundException e) {
                log.warn("Could not load saved type class: {}", userInputTypeName);
            }
        }
        
        return ctx.getStepResult(WorkflowContext.Keys.USER_INPUT, Object.class);
    }
    
    private void clearUserInput(WorkflowInstance instance) {
        instance.updateContext(WorkflowContext.Keys.USER_INPUT, null);
        instance.updateContext(WorkflowContext.Keys.USER_INPUT_TYPE, null);
    }
    
    private Object findRecentCompatibleOutput(WorkflowInstance instance, StepNode step) {
        var history = instance.getExecutionHistory();
        var ctx = instance.getContext();
        
        for (int i = history.size() - 1; i >= 0; i--) {
            var exec = history.get(i);
            
            if (exec.getStepId().equals(step.id())) {
                continue;
            }
            
            if (!ctx.hasStepResult(exec.getStepId())) {
                continue;
            }
            
            Object result = ctx.getStepResult(exec.getStepId(), Object.class);
            if (result != null && isInputCompatible(step, result)) {
                log.debug("Using output from step {} (type: {}) as input for step {}", 
                    exec.getStepId(), result.getClass().getSimpleName(), step.id());
                return result;
            }
        }
        
        return null;
    }
    
    private boolean isInputCompatible(StepNode step, Object input) {
        return step.canAcceptInput(input.getClass());
    }
}
package ai.driftkit.workflow.engine.core;

import ai.driftkit.workflow.engine.graph.StepNode;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import ai.driftkit.workflow.engine.utils.WorkflowInputOutputHandler;
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
            log.debug("Step {} is initial, using trigger data", step.id());
            return instance.getContext().getTriggerData();
        }
        
        WorkflowContext ctx = instance.getContext();
        Class<?> expectedInputType = step.executor().getInputType();
        
        // Priority 1: Check if we're resuming from suspension with user input
        Object userInput = WorkflowInputOutputHandler.getUserInputForStep(instance, step);
        if (userInput != null) {
            return userInput;
        }
        
        // Priority 2: Find the most recent compatible output from execution history
        log.debug("Looking for recent compatible output. Available outputs: {}", 
            ctx.getStepOutputs().keySet());
        Object recentOutput = WorkflowInputOutputHandler.findCompatibleOutputFromHistory(instance, step);
        if (recentOutput != null) {
            log.debug("Found recent compatible output for step {}: type={}", 
                step.id(), recentOutput.getClass().getSimpleName());
            return recentOutput;
        }
        
        // Priority 3: If step has specific input type requirement, search all outputs for exact match
        if (expectedInputType != null && expectedInputType != Object.class) {
            Map<String, StepOutput> allResults = ctx.getStepOutputs();
            
            // First pass: look for exact type match
            for (Map.Entry<String, StepOutput> entry : allResults.entrySet()) {
                if (entry.getKey().equals(step.id()) || entry.getValue() == null || !entry.getValue().hasValue()) {
                    continue;
                }
                
                if (expectedInputType.equals(entry.getValue().getActualClass())) {
                    log.debug("Found exact type match from step {} for input type {}",
                        entry.getKey(), expectedInputType.getSimpleName());
                    return entry.getValue().getValue();
                }
            }
            
            // Second pass: look for compatible type (assignable)
            for (Map.Entry<String, StepOutput> entry : allResults.entrySet()) {
                if (entry.getKey().equals(step.id()) || entry.getValue() == null || !entry.getValue().hasValue()) {
                    continue;
                }
                
                if (entry.getValue().isCompatibleWith(expectedInputType)) {
                    log.debug("Found compatible type from step {} for input type {}",
                        entry.getKey(), expectedInputType.getSimpleName());
                    return entry.getValue().getValue();
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
}
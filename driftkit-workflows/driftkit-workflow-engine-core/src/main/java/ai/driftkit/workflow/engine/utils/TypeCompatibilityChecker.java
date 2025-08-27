package ai.driftkit.workflow.engine.utils;

import ai.driftkit.workflow.engine.core.StepOutput;
import ai.driftkit.workflow.engine.core.WorkflowContext;
import ai.driftkit.workflow.engine.graph.StepNode;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Utility class for checking type compatibility between workflow steps.
 * Centralizes logic for finding compatible outputs and type matching.
 */
@Slf4j
public final class TypeCompatibilityChecker {
    
    private TypeCompatibilityChecker() {
        // Utility class
    }
    
    /**
     * Finds the most recent compatible output from execution history.
     * Prioritizes exact type matches over compatible types.
     * 
     * @param instance The workflow instance
     * @param targetStep The step expecting input
     * @return Compatible output if found, null otherwise
     */
    public static Object findCompatibleOutputFromHistory(WorkflowInstance instance, StepNode targetStep) {
        WorkflowContext ctx = instance.getContext();
        Class<?> expectedInputType = targetStep.executor().getInputType();
        List<WorkflowInstance.StepExecutionRecord> history = instance.getExecutionHistory();
        
        if (history.isEmpty()) {
            return null;
        }
        
        // First pass: Look for exact type match
        Object exactMatch = findExactTypeMatch(history, ctx, targetStep, expectedInputType);
        if (exactMatch != null) {
            return exactMatch;
        }
        
        // Second pass: Look for compatible type
        return findCompatibleTypeMatch(history, ctx, targetStep);
    }
    
    /**
     * Finds an exact type match from execution history.
     */
    private static Object findExactTypeMatch(List<WorkflowInstance.StepExecutionRecord> history,
                                           WorkflowContext ctx,
                                           StepNode targetStep,
                                           Class<?> expectedInputType) {
        if (expectedInputType == null || expectedInputType == Object.class) {
            return null;
        }
        
        // Traverse history from most recent to oldest
        for (int i = history.size() - 1; i >= 0; i--) {
            WorkflowInstance.StepExecutionRecord exec = history.get(i);
            
            // Skip if it's the target step itself
            if (exec.getStepId().equals(targetStep.id())) {
                continue;
            }
            
            StepOutput output = getStepOutput(ctx, exec.getStepId());
            if (output != null && output.getActualClass().equals(expectedInputType)) {
                Object result = output.getValue();
                log.debug("Found exact type match from step {} (type: {}) for step {}",
                        exec.getStepId(), output.getActualClass().getSimpleName(), targetStep.id());
                return result;
            }
        }
        
        return null;
    }
    
    /**
     * Finds a compatible type match from execution history.
     */
    private static Object findCompatibleTypeMatch(List<WorkflowInstance.StepExecutionRecord> history,
                                                WorkflowContext ctx,
                                                StepNode targetStep) {
        // Traverse history from most recent to oldest
        for (int i = history.size() - 1; i >= 0; i--) {
            WorkflowInstance.StepExecutionRecord exec = history.get(i);
            
            // Skip if it's the target step itself
            if (exec.getStepId().equals(targetStep.id())) {
                continue;
            }
            
            StepOutput output = getStepOutput(ctx, exec.getStepId());
            if (output != null && targetStep.canAcceptInput(output.getActualClass())) {
                Object result = output.getValue();
                log.debug("Found compatible output from step {} (type: {}) for step {}",
                        exec.getStepId(), output.getActualClass().getSimpleName(), targetStep.id());
                return result;
            }
        }
        
        return null;
    }
    
    /**
     * Safely retrieves step output from context.
     */
    private static StepOutput getStepOutput(WorkflowContext ctx, String stepId) {
        if (!ctx.hasStepResult(stepId)) {
            return null;
        }
        
        try {
            StepOutput output = ctx.getStepOutputs().get(stepId);
            if (output == null || !output.hasValue()) {
                return null;
            }
            return output;
        } catch (Exception e) {
            log.error("Error retrieving output from step {}", stepId, e);
            return null;
        }
    }
    
    /**
     * Checks if two types are compatible for workflow step input/output.
     * 
     * @param outputType The type being provided
     * @param inputType The type expected
     * @return true if types are compatible
     */
    public static boolean areTypesCompatible(Class<?> outputType, Class<?> inputType) {
        if (inputType == null || inputType == Object.class) {
            return true; // Can accept any type
        }
        
        if (outputType == null) {
            return false;
        }
        
        // Check if input type can be assigned from output type
        return inputType.isAssignableFrom(outputType);
    }
    
    /**
     * Gets a human-readable type name for logging.
     * 
     * @param type The class type
     * @return Simple name or "any" for null/Object types
     */
    public static String getTypeDisplayName(Class<?> type) {
        if (type == null || type == Object.class) {
            return "any";
        }
        return type.getSimpleName();
    }
}
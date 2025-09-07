package ai.driftkit.workflow.engine.analyzer;

import ai.driftkit.workflow.engine.annotations.OnInvocationsLimit;
import ai.driftkit.workflow.engine.annotations.RetryPolicy;
import lombok.Builder;
import lombok.Data;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;

/**
 * Information about a workflow step extracted from annotations.
 * This is an intermediate representation used during graph construction.
 */
@Data
@Builder
public class StepInfo {
    private final String id;
    private final Method method;
    private final Object instance;
    
    @Builder.Default
    private final boolean isInitial = false;
    
    private final String description;
    
    // Execution order and control
    @Builder.Default
    private final int index = 0;
    
    @Builder.Default
    private final long timeoutMs = -1;
    
    // Type information
    private Class<?> inputType;
    private Type genericInputType;
    private int inputParameterIndex = -1;
    
    // Flow control fields from annotation
    private final Class<?>[] nextClasses;
    private final String[] nextSteps;
    private final String condition;
    private final String onTrue;
    private final String onFalse;
    
    // Context requirements
    private boolean requiresContext;
    private int contextParameterIndex = -1;
    
    // Return type analysis
    private Class<?> possibleContinueType;
    
    @Builder.Default
    private final Set<Class<?>> possibleBranchTypes = new HashSet<>();
    
    private ReturnTypeInfo returnTypeInfo;
    
    // Retry configuration
    private RetryPolicy retryPolicy;
    
    @Builder.Default
    private int invocationLimit = 100;
    
    @Builder.Default
    private OnInvocationsLimit onInvocationsLimit = OnInvocationsLimit.ERROR;
    
    /**
     * Holds detailed return type information.
     */
    public record ReturnTypeInfo(
        Type rawType,
        Type innerType
    ) {}
}
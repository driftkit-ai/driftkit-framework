package ai.driftkit.workflow.engine.builder;

import ai.driftkit.workflow.engine.core.StepResult;
import ai.driftkit.workflow.engine.core.WorkflowContext;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builder for try-catch-finally pattern.
 */
public class TryBuilder<T, R> {
    private final WorkflowBuilder<T, R> parentBuilder;
    private final StepDefinition tryStep;
    private final Map<Class<? extends Throwable>, ErrorHandler> errorHandlers = new LinkedHashMap<>();
    private Runnable finallyBlock;
    
    TryBuilder(WorkflowBuilder<T, R> parentBuilder, StepDefinition tryStep) {
        if (parentBuilder == null) {
            throw new IllegalArgumentException("Parent builder cannot be null");
        }
        if (tryStep == null) {
            throw new IllegalArgumentException("Try step cannot be null");
        }
        this.parentBuilder = parentBuilder;
        this.tryStep = tryStep;
    }
    
    public TryBuilder<T, R> catchError(Class<? extends Throwable> errorType, ErrorHandler handler) {
        if (errorType == null) {
            throw new IllegalArgumentException("Error type cannot be null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("Error handler cannot be null");
        }
        if (errorHandlers.containsKey(errorType)) {
            throw new IllegalStateException("Duplicate error handler for type: " + errorType.getName());
        }
        errorHandlers.put(errorType, handler);
        return this;
    }
    
    public WorkflowBuilder<T, R> finallyDo(Runnable cleanup) {
        if (cleanup == null) {
            throw new IllegalArgumentException("Finally block cannot be null");
        }
        this.finallyBlock = cleanup;
        // Add try-catch step to parent builder
        parentBuilder.addBuildStep(new WorkflowBuilder.TryCatchStep(tryStep, errorHandlers, finallyBlock));
        return parentBuilder;
    }
    
    // Allow building without finally block
    public WorkflowBuilder<T, R> endTry() {
        parentBuilder.addBuildStep(new WorkflowBuilder.TryCatchStep(tryStep, errorHandlers, null));
        return parentBuilder;
    }
    
    /**
     * Interface for error handlers.
     */
    @FunctionalInterface
    public interface ErrorHandler {
        StepResult<?> handle(Throwable error, WorkflowContext context);
    }
    
    StepDefinition getTryStep() {
        return tryStep;
    }
    
    Map<Class<? extends Throwable>, ErrorHandler> getErrorHandlers() {
        return errorHandlers;
    }
    
    Runnable getFinallyBlock() {
        return finallyBlock;
    }
}
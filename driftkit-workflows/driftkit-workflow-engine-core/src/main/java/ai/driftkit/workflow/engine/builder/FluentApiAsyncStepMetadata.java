package ai.driftkit.workflow.engine.builder;

import ai.driftkit.workflow.engine.annotations.AsyncStep;
import ai.driftkit.workflow.engine.core.AsyncProgressReporter;
import ai.driftkit.workflow.engine.core.StepResult;
import ai.driftkit.workflow.engine.core.WorkflowAnalyzer.AsyncStepMetadata;
import ai.driftkit.workflow.engine.core.WorkflowContext;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Custom AsyncStepMetadata for FluentAPI that stores the TriFunction handler directly.
 * This allows async handlers to be registered without a workflowInstance.
 */
public class FluentApiAsyncStepMetadata extends AsyncStepMetadata {
    private final WorkflowBuilder.TriFunction<Map<String, Object>, WorkflowContext, AsyncProgressReporter, StepResult<?>> handler;
    
    public FluentApiAsyncStepMetadata(Method method, AsyncStep annotation,
                                      WorkflowBuilder.TriFunction<Map<String, Object>, WorkflowContext, AsyncProgressReporter, StepResult<?>> handler) {
        super(method, null, annotation); // null instance - we don't need it
        this.handler = handler;
    }
    
    /**
     * Gets the handler function that will process the async task.
     */
    public WorkflowBuilder.TriFunction<Map<String, Object>, WorkflowContext, AsyncProgressReporter, StepResult<?>> getHandler() {
        return handler;
    }
    
    /**
     * Invokes the handler directly without needing an instance.
     */
    public StepResult<?> invoke(Map<String, Object> taskArgs, WorkflowContext context, AsyncProgressReporter progress) {
        return handler.apply(taskArgs, context, progress);
    }
}
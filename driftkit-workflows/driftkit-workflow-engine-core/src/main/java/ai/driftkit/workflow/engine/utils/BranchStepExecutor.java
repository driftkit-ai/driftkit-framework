package ai.driftkit.workflow.engine.utils;

import ai.driftkit.workflow.engine.builder.StepDefinition;
import ai.driftkit.workflow.engine.core.InternalStepListener;
import ai.driftkit.workflow.engine.core.RetryExecutor;
import ai.driftkit.workflow.engine.core.StepResult;
import ai.driftkit.workflow.engine.core.WorkflowContext;
import ai.driftkit.workflow.engine.graph.StepNode;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.BiFunction;

/**
 * Utility class for executing steps within branches with proper retry and interception support.
 * Eliminates code duplication between TypedBranchStep and MultiBranchStep.
 */
@Slf4j
public final class BranchStepExecutor {
    
    private BranchStepExecutor() {
        // Utility class
    }
    
    /**
     * Executes a list of steps sequentially within a branch context.
     * Handles retry policies, interception, and proper error handling.
     * 
     * @param steps List of steps to execute
     * @param initialInput Initial input for the first step
     * @param ctx Workflow context
     * @param branchName Name of the branch for logging
     * @return Result of the branch execution
     */
    public static StepResult<?> executeBranchSteps(
            List<StepDefinition> steps,
            Object initialInput,
            WorkflowContext ctx,
            String branchName) {
        
        Object currentInput = initialInput;
        Object lastResult = null;
        
        for (StepDefinition stepDef : steps) {
            try {
                StepResult<?> stepResult = executeSingleBranchStep(
                    stepDef, currentInput, ctx, branchName);
                
                // Process the result
                switch (stepResult) {
                    case StepResult.Continue<?> cont -> {
                        lastResult = cont.data();
                        currentInput = lastResult; // Pass output to next step
                    }
                    case StepResult.Fail<?> fail -> {
                        return StepResult.fail(fail.error());
                    }
                    case StepResult.Finish<?> finish -> {
                        return StepResult.finish(finish.result());
                    }
                    case StepResult.Suspend<?> suspend -> {
                        return stepResult; // Return suspension as-is
                    }
                    case StepResult.Async<?> async -> {
                        return stepResult; // Return async as-is for the engine to handle
                    }
                    case StepResult.Branch<?> branch -> {
                        return stepResult; // Handle branch events
                    }
                }
            } catch (Exception e) {
                log.error("Branch {} step {} failed", branchName, stepDef.getId(), e);
                
                // Notify listener about error
                InternalStepListener listener = ctx.getInternalStepListener();
                if (listener != null) {
                    listener.onInternalStepError(stepDef.getId(), e, ctx);
                }
                
                // Re-throw the exception so RetryExecutor can handle it
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                } else {
                    throw new RuntimeException("Step execution failed", e);
                }
            }
        }
        
        // Return the result from the last step in the branch
        return StepResult.continueWith(lastResult != null ? lastResult : initialInput);
    }
    
    /**
     * Executes a single step within a branch with retry and interception support.
     */
    private static StepResult<?> executeSingleBranchStep(
            StepDefinition stepDef,
            Object input,
            WorkflowContext ctx,
            String branchName) throws Exception {
        
        // Notify context about internal step execution for test tracking
        ctx.notifyInternalStepExecution(stepDef.getId(), input);
        
        log.debug("Processing internal step {} in {}, has retry policy: {}", 
            stepDef.getId(), branchName, stepDef.getRetryPolicy() != null);
        
        // Check if listener wants to intercept this step
        StepResult<?> stepResult;
        InternalStepListener listener = ctx.getInternalStepListener();
        
        if (listener != null) {
            var intercepted = listener.interceptInternalStep(stepDef.getId(), input, ctx);
            if (intercepted.isPresent()) {
                stepResult = handleInterceptedStep(stepDef, input, ctx, listener, intercepted.get());
            } else {
                stepResult = executeStepWithRetry(stepDef, input, ctx);
            }
        } else {
            stepResult = executeStepWithRetry(stepDef, input, ctx);
        }
        
        // Notify listener about completion
        if (listener != null) {
            listener.afterInternalStep(stepDef.getId(), stepResult, ctx);
        }
        
        return stepResult;
    }
    
    /**
     * Handles intercepted step execution with proper retry support.
     */
    private static StepResult<?> handleInterceptedStep(
            StepDefinition stepDef,
            Object input,
            WorkflowContext ctx,
            InternalStepListener listener,
            StepResult<?> interceptedResult) throws Exception {
        
        // If the intercepted result is a failure and the step has a retry policy,
        // we need to handle it properly by letting executeStepWithRetry handle the retry
        if (interceptedResult instanceof StepResult.Fail && stepDef.getRetryPolicy() != null) {
            log.debug("Intercepted mock returned failure for step {} with retry policy, delegating to retry executor", 
                stepDef.getId());
            
            // Wrap the mock's behavior in the step executor
            @SuppressWarnings("unchecked")
            BiFunction<Object, WorkflowContext, StepResult<Object>> wrappedExecutor = 
                (Object input2, WorkflowContext ctx2) -> {
                    // Try to intercept again
                    var intercepted2 = listener.interceptInternalStep(stepDef.getId(), input2, ctx2);
                    if (intercepted2.isPresent()) {
                        return (StepResult<Object>) intercepted2.get();
                    } else {
                        // Fall back to original executor
                        try {
                            return (StepResult<Object>) stepDef.getExecutor().execute(input2, ctx2);
                        } catch (Exception e) {
                            if (e instanceof RuntimeException) {
                                throw (RuntimeException) e;
                            }
                            throw new RuntimeException("Step execution failed", e);
                        }
                    }
                };
            
            StepDefinition wrappedStep = StepDefinition.of(stepDef.getId(), wrappedExecutor)
                .withRetryPolicy(stepDef.getRetryPolicy())
                .withInvocationLimit(stepDef.getInvocationLimit())
                .withOnInvocationsLimit(stepDef.getOnInvocationsLimit());
            
            return executeStepWithRetry(wrappedStep, input, ctx);
        }
        
        return interceptedResult;
    }
    
    /**
     * Executes a step with retry support if it has a retry policy.
     * This is used internally when executing steps within branches.
     */
    private static StepResult<?> executeStepWithRetry(
            StepDefinition stepDef,
            Object input,
            WorkflowContext ctx) throws Exception {
        
        if (stepDef.getRetryPolicy() != null) {
            log.debug("Executing step {} with retry policy: maxAttempts={}, delay={}ms", 
                stepDef.getId(), 
                stepDef.getRetryPolicy().maxAttempts(), 
                stepDef.getRetryPolicy().delay());
            
            // Create a minimal RetryExecutor for this specific step
            RetryExecutor retryExecutor = new RetryExecutor();
            
            // Create a fake WorkflowInstance and StepNode just for retry execution
            WorkflowInstance fakeInstance = WorkflowInstance.builder()
                .instanceId(ctx.getRunId())
                .context(ctx)
                .build();
                
            StepNode fakeStepNode = new StepNode(
                stepDef.getId(),
                stepDef.getDescription(),
                new StepNode.StepExecutor() {
                    @Override
                    public Object execute(Object input, WorkflowContext context) throws Exception {
                        return stepDef.getExecutor().execute(input, context);
                    }
                    
                    @Override
                    public Class<?> getInputType() {
                        return stepDef.getInputType();
                    }
                    
                    @Override
                    public Class<?> getOutputType() {
                        return stepDef.getOutputType();
                    }
                    
                    @Override
                    public boolean requiresContext() {
                        return true;
                    }
                },
                false,
                false,
                stepDef.getRetryPolicy(),
                stepDef.getInvocationLimit(),
                stepDef.getOnInvocationsLimit()
            );
            
            // Execute with retry
            return retryExecutor.executeWithRetry(fakeInstance, fakeStepNode, 
                (inst, stp) -> {
                    Object result = stepDef.getExecutor().execute(input, inst.getContext());
                    if (result instanceof StepResult<?>) {
                        return (StepResult<?>) result;
                    } else {
                        // The executor returns the raw result, wrap it in StepResult
                        return StepResult.continueWith(result);
                    }
                });
        } else {
            // No retry policy, execute directly
            Object result = stepDef.getExecutor().execute(input, ctx);
            if (result instanceof StepResult<?>) {
                return (StepResult<?>) result;
            } else {
                // The executor returns the raw result, wrap it in StepResult
                return StepResult.continueWith(result);
            }
        }
    }
}
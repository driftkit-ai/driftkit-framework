package ai.driftkit.workflow.engine.core;

import ai.driftkit.workflow.engine.annotations.AsyncStep;
import ai.driftkit.workflow.engine.core.WorkflowAnalyzer.AsyncStepMetadata;
import ai.driftkit.workflow.engine.graph.StepNode;
import ai.driftkit.workflow.engine.graph.WorkflowGraph;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles execution of @AsyncStep annotated methods for asynchronous workflow operations.
 * This class manages the mapping between async tasks and their handler methods.
 */
@Slf4j
@RequiredArgsConstructor
public class AsyncStepHandler {
    
    private final Map<String, AsyncStepInfo> asyncStepCache = new ConcurrentHashMap<>();
    
    /**
     * Registers async steps from a workflow graph.
     * This is called when a workflow is registered with the engine.
     */
    public void registerWorkflow(WorkflowGraph<?, ?> graph) {
        if (graph.asyncStepMetadata() == null || graph.asyncStepMetadata().isEmpty()) {
            return;
        }
        
        String workflowId = graph.id();
        Object workflowInstance = graph.workflowInstance();
        
        if (workflowInstance == null) {
            log.warn("Workflow {} has async steps but no workflow instance", workflowId);
            return;
        }
        
        for (Entry<String, AsyncStepMetadata> entry : graph.asyncStepMetadata().entrySet()) {
            String asyncStepId = entry.getKey();
            AsyncStepMetadata metadata = entry.getValue();
            
            String key = createKey(workflowId, asyncStepId);
            asyncStepCache.put(key, new AsyncStepInfo(
                metadata.getMethod(),
                workflowInstance,
                metadata.getAnnotation()
            ));
            
            log.debug("Registered async step handler {} for workflow {} with id {}", 
                metadata.getMethod().getName(), workflowId, asyncStepId);
        }
    }
    
    /**
     * Handles the result of an async operation by finding and invoking the appropriate @AsyncStep method.
     * This method is called when an async task completes.
     * 
     * @param graph The workflow graph
     * @param primaryId The primary ID to look up (usually taskId)
     * @param fallbackId The fallback ID to try if primary not found (usually stepId)  
     * @param asyncResult The async task arguments
     * @param context The workflow context
     * @param progressReporter The progress reporter for updating async progress
     * @return The step result
     */
    public StepResult<?> handleAsyncResult(WorkflowGraph<?, ?> graph, 
                                          String primaryId,
                                          String fallbackId,
                                          Object asyncResult, 
                                          WorkflowContext context,
                                          AsyncProgressReporter progressReporter) {
        String workflowId = graph.id();
        
        // Try primary ID first (usually taskId)
        String primaryKey = createKey(workflowId, primaryId);
        AsyncStepInfo info = asyncStepCache.get(primaryKey);
        
        // If not found, try fallback ID (usually stepId)
        if (info == null && fallbackId != null && !fallbackId.equals(primaryId)) {
            String fallbackKey = createKey(workflowId, fallbackId);
            info = asyncStepCache.get(fallbackKey);
            if (info != null) {
                log.debug("Found async handler using fallback ID {} for workflow {}", fallbackId, workflowId);
            }
        }
        
        if (info == null) {
            log.warn("No async handler found for workflow {} with id {} or {}", workflowId, primaryId, fallbackId);
            // If no handler found, continue with the async result
            return new StepResult.Continue<>(asyncResult);
        }
        
        try {
            log.debug("Invoking async handler {} for id {}", info.method.getName(), info == asyncStepCache.get(primaryKey) ? primaryId : fallbackId);
            
            // Build method arguments including AsyncProgressReporter
            Object[] args = buildAsyncMethodArgs(info.method, asyncResult, context, progressReporter);
            Object result = info.method.invoke(info.workflowInstance, args);
            
            if (!(result instanceof StepResult)) {
                throw new IllegalStateException(
                    "Async step handler must return StepResult, got: " + 
                    (result != null ? result.getClass().getName() : "null")
                );
            }
            
            return (StepResult<?>) result;
            
        } catch (Exception e) {
            log.error("Error invoking async handler for id {} or {}", primaryId, fallbackId, e);
            return new StepResult.Fail<>(e);
        }
    }
    
    
    /**
     * Creates a unique key for async step registration.
     */
    private String createKey(String workflowId, String asyncStepId) {
        return workflowId + ":" + asyncStepId;
    }
    
    /**
     * Clears cached async steps for a workflow.
     * This should be called when a workflow is unregistered.
     */
    public void unregisterWorkflow(String workflowId) {
        asyncStepCache.entrySet().removeIf(entry -> entry.getKey().startsWith(workflowId + ":"));
        log.debug("Unregistered async steps for workflow {}", workflowId);
    }
    
    /**
     * Builds method arguments for async step invocation.
     * Async methods must accept: (Map<String, Object> taskArgs, WorkflowContext context, AsyncProgressReporter progress)
     */
    private Object[] buildAsyncMethodArgs(Method method, Object asyncResult, 
                                         WorkflowContext context, AsyncProgressReporter progressReporter) {
        Class<?>[] paramTypes = method.getParameterTypes();
        Object[] args = new Object[paramTypes.length];
        
        boolean hasTaskArgs = false;
        boolean hasContext = false;
        boolean hasProgress = false;
        
        // Fill arguments based on parameter types
        for (int i = 0; i < paramTypes.length; i++) {
            Class<?> paramType = paramTypes[i];
            
            if (!hasTaskArgs && (Map.class.isAssignableFrom(paramType) || paramType.isInstance(asyncResult))) {
                args[i] = asyncResult;
                hasTaskArgs = true;
            } else if (!hasContext && WorkflowContext.class.isAssignableFrom(paramType)) {
                args[i] = context;
                hasContext = true;
            } else if (!hasProgress && AsyncProgressReporter.class.isAssignableFrom(paramType)) {
                args[i] = progressReporter;
                hasProgress = true;
            } else {
                throw new IllegalArgumentException(
                    "Async method " + method.getName() + " has unexpected parameter type at position " + i + 
                    ": " + paramType.getName()
                );
            }
        }
        
        // Validate that all required parameters are present
        if (!hasProgress) {
            throw new IllegalArgumentException(
                "Async method " + method.getName() + " must accept AsyncProgressReporter parameter"
            );
        }
        
        return args;
    }
    
    /**
     * Internal metadata for registered async steps.
     */
    private static class AsyncStepInfo {
        final Method method;
        final Object workflowInstance;
        final AsyncStep annotation;
        
        AsyncStepInfo(Method method, Object workflowInstance, AsyncStep annotation) {
            this.method = method;
            this.workflowInstance = workflowInstance;
            this.annotation = annotation;
            method.setAccessible(true);
        }
    }
}
package ai.driftkit.workflow.engine.core;

import ai.driftkit.workflow.engine.annotations.AsyncStep;
import ai.driftkit.workflow.engine.async.TaskProgressReporter;
import ai.driftkit.workflow.engine.builder.FluentApiAsyncStepMetadata;
import ai.driftkit.workflow.engine.core.WorkflowAnalyzer.AsyncStepMetadata;
import ai.driftkit.workflow.engine.graph.StepNode;
import ai.driftkit.workflow.engine.graph.WorkflowGraph;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import ai.driftkit.workflow.engine.utils.ReflectionUtils;
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
        
        for (Entry<String, AsyncStepMetadata> entry : graph.asyncStepMetadata().entrySet()) {
            String asyncStepId = entry.getKey();
            AsyncStepMetadata metadata = entry.getValue();
            
            String key = createKey(workflowId, asyncStepId);
            
            // Check if this is a FluentAPI metadata (which has no instance)
            if (metadata instanceof FluentApiAsyncStepMetadata) {
                // Store the metadata directly for FluentAPI handlers
                asyncStepCache.put(key, new FluentApiAsyncStepInfo((FluentApiAsyncStepMetadata) metadata));
            } else {
                // Traditional annotation-based handler
                asyncStepCache.put(key, new AsyncStepInfo(
                    metadata.getMethod(),
                    workflowInstance,
                    metadata.getAnnotation()
                ));
            }
            
            log.info("Registered async step handler {} for workflow {} with pattern '{}' (FluentAPI: {})", 
                metadata.getMethod().getName(), workflowId, asyncStepId,
                metadata instanceof FluentApiAsyncStepMetadata);
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
                                          TaskProgressReporter progressReporter) {
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
        
        // If still not found, try wildcard patterns
        if (info == null) {
            info = findByPattern(workflowId, primaryId);
            if (info != null) {
                log.debug("Found async handler using pattern matching for id {} in workflow {}", primaryId, workflowId);
            }
        }
        
        if (info == null) {
            log.warn("No async handler found for workflow {} with id {} or {}", workflowId, primaryId, fallbackId);
            // If no handler found, continue with the async result
            return new StepResult.Continue<>(asyncResult);
        }
        
        try {
            // Check if this is a FluentAPI handler
            if (info instanceof FluentApiAsyncStepInfo) {
                FluentApiAsyncStepInfo fluentInfo = (FluentApiAsyncStepInfo) info;
                log.info("Invoking FluentAPI async handler for id {}", primaryId);
                
                // Cast asyncResult to Map for FluentAPI handlers
                @SuppressWarnings("unchecked")
                Map<String, Object> taskArgs = (Map<String, Object>) asyncResult;
                
                // Invoke the FluentAPI handler directly
                return fluentInfo.invoke(taskArgs, context, progressReporter);
            } else {
                // Traditional annotation-based handler
                log.debug("Invoking async handler {} for id {}", info.method.getName(), info == asyncStepCache.get(primaryKey) ? primaryId : fallbackId);
                
                // Build method arguments including AsyncProgressReporter
                Object[] args = ReflectionUtils.buildAsyncMethodArgs(info.method, asyncResult, context, progressReporter);
                Object result = info.method.invoke(info.workflowInstance, args);
                
                if (!(result instanceof StepResult)) {
                    throw new IllegalStateException(
                        "Async step handler must return StepResult, got: " + 
                        (result != null ? result.getClass().getName() : "null")
                    );
                }
                
                return (StepResult<?>) result;
            }
            
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
     * Finds async handler by pattern matching.
     * Supports wildcard patterns like "*" or "prefix-*".
     */
    private AsyncStepInfo findByPattern(String workflowId, String taskId) {
        String prefix = workflowId + ":";
        AsyncStepInfo wildcardHandler = null;
        
        for (Map.Entry<String, AsyncStepInfo> entry : asyncStepCache.entrySet()) {
            String key = entry.getKey();
            
            // Only check entries for this workflow
            if (!key.startsWith(prefix)) {
                continue;
            }
            
            // Extract the pattern part after "workflowId:"
            String pattern = key.substring(prefix.length());
            
            // Check if pattern matches taskId
            if (matchesPattern(pattern, taskId)) {
                // If it's a wildcard, save it as fallback
                if ("*".equals(pattern)) {
                    wildcardHandler = entry.getValue();
                } else {
                    // Non-wildcard pattern has priority
                    return entry.getValue();
                }
            }
        }
        
        // Return wildcard handler if no specific pattern matched
        return wildcardHandler;
    }
    
    /**
     * Checks if a taskId matches a pattern.
     * Supports "*" for match all and "prefix-*" patterns.
     */
    private boolean matchesPattern(String pattern, String taskId) {
        if ("*".equals(pattern)) {
            return true;
        }
        
        if (pattern.endsWith("*")) {
            String prefix = pattern.substring(0, pattern.length() - 1);
            return taskId.startsWith(prefix);
        }
        
        return pattern.equals(taskId);
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
    
    /**
     * Internal metadata for FluentAPI async steps.
     */
    private static class FluentApiAsyncStepInfo extends AsyncStepInfo {
        private final FluentApiAsyncStepMetadata fluentMetadata;
        
        FluentApiAsyncStepInfo(FluentApiAsyncStepMetadata metadata) {
            super(metadata.getMethod(), null, metadata.getAnnotation());
            this.fluentMetadata = metadata;
        }
        
        StepResult<?> invoke(Map<String, Object> taskArgs, WorkflowContext context, TaskProgressReporter progress) {
            return fluentMetadata.invoke(taskArgs, context, progress);
        }
    }
}
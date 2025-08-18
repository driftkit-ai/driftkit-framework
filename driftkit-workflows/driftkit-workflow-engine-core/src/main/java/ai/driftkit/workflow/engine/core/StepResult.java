package ai.driftkit.workflow.engine.core;

import ai.driftkit.workflow.engine.domain.WorkflowEvent;
import ai.driftkit.workflow.engine.schema.AIFunctionSchema;
import ai.driftkit.workflow.engine.schema.SchemaUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Sealed interface representing all possible outcomes of a workflow step execution.
 * This design pattern enables compile-time type safety for workflow branching logic.
 * 
 * @param <R> The type of the final workflow result (only used by Finish)
 */
public sealed interface StepResult<R> 
    permits StepResult.Continue, StepResult.Suspend, StepResult.Branch, StepResult.Finish, StepResult.Fail, StepResult.Async {
    
    // Static factory methods for better SDK experience
    
    static <T> Suspend<T> suspend(T promptToUser, Class<?> nextInputClass) {
        AIFunctionSchema schema = SchemaUtils.getSchemaFromClass(nextInputClass);
        return new Suspend<>(promptToUser, nextInputClass, schema, new HashMap<>());
    }
    
    static <T> Suspend<T> suspend(T promptToUser, Class<?> nextInputClass, Map<String, Object> metadata) {
        AIFunctionSchema schema = SchemaUtils.getSchemaFromClass(nextInputClass);
        return new Suspend<>(promptToUser, nextInputClass, schema, metadata);
    }
    
    static <T> Async<T> async(String taskId, long estimatedMs, Object immediateData) {
        return new Async<>(taskId, estimatedMs, new HashMap<>(), immediateData);
    }
    
    static <T> Async<T> async(String taskId, long estimatedMs, Map<String, Object> taskArgs, Object immediateData) {
        return new Async<>(taskId, estimatedMs, taskArgs, immediateData);
    }
    
    static <T> Finish<T> finish(T result) {
        return new Finish<>(result);
    }
    
    static <T> Fail<T> fail(String message) {
        return new Fail<>(new RuntimeException(message));
    }
    
    static <T> Fail<T> fail(Throwable error) {
        return new Fail<>(error);
    }
    
    static <T> Continue<T> continueWith(T data) {
        return new Continue<>(data);
    }
    
    static <T> Branch<T> branch(T event) {
        return new Branch<>(event);
    }
    
    /**
     * Standard outcome indicating successful step completion and continuation.
     * The data will be passed as input to the next step in the workflow.
     * 
     * @param <T> The type of data to pass to the next step
     */
    record Continue<T>(T data) implements StepResult<T> {
        public Continue {
            // data can be null for steps that don't produce output
        }
    }
    
    /**
     * Suspends workflow execution for Human-in-the-Loop (HITL) scenarios.
     * The workflow state will be persisted and can be resumed later with user input.
     * 
     * @param <T> The type parameter to maintain consistency with the step's return type
     * @param promptToUser Data to be sent to the user (e.g., question, options)
     * @param nextInputClass The expected input class for when the workflow resumes
     * @param nextInputSchema The schema for the expected input
     * @param metadata Additional information about the expected response format
     */
    record Suspend<T>(
        T promptToUser, 
        Class<?> nextInputClass,
        AIFunctionSchema nextInputSchema,
        Map<String, Object> metadata
    ) implements StepResult<T> {
        public Suspend {
            if (promptToUser == null) {
                throw new IllegalArgumentException("promptToUser cannot be null");
            }
            if (nextInputClass == null) {
                throw new IllegalArgumentException("nextInputClass cannot be null");
            }
            if (nextInputSchema == null) {
                throw new IllegalArgumentException("nextInputSchema cannot be null");
            }
            if (metadata == null) {
                metadata = new HashMap<>();
            }
        }
        
        /**
         * Simplified constructor with empty metadata
         */
        public Suspend(T promptToUser, Class<?> nextInputClass, AIFunctionSchema schema) {
            this(promptToUser, nextInputClass, schema, new HashMap<>());
        }
    }
    
    /**
     * Explicit workflow branching based on an event object.
     * The engine will find the next step that accepts the event's type as input.
     * 
     * @param event The event object used to determine the next step
     */
    record Branch<T>(T event) implements StepResult<T> {
        public Branch {
            if (event == null) {
                throw new IllegalArgumentException("event cannot be null");
            }
        }
    }
    
    /**
     * Successful completion of the entire workflow.
     * 
     * @param <R> The type of the final result
     * @param result The final result of the workflow
     */
    record Finish<R>(R result) implements StepResult<R> {
        // result can be null
    }
    
    /**
     * Workflow termination due to an error.
     * 
     * @param <T> The type parameter to maintain consistency with the step's return type
     * @param error The exception that caused the failure
     */
    record Fail<T>(Throwable error) implements StepResult<T> {
        public Fail {
            if (error == null) {
                throw new IllegalArgumentException("error cannot be null");
            }
        }
        
        /**
         * Convenience constructor that wraps a message in a RuntimeException
         */
        public Fail(String errorMessage) {
            this(new RuntimeException(errorMessage));
        }
    }
    
    /**
     * Indicates that the step is executing asynchronously.
     * The workflow will continue processing in the background.
     * 
     * @param <T> The type parameter to maintain consistency with the step's return type
     * @param taskId The ID of the async task for tracking (must be @AsyncStep method ID)
     * @param estimatedDurationMs Estimated duration in milliseconds (-1 if unknown)
     * @param taskArgs Arguments to pass to the async task
     * @param immediateData ANY user object to return immediately to the user (can be annotated with @SchemaClass)
     */
    record Async<T>(
        String taskId, 
        long estimatedDurationMs,
        Map<String, Object> taskArgs,
        Object immediateData  // Changed from T to Object to allow any type
    ) implements StepResult<T> {
        public Async {
            if (taskId == null || taskId.isBlank()) {
                throw new IllegalArgumentException("taskId cannot be null or blank");
            }
            if (taskArgs == null) {
                taskArgs = new HashMap<>();
            }
            // immediateData can be null
        }
        
        /**
         * Convenience constructor for async without duration estimate
         */
        public Async(String taskId, Map<String, Object> taskArgs, Object immediateData) {
            this(taskId, -1, taskArgs, immediateData);
        }
        
        /**
         * Convenience constructor with just task ID and immediate data
         */
        public Async(String taskId, Object immediateData) {
            this(taskId, -1, new HashMap<>(), immediateData);
        }
        
        /**
         * Convenience constructor for CompletableFuture-based async operations.
         * The future will be registered with the workflow engine for async execution.
         * Uses null as immediate data.
         * 
         * @param taskId The ID of the async task for tracking
         * @param future The CompletableFuture representing the async operation
         */
        public Async(String taskId, CompletableFuture<StepResult<T>> future) {
            this(taskId, -1, Map.of(WorkflowContext.Keys.ASYNC_FUTURE, future), null);
        }
        
        /**
         * Convenience constructor for CompletableFuture-based async operations.
         * The future will be registered with the workflow engine for async execution.
         * 
         * @param taskId The ID of the async task for tracking
         * @param future The CompletableFuture representing the async operation
         * @param immediateData Immediate data to return to the user
         */
        public Async(String taskId, CompletableFuture<StepResult<T>> future, Object immediateData) {
            this(taskId, -1, Map.of(WorkflowContext.Keys.ASYNC_FUTURE, future), immediateData);
        }
    }
}
package ai.driftkit.workflow.engine.spring.controller;

import ai.driftkit.workflow.engine.async.ProgressTracker;
import ai.driftkit.workflow.engine.async.ProgressTracker.Progress;
import ai.driftkit.workflow.engine.core.StepResult.Finish;
import ai.driftkit.workflow.engine.core.WorkflowContext.Keys;
import ai.driftkit.workflow.engine.core.StepResult;
import ai.driftkit.workflow.engine.core.WorkflowEngine;
import ai.driftkit.workflow.engine.domain.WorkflowEvent;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import ai.driftkit.workflow.engine.schema.AIFunctionSchema;
import ai.driftkit.workflow.engine.schema.SchemaUtils;
import ai.driftkit.workflow.engine.spring.service.WorkflowService;
import ai.driftkit.workflow.engine.domain.WorkflowDetails;
import ai.driftkit.workflow.engine.domain.WorkflowMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST controller for workflow-specific execution and management.
 * Chat-related endpoints are in AssistantV3Controller.
 */
@Slf4j
@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
@Validated
public class WorkflowController {
    
    private final WorkflowEngine engine;
    private final ProgressTracker progressTracker;
    private final WorkflowService workflowService;
    
    // Enums
    
    public enum WorkflowStatus {
        PENDING,
        RUNNING,
        SUSPENDED,
        COMPLETED,
        FAILED,
        CANCELLED,
        ASYNC
    }
    
    public enum StepStatus {
        CONTINUE,
        SUSPEND,
        FINISH,
        FAIL,
        ASYNC
    }
    
    // ========== Workflow-specific endpoints ==========
    
    /**
     * Execute a workflow with the given input.
     * 
     * @param workflowId The workflow ID
     * @param request The execution request
     * @return The workflow response
     */
    @PostMapping("/{workflowId}/execute")
    public ResponseEntity<WorkflowResponse> execute(
            @PathVariable String workflowId,
            @RequestBody WorkflowExecutionRequest request,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId
    ) {
        try {
            log.debug("Executing workflow: workflowId={}, sessionId={}", workflowId, sessionId);
            
            // Convert input data using schema utils if needed
            Object input = request.properties();
            if (request.inputClass() != null) {
                Class<?> inputClass = Class.forName(request.inputClass());
                input = SchemaUtils.createInstance(inputClass, request.properties());
            }
            
            // Execute workflow
            var execution = engine.execute(workflowId, input);
            
            // Check if async
            if (execution.isAsync()) {
                String taskId = progressTracker.generateTaskId();
                WorkflowEvent event = WorkflowEvent.asyncStarted(taskId, execution.getRunId());
                progressTracker.trackExecution(taskId, event);
                
                // Return immediate response with task ID
                return ResponseEntity.accepted()
                    .body(WorkflowResponse.async(execution.getRunId(), taskId));
            }
            
            // Get synchronous result
            Object result = execution.getResult();
            
            // Create WorkflowInstance for the response
            WorkflowInstance instance = WorkflowInstance.builder()
                .instanceId(execution.getRunId())
                .workflowId(workflowId)
                .status(WorkflowInstance.WorkflowStatus.COMPLETED)
                .build();
                
            // Wrap result in a Finish StepResult for consistent response format
            StepResult<?> stepResult = new Finish<>(result);
            
            return ResponseEntity.ok(WorkflowResponse.from(instance, stepResult));
            
        } catch (Exception e) {
            log.error("Error executing workflow: workflowId={}", workflowId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(WorkflowResponse.error(e.getMessage()));
        }
    }
    
    /**
     * Resume a suspended workflow with user input.
     * 
     * @param runId The workflow run ID
     * @param request The resume request with user input
     * @return The workflow response
     */
    @PostMapping("/{runId}/resume")
    public ResponseEntity<WorkflowResponse> resume(
            @PathVariable String runId,
            @RequestBody WorkflowResumeRequest request
    ) {
        try {
            log.debug("Resuming workflow: runId={}", runId);
            
            // Convert user input if schema provided
            Object userInput = request.getUserInput();
            if (request.inputClass() != null) {
                Class<?> inputClass = Class.forName(request.inputClass());
                userInput = SchemaUtils.createInstance(inputClass, request.properties());
            }
            
            // Resume workflow
            var execution = engine.resume(runId, userInput);
            
            // Get result
            Object result = execution.getResult();
            
            // Create WorkflowInstance for the response
            WorkflowInstance instance = WorkflowInstance.builder()
                .instanceId(execution.getRunId())
                .workflowId(execution.getWorkflowId())
                .status(WorkflowInstance.WorkflowStatus.COMPLETED)
                .build();
                
            // Wrap result in a Finish StepResult for consistent response format
            StepResult<?> stepResult = new Finish<>(result);
                
            return ResponseEntity.ok(WorkflowResponse.from(instance, stepResult));
            
        } catch (Exception e) {
            log.error("Error resuming workflow: runId={}", runId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(WorkflowResponse.error(e.getMessage()));
        }
    }
    
    /**
     * Get the status of a workflow execution.
     * 
     * @param runId The workflow run ID
     * @return The workflow status
     */
    @GetMapping("/{runId}/status")
    public ResponseEntity<WorkflowStatusResponse> getStatus(@PathVariable String runId) {
        try {
            // Check with progress tracker first for async tasks
            var progress = progressTracker.getExecution(runId)
                .flatMap(event -> progressTracker.getProgress(event.getAsyncTaskId()));
            
            if (progress.isPresent()) {
                return ResponseEntity.ok(WorkflowStatusResponse.fromProgress(progress.get()));
            }
            
            // Check workflow state
            var state = workflowService.getWorkflowState(runId);
            if (state.isPresent()) {
                return ResponseEntity.ok(WorkflowStatusResponse.fromState(state.get()));
            }
            
            return ResponseEntity.notFound().build();
            
        } catch (Exception e) {
            log.error("Error getting workflow status: runId={}", runId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get the current result of a workflow execution.
     * This returns the current state, including intermediate results for async/suspended workflows.
     * 
     * @param runId The workflow run ID
     * @return The current workflow result
     */
    @GetMapping("/{runId}/current")
    public ResponseEntity<WorkflowCurrentResultResponse> getCurrentResult(@PathVariable String runId) {
        try {
            Optional<WorkflowEvent> currentResult = engine.getCurrentResult(runId);
            
            if (currentResult.isPresent()) {
                WorkflowEvent event = currentResult.get();
                String message = event.getProperties() != null ? 
                    event.getProperties().get("message") : null;
                    
                Map<String, Object> data = event.getProperties() != null ? 
                    new HashMap<>(event.getProperties()) : null;
                    
                return ResponseEntity.ok(new WorkflowCurrentResultResponse(
                    runId,
                    event.isCompleted() ? WorkflowStatus.COMPLETED : WorkflowStatus.RUNNING,
                    event.getPercentComplete(),
                    message,
                    data,
                    event.isAsync()
                ));
            }
            
            // Fallback to checking workflow state
            var state = workflowService.getWorkflowState(runId);
            if (state.isPresent()) {
                WorkflowInstance instance = state.get();
                Object result = instance.getContext() != null 
                    ? instance.getContext().getStepOutputs().get(Keys.FINAL_RESULT)
                    : null;
                    
                return ResponseEntity.ok(new WorkflowCurrentResultResponse(
                    runId,
                    mapWorkflowStatus(instance.getStatus()),
                    instance.getStatus() == WorkflowInstance.WorkflowStatus.COMPLETED ? 100 : 0,
                    "Workflow " + instance.getStatus().toString().toLowerCase(),
                    result != null ? Map.of("result", result) : null,
                    false
                ));
            }
            
            return ResponseEntity.notFound().build();
            
        } catch (Exception e) {
            log.error("Error getting current result: runId={}", runId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Cancel an async operation for a workflow.
     * 
     * @param runId The workflow run ID
     * @return Success indicator
     */
    @PostMapping("/{runId}/cancel")
    public ResponseEntity<WorkflowCancelResponse> cancelAsyncOperation(@PathVariable String runId) {
        try {
            boolean cancelled = engine.cancelAsyncOperation(runId);
            
            if (cancelled) {
                return ResponseEntity.ok(new WorkflowCancelResponse(
                    runId,
                    true,
                    "Async operation cancelled successfully"
                ));
            } else {
                return ResponseEntity.ok(new WorkflowCancelResponse(
                    runId,
                    false,
                    "No active async operation found for this workflow"
                ));
            }
            
        } catch (Exception e) {
            log.error("Error cancelling async operation: runId={}", runId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new WorkflowCancelResponse(
                    runId,
                    false,
                    "Error cancelling operation: " + e.getMessage()
                ));
        }
    }
    
    private WorkflowStatus mapWorkflowStatus(WorkflowInstance.WorkflowStatus status) {
        return switch (status) {
            case RUNNING -> WorkflowStatus.RUNNING;
            case SUSPENDED -> WorkflowStatus.SUSPENDED;
            case COMPLETED -> WorkflowStatus.COMPLETED;
            case FAILED -> WorkflowStatus.FAILED;
            case CANCELLED -> WorkflowStatus.CANCELLED;
        };
    }
    
    /**
     * Get all schemas for a specific workflow.
     * 
     * @param workflowId The workflow ID
     * @return List of schemas for all steps
     */
    @GetMapping("/{workflowId}/schemas")
    public ResponseEntity<List<AIFunctionSchema>> getWorkflowSchemas(@PathVariable String workflowId) {
        try {
            List<AIFunctionSchema> schemas = workflowService.getWorkflowSchemas(workflowId);
            return ResponseEntity.ok(schemas);
        } catch (Exception e) {
            log.error("Error getting workflow schemas: workflowId={}", workflowId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * List all available workflows
     * 
     * @return List of workflow metadata
     */
    @GetMapping
    public ResponseEntity<List<WorkflowMetadata>> listWorkflows() {
        try {
            List<WorkflowMetadata> workflows = workflowService.listWorkflows();
            return ResponseEntity.ok(workflows);
        } catch (Exception e) {
            log.error("Error listing workflows", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get detailed information about a workflow
     * 
     * @param workflowId The workflow ID
     * @return Workflow details
     */
    @GetMapping("/{workflowId}")
    public ResponseEntity<WorkflowDetails> getWorkflowDetails(@PathVariable String workflowId) {
        try {
            WorkflowDetails details = workflowService.getWorkflowDetails(workflowId);
            if (details != null) {
                return ResponseEntity.ok(details);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error getting workflow details: workflowId={}", workflowId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    
    // ========== Request/Response DTOs ==========
    
    record WorkflowExecutionRequest(
        Map<String, String> properties,
        String inputClass
    ) {
    }
    
    record WorkflowResumeRequest(
        Map<String, Object> userInput,
        Map<String, String> properties,
        String inputClass
    ) {
        public Object getUserInput() {
            return userInput != null ? userInput : properties;
        }
    }
    
    record WorkflowResponse(
        String runId,
        StepStatus status,
        Object result,
        String error,
        boolean async,
        String asyncTaskId,
        AIFunctionSchema nextInputSchema,
        List<AIFunctionSchema> possibleSchemas
    ) {
        public static WorkflowResponse from(WorkflowInstance instance, StepResult<?> result) {
            StepStatus status;
            Object data = null;
            String error = null;
            
            if (result instanceof StepResult.Continue<?> cont) {
                status = StepStatus.CONTINUE;
                data = cont.data();
            } else if (result instanceof StepResult.Suspend<?> susp) {
                status = StepStatus.SUSPEND;
                data = Map.of("suspensionId", instance.getInstanceId() + "_" + instance.getCurrentStepId());
            } else if (result instanceof StepResult.Finish<?> fin) {
                status = StepStatus.FINISH;
                data = fin.result();
            } else if (result instanceof StepResult.Fail<?> fail) {
                status = StepStatus.FAIL;
                error = fail.error().getMessage();
            } else {
                status = StepStatus.CONTINUE;
            }
            
            return new WorkflowResponse(
                instance.getInstanceId(),
                status,
                data,
                error,
                false,
                null,
                null,
                null
            );
        }
        
        public static WorkflowResponse async(String runId, String taskId) {
            return new WorkflowResponse(
                runId,
                StepStatus.ASYNC,
                null,
                null,
                true,
                taskId,
                null,
                null
            );
        }
        
        public static WorkflowResponse error(String error) {
            return new WorkflowResponse(
                null,
                StepStatus.FAIL,
                null,
                error,
                false,
                null,
                null,
                null
            );
        }
    }
    
    record WorkflowStatusResponse(
        WorkflowStatus status,
        int percentComplete,
        String message,
        Object data
    ) {
        public static WorkflowStatusResponse fromProgress(Progress progress) {
            WorkflowStatus status = switch (progress.status()) {
                case PENDING -> WorkflowStatus.PENDING;
                case IN_PROGRESS -> WorkflowStatus.RUNNING;
                case COMPLETED -> WorkflowStatus.COMPLETED;
                case FAILED -> WorkflowStatus.FAILED;
                case CANCELLED -> WorkflowStatus.CANCELLED;
            };
            
            return new WorkflowStatusResponse(
                status,
                progress.percentComplete(),
                progress.message(),
                null
            );
        }
        
        public static WorkflowStatusResponse fromState(WorkflowInstance state) {
            WorkflowStatus status = switch (state.getStatus()) {
                case RUNNING -> WorkflowStatus.RUNNING;
                case SUSPENDED -> WorkflowStatus.SUSPENDED;
                case COMPLETED -> WorkflowStatus.COMPLETED;
                case FAILED -> WorkflowStatus.FAILED;
                case CANCELLED -> WorkflowStatus.CANCELLED;
            };
            
            return new WorkflowStatusResponse(
                status,
                state.getStatus() == WorkflowInstance.WorkflowStatus.COMPLETED ? 100 : 0,
                "Workflow " + state.getStatus().toString().toLowerCase(),
                state.getContext() != null ? state.getContext().getStepOutputs().get(Keys.FINAL_RESULT) : null
            );
        }
    }
    
    record WorkflowCurrentResultResponse(
        String runId,
        WorkflowStatus status,
        int percentComplete,
        String message,
        Map<String, Object> data,
        boolean isAsync
    ) {}
    
    record WorkflowCancelResponse(
        String runId,
        boolean cancelled,
        String message
    ) {}
    
}
package ai.driftkit.workflow.engine.spring.dto;

import ai.driftkit.workflow.engine.async.ProgressTracker.Progress;
import ai.driftkit.workflow.engine.core.StepResult;
import ai.driftkit.workflow.engine.core.WorkflowContext.Keys;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import ai.driftkit.workflow.engine.schema.AIFunctionSchema;

import java.util.List;
import java.util.Map;

/**
 * DTO classes for Workflow operations.
 */
public class WorkflowDtos {
    
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
    
    public record WorkflowExecutionRequest(
        Map<String, String> properties,
        String inputClass
    ) {
    }
    
    public record WorkflowResumeRequest(
        Map<String, Object> userInput,
        Map<String, String> properties,
        String inputClass
    ) {
        public Object getUserInput() {
            return userInput != null ? userInput : properties;
        }
    }
    
    public record WorkflowResponse(
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
    
    public record WorkflowStatusResponse(
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
    
    public record WorkflowCurrentResultResponse(
        String runId,
        WorkflowStatus status,
        int percentComplete,
        String message,
        Map<String, Object> data,
        boolean isAsync
    ) {}
    
    public record WorkflowCancelResponse(
        String runId,
        boolean cancelled,
        String message
    ) {}
    
    public record WorkflowHistoryEntry(
        String runId,
        String workflowId,
        WorkflowStatus status,
        long startedAt,
        Long completedAt,
        Long executionTimeMs,
        String userId,
        String sessionId,
        Map<String, Object> input,
        Object result,
        String error
    ) {}
}
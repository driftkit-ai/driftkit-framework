package ai.driftkit.workflow.controllers.controller;

import ai.driftkit.common.domain.PromptRequest;
import ai.driftkit.workflow.engine.agent.AgentResponse;
import ai.driftkit.workflow.engine.spring.tracing.domain.AsyncTaskEntity;
import ai.driftkit.workflow.engine.spring.tracing.domain.AsyncTaskEntity.TaskStatus;
import ai.driftkit.workflow.controllers.service.AsyncTaskService;
import ai.driftkit.workflow.engine.spring.dto.ModelRequestDtos.AsyncTaskResponse;
import ai.driftkit.workflow.engine.spring.dto.ModelRequestDtos.TaskRating;
import ai.driftkit.workflow.engine.spring.dto.ModelRequestDtos.TextRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for asynchronous LLM model requests.
 * Returns task IDs immediately and allows checking status/results later.
 * Only activated when AsyncTaskService is available (requires MongoDB).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/model/async")
@RequiredArgsConstructor
@ConditionalOnWebApplication
public class AsyncModelRequestController {

    @Autowired(required = false)
    private AsyncTaskService asyncTaskService;
    
    /**
     * Process a prompt request asynchronously - returns task ID immediately
     */
    @PostMapping(value = "/prompt", produces = MediaType.APPLICATION_JSON_VALUE)
    public AsyncTaskResponse processPromptRequestAsync(
            @RequestBody PromptRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        log.debug("Processing async prompt request");
        
        // Use "anonymous" if no user ID provided
        String effectiveUserId = userId != null ? userId : "anonymous";
        
        // Create async task and return ID
        String taskId = asyncTaskService.executePromptRequestAsync(request, effectiveUserId);
        
        return new AsyncTaskResponse(taskId, TaskStatus.PENDING);
    }
    
    /**
     * Process a text request asynchronously - returns task ID immediately
     */
    @PostMapping(value = "/text", produces = MediaType.APPLICATION_JSON_VALUE)
    public AsyncTaskResponse processTextRequestAsync(
            @RequestBody TextRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        log.debug("Processing async text request");
        
        // Use "anonymous" if no user ID provided
        String effectiveUserId = userId != null ? userId : "anonymous";
        
        // Create async task and return ID
        String taskId = asyncTaskService.executeTextRequestAsync(request, effectiveUserId);
        
        return new AsyncTaskResponse(taskId, TaskStatus.PENDING);
    }
    
    /**
     * Get task status
     */
    @GetMapping("/task/{taskId}/status")
    public ResponseEntity<AsyncTaskResponse> getTaskStatus(@PathVariable String taskId) {
        log.debug("Getting status for task: {}", taskId);
        
        return asyncTaskService.getTask(taskId)
                .map(task -> ResponseEntity.ok(new AsyncTaskResponse(task.getTaskId(), task.getStatus())))
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Get task result
     */
    @GetMapping("/task/{taskId}/result")
    public ResponseEntity<?> getTaskResult(@PathVariable String taskId) {
        log.debug("Getting result for task: {}", taskId);
        
        // First check if task exists
        var taskOpt = asyncTaskService.getTask(taskId);
        if (taskOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        AsyncTaskEntity task = taskOpt.get();
        
        // Check task status
        switch (task.getStatus()) {
            case COMPLETED:
                // Get the result
                Optional<AgentResponse<?>> result = asyncTaskService.getTaskResult(taskId);
                if (result.isPresent()) {
                    return ResponseEntity.ok(result.get());
                } else {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(AgentResponse.text("Failed to retrieve result"));
                }
            
            case FAILED:
                return ResponseEntity.ok(AgentResponse.text(task.getErrorMessage()));
            
            case CANCELLED:
                return ResponseEntity.ok(AgentResponse.text("Task was cancelled"));
            
            case PENDING:
            case RUNNING:
                // Task is still in progress
                return ResponseEntity.status(HttpStatus.ACCEPTED)
                        .body(new AsyncTaskResponse(taskId, task.getStatus()));
            
            default:
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(AgentResponse.text("Unknown task status"));
        }
    }
    
    /**
     * Get full task details
     */
    @GetMapping("/task/{taskId}")
    public ResponseEntity<AsyncTaskEntity> getTask(@PathVariable String taskId) {
        log.debug("Getting task details: {}", taskId);
        
        return asyncTaskService.getTask(taskId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Cancel a task
     */
    @DeleteMapping("/task/{taskId}")
    public ResponseEntity<Void> cancelTask(@PathVariable String taskId) {
        log.debug("Cancelling task: {}", taskId);
        
        boolean cancelled = asyncTaskService.cancelTask(taskId);
        
        if (cancelled) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
        }
    }
    
    /**
     * Rate a task result
     */
    @PostMapping("/task/{taskId}/rate")
    public ResponseEntity<AsyncTaskEntity> rateTask(
            @PathVariable String taskId,
            @RequestBody TaskRating rating) {
        log.debug("Rating task: {} with grade: {}", taskId, rating.getGrade());
        
        Optional<AsyncTaskEntity> taskOpt = asyncTaskService.rateTask(taskId, rating.getGrade(), rating.getComment());
        
        return taskOpt
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
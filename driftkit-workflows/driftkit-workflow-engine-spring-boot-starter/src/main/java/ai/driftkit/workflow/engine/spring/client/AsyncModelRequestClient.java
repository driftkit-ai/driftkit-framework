package ai.driftkit.workflow.engine.spring.client;

import ai.driftkit.common.domain.PromptRequest;
import ai.driftkit.workflow.engine.spring.dto.ModelRequestDtos.AsyncTaskResponse;
import ai.driftkit.workflow.engine.spring.dto.ModelRequestDtos.TaskRating;
import ai.driftkit.workflow.engine.spring.dto.ModelRequestDtos.TextRequest;
import ai.driftkit.workflow.engine.spring.tracing.domain.AsyncTaskEntity;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Feign client for AsyncModelRequestController.
 * Provides remote access to asynchronous LLM model request endpoints.
 */
@FeignClient(name = "async-model-request-service", path = "/api/v1/model/async", configuration = WorkflowFeignConfiguration.class)
public interface AsyncModelRequestClient {
    
    /**
     * Process a prompt request asynchronously - returns task ID immediately.
     */
    @PostMapping(value = "/prompt", produces = MediaType.APPLICATION_JSON_VALUE)
    AsyncTaskResponse processPromptRequestAsync(
            @RequestBody PromptRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId
    );
    
    /**
     * Process a text request asynchronously - returns task ID immediately.
     */
    @PostMapping(value = "/text", produces = MediaType.APPLICATION_JSON_VALUE)
    AsyncTaskResponse processTextRequestAsync(
            @RequestBody TextRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId
    );
    
    /**
     * Get task status.
     */
    @GetMapping("/task/{taskId}/status")
    ResponseEntity<AsyncTaskResponse> getTaskStatus(@PathVariable("taskId") String taskId);
    
    /**
     * Get task result.
     */
    @GetMapping("/task/{taskId}/result")
    ResponseEntity<?> getTaskResult(@PathVariable("taskId") String taskId);
    
    /**
     * Get full task details.
     */
    @GetMapping("/task/{taskId}")
    ResponseEntity<AsyncTaskEntity> getTask(@PathVariable("taskId") String taskId);
    
    /**
     * Cancel a task.
     */
    @DeleteMapping("/task/{taskId}")
    ResponseEntity<Void> cancelTask(@PathVariable("taskId") String taskId);
    
    /**
     * Rate a task result.
     */
    @PostMapping("/task/{taskId}/rate")
    ResponseEntity<AsyncTaskEntity> rateTask(
            @PathVariable("taskId") String taskId,
            @RequestBody TaskRating rating
    );
}
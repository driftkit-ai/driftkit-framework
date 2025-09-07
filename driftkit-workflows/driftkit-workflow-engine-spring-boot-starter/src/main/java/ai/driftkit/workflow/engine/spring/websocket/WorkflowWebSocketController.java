package ai.driftkit.workflow.engine.spring.websocket;

import ai.driftkit.workflow.engine.async.ProgressTracker;
import ai.driftkit.workflow.engine.core.WorkflowEngine;
import ai.driftkit.workflow.engine.spring.service.WorkflowService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket controller for handling real-time workflow interactions.
 * Provides endpoints for subscribing to updates and sending commands.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "driftkit.workflow.websocket",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class WorkflowWebSocketController {
    
    private final WorkflowEngine engine;
    private final WorkflowService workflowService;
    private final WorkflowWebSocketService webSocketService;
    private final ProgressTracker progressTracker;
    
    /**
     * Subscribe to workflow status updates.
     * Called when client subscribes to /topic/workflow/{runId}/status
     */
    @SubscribeMapping("/topic/workflow/{runId}/status")
    public WorkflowWebSocketService.WorkflowStatusUpdate subscribeToWorkflowStatus(
            @DestinationVariable String runId) {
        log.info("Client subscribed to workflow status for runId: {}", runId);
        
        // Send current status immediately upon subscription
        return engine.getWorkflowInstance(runId)
                .map(instance -> new WorkflowWebSocketService.WorkflowStatusUpdate(
                        runId,
                        instance.getWorkflowId(),
                        instance.getStatus().toString(),
                        instance.getCurrentStepId(),
                        instance.getNextStepId(),
                        instance.getUpdatedAt()
                ))
                .orElse(null);
    }
    
    /**
     * Subscribe to async task progress.
     * Called when client subscribes to /topic/async/{runId}/{taskId}/progress
     */
    @SubscribeMapping("/topic/async/{runId}/{taskId}/progress")
    public WorkflowWebSocketService.AsyncProgressUpdate subscribeToAsyncProgress(
            @DestinationVariable String runId,
            @DestinationVariable String taskId) {
        log.info("Client subscribed to async progress for runId: {}, taskId: {}", runId, taskId);
        
        // Send current progress immediately upon subscription
        return progressTracker.getProgress(taskId)
                .map(progress -> new WorkflowWebSocketService.AsyncProgressUpdate(
                        runId,
                        taskId,
                        progress.status().toString(),
                        progress.percentComplete(),
                        progress.message(),
                        System.currentTimeMillis()
                ))
                .orElse(null);
    }
    
    /**
     * Execute workflow via WebSocket.
     * Client sends to /app/workflow/execute
     */
    @MessageMapping("/workflow/execute")
    @SendTo("/topic/workflow/executions")
    public WorkflowExecutionResponse executeWorkflow(
            @Payload WorkflowExecutionRequest request,
            Principal principal) {
        log.info("Executing workflow via WebSocket: workflowId={}, user={}", 
                request.getWorkflowId(), principal != null ? principal.getName() : "anonymous");
        
        try {
            var execution = engine.execute(request.getWorkflowId(), request.getInput());
            
            // Workflow updates will be sent automatically through WorkflowService
            
            return new WorkflowExecutionResponse(
                    execution.getRunId(),
                    request.getWorkflowId(),
                    "STARTED",
                    null
            );
        } catch (Exception e) {
            log.error("Error executing workflow via WebSocket", e);
            return new WorkflowExecutionResponse(
                    null,
                    request.getWorkflowId(),
                    "ERROR",
                    e.getMessage()
            );
        }
    }
    
    
    // Request/Response DTOs
    
    @Data
    public static class WorkflowExecutionRequest {
        private String workflowId;
        private Map<String, Object> input;
        private Map<String, String> metadata;
    }
    
    @Data
    public static class WorkflowExecutionResponse {
        private final String runId;
        private final String workflowId;
        private final String status;
        private final String error;
    }
}
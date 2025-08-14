package ai.driftkit.workflow.engine.spring.websocket;

import ai.driftkit.workflow.engine.async.ProgressTracker;
import ai.driftkit.workflow.engine.chat.ChatDomain.ChatMessage;
import ai.driftkit.workflow.engine.chat.ChatDomain.ChatResponse;
import ai.driftkit.workflow.engine.domain.WorkflowEvent;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service for sending real-time workflow updates via WebSocket.
 * Provides methods to broadcast workflow state changes, progress updates, and chat messages.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "driftkit.workflow.websocket",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class WorkflowWebSocketService {
    
    private final SimpMessagingTemplate messagingTemplate;
    
    /**
     * Send workflow status update to subscribers.
     */
    public void sendWorkflowStatusUpdate(String runId, WorkflowInstance instance) {
        WorkflowStatusUpdate update = new WorkflowStatusUpdate(
            runId,
            instance.getWorkflowId(),
            instance.getStatus().toString(),
            instance.getCurrentStepId(),
            instance.getNextStepId(),
            System.currentTimeMillis()
        );
        
        String destination = "/topic/workflow/" + runId + "/status";
        messagingTemplate.convertAndSend(destination, update);
        log.debug("Sent workflow status update for runId: {} to {}", runId, destination);
    }
    
    /**
     * Send async task progress update.
     */
    public void sendAsyncProgressUpdate(String runId, String taskId, ProgressTracker.Progress progress) {
        AsyncProgressUpdate update = new AsyncProgressUpdate(
            runId,
            taskId,
            progress.status().toString(),
            progress.percentComplete(),
            progress.message(),
            System.currentTimeMillis()
        );
        
        String destination = "/topic/async/" + runId + "/" + taskId + "/progress";
        messagingTemplate.convertAndSend(destination, update);
        log.debug("Sent async progress update for taskId: {} to {}", taskId, destination);
    }
    
    /**
     * Send workflow event to subscribers.
     */
    public void sendWorkflowEvent(String runId, WorkflowEvent event) {
        String destination = "/topic/workflow/" + runId + "/events";
        messagingTemplate.convertAndSend(destination, event);
        log.debug("Sent workflow event for runId: {} to {}", runId, destination);
    }
    
    /**
     * Send chat message (request or response) to subscribers.
     */
    public void sendChatMessage(String chatId, Object message) {
        String destination = "/topic/chat/" + chatId + "/messages";
        messagingTemplate.convertAndSend(destination, message);
        log.debug("Sent chat message for chatId: {} to {}", chatId, destination);
    }
    
    /**
     * Send chat message update.
     */
    public void sendChatMessage(String chatId, ChatMessage message) {
        String destination = "/topic/chat/" + chatId + "/messages";
        messagingTemplate.convertAndSend(destination, message);
        log.debug("Sent chat message for chatId: {} to {}", chatId, destination);
    }
    
    /**
     * Send chat response update (for async responses).
     */
    public void sendChatResponseUpdate(String chatId, ChatResponse response) {
        ChatResponseUpdate update = new ChatResponseUpdate(
            response.getId(),
            chatId,
            response.isCompleted(),
            response.getPercentComplete(),
            response.getPropertiesMap(),
            System.currentTimeMillis()
        );
        
        String destination = "/topic/chat/" + chatId + "/responses/" + response.getId();
        messagingTemplate.convertAndSend(destination, update);
        log.debug("Sent chat response update for responseId: {} to {}", response.getId(), destination);
    }
    
    /**
     * Send error notification.
     */
    public void sendErrorNotification(String runId, String stepId, Throwable error) {
        ErrorNotification notification = new ErrorNotification(
            runId,
            stepId,
            error.getClass().getSimpleName(),
            error.getMessage(),
            System.currentTimeMillis()
        );
        
        String destination = "/topic/workflow/" + runId + "/errors";
        messagingTemplate.convertAndSend(destination, notification);
        log.debug("Sent error notification for runId: {} to {}", runId, destination);
    }
    
    /**
     * Send workflow completion notification.
     */
    public void sendCompletionNotification(String runId, Object result) {
        CompletionNotification notification = new CompletionNotification(
            runId,
            result != null ? result.getClass().getSimpleName() : null,
            result,
            System.currentTimeMillis()
        );
        
        String destination = "/topic/workflow/" + runId + "/completion";
        messagingTemplate.convertAndSend(destination, notification);
        log.debug("Sent completion notification for runId: {} to {}", runId, destination);
    }
    
    // DTOs for WebSocket messages
    
    @Data
    public static class WorkflowStatusUpdate {
        private final String runId;
        private final String workflowId;
        private final String status;
        private final String currentStepId;
        private final String nextStepId;
        private final long timestamp;
    }
    
    @Data
    public static class AsyncProgressUpdate {
        private final String runId;
        private final String taskId;
        private final String status;
        private final int percentComplete;
        private final String message;
        private final long lastUpdateTime;
    }
    
    @Data
    public static class ChatResponseUpdate {
        private final String responseId;
        private final String chatId;
        private final boolean completed;
        private final Integer percentComplete;
        private final Map<String, String> properties;
        private final long timestamp;
    }
    
    @Data
    public static class ErrorNotification {
        private final String runId;
        private final String stepId;
        private final String errorType;
        private final String errorMessage;
        private final long timestamp;
    }
    
    @Data
    public static class CompletionNotification {
        private final String runId;
        private final String resultType;
        private final Object result;
        private final long timestamp;
    }
}
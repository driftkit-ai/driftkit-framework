package ai.driftkit.workflow.engine.spring.websocket;

import ai.driftkit.workflow.engine.chat.ChatDomain.ChatRequest;
import ai.driftkit.workflow.engine.chat.ChatDomain.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Bridge component that sends chat messages to WebSocket clients.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "driftkit.workflow.websocket",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
@ConditionalOnBean(WorkflowWebSocketService.class)
public class WorkflowEventWebSocketBridge {
    
    private final WorkflowWebSocketService webSocketService;
    
    /**
     * Send chat request to WebSocket clients.
     */
    public void sendChatRequest(ChatRequest request) {
        log.debug("Sending ChatRequest to WebSocket: chatId={}, userId={}", 
            request.getChatId(), request.getUserId());
        webSocketService.sendChatMessage(request.getChatId(), request);
    }
    
    /**
     * Send chat response to WebSocket clients.
     */
    public void sendChatResponse(ChatResponse response) {
        log.debug("Sending ChatResponse to WebSocket: chatId={}, completed={}", 
            response.getChatId(), response.isCompleted());
        webSocketService.sendChatMessage(response.getChatId(), response);
    }
}
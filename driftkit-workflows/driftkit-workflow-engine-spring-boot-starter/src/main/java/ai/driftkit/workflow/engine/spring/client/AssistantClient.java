package ai.driftkit.workflow.engine.spring.client;

import ai.driftkit.common.domain.chat.ChatRequest;
import ai.driftkit.common.domain.chat.ChatResponse;
import ai.driftkit.workflow.engine.spring.dto.AssistantDtos.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Feign client for AssistantController.
 * Provides remote access to AI assistant chat endpoints.
 */
@FeignClient(name = "assistant-service", path = "/api/v3/assistant", configuration = WorkflowFeignConfiguration.class)
public interface AssistantClient {
    
    /**
     * Send a chat message synchronously.
     */
    @PostMapping(value = "/chat", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ChatResponse> chat(
            @RequestBody ChatRequest request,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Workflow-Id", required = false) String workflowId
    );
    
    /**
     * Send a chat message with streaming response.
     * Note: Feign doesn't support streaming responses well. 
     * Consider using WebClient for streaming endpoints.
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<StreamEvent> chatStream(
            @RequestBody ChatRequest request,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Workflow-Id", required = false) String workflowId
    );
    
    /**
     * Start a new chat session.
     */
    @PostMapping("/sessions")
    ResponseEntity<SessionInfo> createSession(
            @RequestBody(required = false) SessionRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId
    );
    
    /**
     * Get session info.
     */
    @GetMapping("/sessions/{sessionId}")
    ResponseEntity<SessionInfo> getSession(
            @PathVariable("sessionId") String sessionId,
            @RequestHeader(value = "X-User-Id", required = false) String userId
    );
    
    /**
     * Update session context.
     */
    @PutMapping("/sessions/{sessionId}/context")
    ResponseEntity<Void> updateSessionContext(
            @PathVariable("sessionId") String sessionId,
            @RequestBody SessionContext context,
            @RequestHeader(value = "X-User-Id", required = false) String userId
    );
    
    /**
     * Clear session memory.
     */
    @DeleteMapping("/sessions/{sessionId}/memory")
    ResponseEntity<Void> clearSessionMemory(
            @PathVariable("sessionId") String sessionId,
            @RequestHeader(value = "X-User-Id", required = false) String userId
    );
    
    /**
     * End a chat session.
     */
    @DeleteMapping("/sessions/{sessionId}")
    ResponseEntity<Void> endSession(
            @PathVariable("sessionId") String sessionId,
            @RequestHeader(value = "X-User-Id", required = false) String userId
    );
    
    /**
     * Get chat history for a session.
     */
    @GetMapping("/sessions/{sessionId}/history")
    ResponseEntity<List<ChatMessage>> getChatHistory(
            @PathVariable("sessionId") String sessionId,
            @RequestParam(value = "limit", defaultValue = "100") int limit,
            @RequestHeader(value = "X-User-Id", required = false) String userId
    );
    
    /**
     * Get available workflows for assistant.
     */
    @GetMapping("/workflows")
    ResponseEntity<List<WorkflowInfo>> getAvailableWorkflows(
            @RequestHeader(value = "X-User-Id", required = false) String userId
    );
    
    /**
     * Get assistant capabilities.
     */
    @GetMapping("/capabilities")
    ResponseEntity<AssistantCapabilities> getCapabilities();
}
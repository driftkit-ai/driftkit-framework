package ai.driftkit.chat.framework.service;

import ai.driftkit.chat.framework.model.ChatDomain.ChatMessage;
import ai.driftkit.chat.framework.model.ChatDomain.ChatRequest;
import ai.driftkit.chat.framework.model.ChatDomain.ChatResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service interface for managing chat history.
 * Implementation should be provided by the consuming application.
 */
public interface ChatHistoryService {
    
    /**
     * Add a chat request to the history
     * @param request The request to add
     */
    void addRequest(ChatRequest request);
    
    /**
     * Add a chat response to the history
     * @param response The response to add
     */
    void addResponse(ChatResponse response);
    
    /**
     * Update an existing response in the history
     * @param response The response to update
     */
    void updateResponse(ChatResponse response);
    
    /**
     * Get a request by ID
     * @param requestId The request ID
     * @return The request if found, null otherwise
     */
    ChatRequest getRequest(String requestId);
    
    /**
     * Get a response by ID
     * @param responseId The response ID
     * @return The response if found, null otherwise
     */
    ChatResponse getResponse(String responseId);
    
    /**
     * Get a message by ID
     * @param messageId The message ID
     * @return The message if found, null otherwise
     */
    ChatMessage getMessage(String messageId);
    
    /**
     * Get all messages for a chat, ordered by timestamp (newest first)
     * @param chatId The chat ID
     * @param pageable Pagination information
     * @return Page of chat messages
     */
    Page<ChatMessage> getMessages(String chatId, Pageable pageable);
}
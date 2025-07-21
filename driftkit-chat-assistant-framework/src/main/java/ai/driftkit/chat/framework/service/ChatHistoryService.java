package ai.driftkit.chat.framework.service;

import ai.driftkit.chat.framework.model.ChatDomain.ChatRequest;
import ai.driftkit.chat.framework.model.ChatDomain.ChatResponse;

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
     * Get the chat history for a specific chat ID
     * @param chatId The chat ID
     * @return List of chat messages (requests and responses)
     */
    // List<ChatMessage> getChatHistory(String chatId);
}
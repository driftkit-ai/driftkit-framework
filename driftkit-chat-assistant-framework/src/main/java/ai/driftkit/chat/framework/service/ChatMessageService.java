package ai.driftkit.chat.framework.service;

import ai.driftkit.chat.framework.model.ChatDomain.ChatMessage;
import ai.driftkit.chat.framework.model.ChatDomain.ChatRequest;
import ai.driftkit.chat.framework.model.ChatDomain.ChatResponse;
import ai.driftkit.chat.framework.model.ChatDomain.MessageType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service interface for managing chat messages.
 * Implementation should be provided by the consuming application.
 */
public interface ChatMessageService {
    
    /**
     * Add a message to the chat
     * @param chatId The chat ID
     * @param message The message content
     * @param type The message type
     */
    void addMessage(String chatId, String message, MessageType type);
    
    /**
     * Add a user message to the chat
     * @param chatId The chat ID
     * @param message The message content
     */
    default void addUserMessage(String chatId, String message) {
        addMessage(chatId, message, MessageType.USER);
    }
    
    /**
     * Add an AI message to the chat
     * @param chatId The chat ID
     * @param message The message content
     */
    default void addAIMessage(String chatId, String message) {
        addMessage(chatId, message, MessageType.AI);
    }
    
    /**
     * Add a context message to the chat
     * @param chatId The chat ID
     * @param message The message content
     */
    default void addContextMessage(String chatId, String message) {
        addMessage(chatId, message, MessageType.CONTEXT);
    }
    
    /**
     * Add a chat request to the history
     * @param chatId The chat ID
     * @param request The request to add
     * @return The saved request
     */
    ChatRequest addRequest(String chatId, ChatRequest request);
    
    /**
     * Add a chat response to the history
     * @param chatId The chat ID
     * @param response The response to add
     * @return The saved response
     */
    ChatResponse addResponse(String chatId, ChatResponse response);
    
    /**
     * Update an existing chat response
     * @param response The response to update
     * @return The updated response
     */
    ChatResponse updateResponse(ChatResponse response);
    
    /**
     * Get chat history for a session
     * @param chatId The chat ID
     * @param pageable Pagination information
     * @return Page of chat messages
     */
    Page<ChatMessage> getHistory(String chatId, Pageable pageable);
}
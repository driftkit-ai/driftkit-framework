package ai.driftkit.chat.framework.service;

import ai.driftkit.chat.framework.model.ChatDomain.MessageType;

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
}
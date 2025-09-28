package ai.driftkit.clients.util;

import ai.driftkit.common.domain.chat.ChatMessage;
import ai.driftkit.common.domain.client.ModelContentMessage;
import ai.driftkit.common.domain.client.Role;
import ai.driftkit.common.service.ChatStore;
import ai.driftkit.common.utils.JsonUtils;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility methods for ChatStore operations and conversions to model API format.
 */
public class ChatStoreUtils {
    
    /**
     * Convert ChatMessages to ModelContentMessages for LLM API calls.
     */
    public static List<ModelContentMessage> toModelMessages(List<ChatMessage> messages) {
        return messages.stream()
            .map(ChatStoreUtils::toModelMessage)
            .collect(Collectors.toList());
    }
    
    /**
     * Convert a single ChatMessage to ModelContentMessage.
     */
    @SneakyThrows
    public static ModelContentMessage toModelMessage(ChatMessage message) {
        Role role = switch (message.getType()) {
            case USER -> Role.user;
            case AI -> Role.assistant;
            case SYSTEM -> Role.system;
            case CONTEXT -> Role.system; // Context messages are system messages
        };
        
        // Get content from properties
        String content = message.getPropertiesMap().get(ChatMessage.PROPERTY_MESSAGE);
        if (StringUtils.isBlank(content)) {
            // If no "message" property, use JSON representation of all properties
            content = JsonUtils.toJson(message.getPropertiesMap());
        }
        
        return ModelContentMessage.create(role, content);
    }
    
    /**
     * Get messages from ChatStore and convert to model format.
     */
    public static List<ModelContentMessage> getModelMessages(ChatStore chatStore, String chatId) {
        List<ChatMessage> messages = chatStore.getRecent(chatId);
        return toModelMessages(messages);
    }
    
    /**
     * Get messages within token limit and convert to model format.
     */
    public static List<ModelContentMessage> getModelMessages(ChatStore chatStore, String chatId, int maxTokens) {
        List<ChatMessage> messages = chatStore.getRecentWithinTokens(chatId, maxTokens);
        return toModelMessages(messages);
    }
}
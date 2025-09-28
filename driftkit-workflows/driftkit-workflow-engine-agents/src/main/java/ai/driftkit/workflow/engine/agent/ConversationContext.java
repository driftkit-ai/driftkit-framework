package ai.driftkit.workflow.engine.agent;

import ai.driftkit.common.domain.chat.ChatMessage;
import ai.driftkit.common.domain.chat.ChatMessage.MessageType;
import ai.driftkit.common.domain.chat.ChatRequest;
import ai.driftkit.common.domain.chat.ChatResponse;
import ai.driftkit.common.domain.client.ModelMessage;
import ai.driftkit.common.domain.client.Role;
import ai.driftkit.common.service.ChatStore;
import ai.driftkit.common.utils.JsonUtils;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Unified conversation context that handles both history modes:
 * - WITH_HISTORY: When chatStore exists and workflowId is blank
 * - STATELESS: When chatStore is null or workflowId is not blank
 */
@Slf4j
@Getter
@Builder
public class ConversationContext {
    
    private final ChatStore chatStore;
    private final String workflowId;
    private final String chatId;

    @Builder.Default
    private final List<ModelMessage> messages = new ArrayList<>();
    
    private final boolean historyMode;
    
    /**
     * Create context from LLMAgent state
     */
    public static ConversationContext from(ChatStore chatStore, 
                                          String workflowId,
                                          String sessionId) {
        return from(chatStore, workflowId, sessionId, 4096);
    }
    
    /**
     * Create context from LLMAgent state with custom token limit
     */
    public static ConversationContext from(ChatStore chatStore, 
                                          String workflowId,
                                          String chatId,
                                          Integer maxTokens) {
        boolean historyMode = chatStore != null && StringUtils.isBlank(workflowId);
        
        ConversationContextBuilder builder = ConversationContext.builder()
            .chatStore(chatStore)
            .workflowId(workflowId)
            .chatId(chatId)
            .historyMode(historyMode);
            
        // Load existing history if in history mode
        if (historyMode) {
            // Use ChatStore's getRecentWithinTokens to get proper context window
            List<ChatMessage> recentHistory = maxTokens == null ? chatStore.getRecent(chatId) : chatStore.getRecentWithinTokens(chatId, maxTokens);
            
            // Convert to ModelMessage format for API
            List<ModelMessage> recentMessages = convertToModelMessages(recentHistory);
            builder.messages(recentMessages);
        }
        
        return builder.build();
    }
    
    /**
     * Add user message to context
     */
    public void addUserMessage(String content) {
        log.debug("Adding user message: {}", content);
        
        // Always add to current messages for API request
        ModelMessage modelMessage = ModelMessage.user(content);
        messages.add(modelMessage);
        
        // Save to history if in history mode
        if (historyMode && chatStore != null) {
            chatStore.add(chatId, content, MessageType.USER);
        }
    }
    
    /**
     * Add assistant message to context
     */
    public void addAssistantMessage(String content) {
        log.debug("Adding assistant message: {}", content);
        
        // Always add to current messages
        ModelMessage modelMessage = ModelMessage.assistant(content);
        messages.add(modelMessage);
        
        // Save to history if in history mode
        if (historyMode && chatStore != null) {
            chatStore.add(chatId, content, MessageType.AI);
        }
    }
    
    /**
     * Add system message to context
     */
    public void addSystemMessage(String content) {
        log.debug("Adding system message: {}", content);
        
        // System messages typically go at the beginning
        ModelMessage modelMessage = ModelMessage.system(content);
        messages.add(0, modelMessage);
    }
    
    /**
     * Add tool result to context
     */
    public void addToolResult(String toolName, Object result) {
        String content = String.format("Tool '%s' returned: %s", toolName, result);
        
        // Add as assistant message
        addAssistantMessage(content);
    }
    
    /**
     * Get messages for API request
     */
    public List<ModelMessage> getMessagesForRequest() {
        // Return a copy to prevent external modification
        return new ArrayList<>(messages);
    }
    
    /**
     * Get full conversation history from store
     */
    public List<ChatMessage> getFullHistory() {
        if (historyMode && chatStore != null) {
            // Get all history from store
            return chatStore.getAll(chatId);
        }
        // In stateless mode, convert current messages to ChatMessage format
        return convertToChatMessages(messages);
    }
    
    /**
     * Clear current messages (useful for resetting between requests)
     */
    public void clearMessages() {
        messages.clear();
    }
    
    // Utility methods
    
    private static List<ModelMessage> convertToModelMessages(List<ChatMessage> chatMessages) {
        return chatMessages.stream()
            .map(cm -> {
                MessageType type = cm.getType();
                String content = extractMessageContent(cm);
                
                if (type == MessageType.USER) {
                    return ModelMessage.user(content);
                } else if (type == MessageType.AI) {
                    return ModelMessage.assistant(content);
                } else if (type == MessageType.SYSTEM) {
                    return ModelMessage.system(content);
                } else {
                    // CONTEXT or unknown - treat as system
                    return ModelMessage.system(content);
                }
            })
            .collect(Collectors.toList());
    }
    
    private static String extractMessageContent(ChatMessage cm) {
        // Try to get message from ChatRequest
        if (cm instanceof ChatRequest) {
            String message = ((ChatRequest) cm).getMessage();
            if (StringUtils.isNotBlank(message)) {
                return message;
            }
        }
        
        // Try to get from properties map
        Map<String, String> propsMap = cm.getPropertiesMap();
        if (propsMap != null && !propsMap.isEmpty()) {
            // Look for message property
            String message = propsMap.get(ChatMessage.PROPERTY_MESSAGE);
            if (StringUtils.isNotBlank(message)) {
                return message;
            }
            
            // If no message property, convert all properties to JSON
            try {
                return JsonUtils.toJson(propsMap);
            } catch (Exception e) {
                log.warn("Failed to convert properties to JSON", e);
                return propsMap.toString();
            }
        }
        
        return "";
    }
    
    private static List<ChatMessage> convertToChatMessages(List<ModelMessage> modelMessages) {
        return modelMessages.stream()
            .map(mm -> {
                Role role = mm.getRole();
                MessageType messageType;
                
                if (role == Role.user) {
                    messageType = MessageType.USER;
                } else if (role == Role.assistant) {
                    messageType = MessageType.AI;
                } else if (role == Role.system) {
                    messageType = MessageType.SYSTEM;
                } else {
                    throw new IllegalArgumentException("Unknown role: " + role);
                }
                
                ChatMessage msg;
                if (messageType == MessageType.USER) {
                    msg = new ChatRequest();
                } else if (messageType == MessageType.AI) {
                    msg = new ChatResponse();
                } else {
                    msg = new ChatMessage();
                }
                
                msg.setType(messageType);
                msg.setTimestamp(System.currentTimeMillis());
                
                // Add content as property
                ChatMessage.DataProperty prop = new ChatMessage.DataProperty(
                    ChatMessage.PROPERTY_MESSAGE, 
                    mm.getContent(), 
                    ChatMessage.PropertyType.STRING
                );
                msg.getProperties().add(prop);
                
                return msg;
            })
            .collect(Collectors.toList());
    }
}
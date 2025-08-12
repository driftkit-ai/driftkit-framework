package ai.driftkit.workflow.engine.memory;

import ai.driftkit.common.domain.ChatMessageType;
import ai.driftkit.common.domain.Message;
import ai.driftkit.common.domain.MessageType;
import ai.driftkit.common.service.ChatMemoryStore;
import ai.driftkit.common.utils.JsonUtils;
import ai.driftkit.workflow.engine.chat.ChatDomain;
import ai.driftkit.workflow.engine.chat.ChatDomain.ChatMessage;
import ai.driftkit.workflow.engine.chat.ChatDomain.ChatRequest;
import ai.driftkit.workflow.engine.chat.ChatDomain.ChatResponse;
import ai.driftkit.workflow.engine.persistence.ChatHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Adapter that implements driftkit-common ChatMemoryStore interface
 * using workflow engine's ChatHistoryRepository.
 * 
 * This allows us to use TokenWindowChatMemory with automatic token-based
 * memory management on top of our existing chat history storage.
 */
@Slf4j
@RequiredArgsConstructor
public class ChatHistoryMemoryStoreAdapter implements ChatMemoryStore {
    
    private final ChatHistoryRepository chatHistoryRepository;
    
    @Override
    public List<Message> getMessages(String id, int limit) {
        List<ChatMessage> chatMessages = chatHistoryRepository.findRecentByChatId(id, limit);
        return chatMessages.stream()
                .map(this::convertToMessage)
                .collect(Collectors.toList());
    }
    
    @Override
    public void updateMessages(String id, List<Message> messages) {
        // Clear existing messages and replace with new ones
        chatHistoryRepository.deleteByChatId(id);
        
        // Convert and add all messages
        List<ChatMessage> chatMessages = messages.stream()
                .map(msg -> convertToChatMessage(id, msg))
                .collect(Collectors.toList());
        
        chatHistoryRepository.addMessages(id, chatMessages);
    }
    
    @Override
    public void deleteMessages(String id) {
        chatHistoryRepository.deleteByChatId(id);
    }
    
    /**
     * Convert workflow ChatMessage to common Message.
     */
    @SneakyThrows
    private Message convertToMessage(ChatMessage chatMessage) {
        ChatMessageType type = convertMessageTypeToCommonType(chatMessage.getType());
        
        // Get message text from properties as JSON
        String messageText = null;
        if (!chatMessage.getProperties().isEmpty()) {
            messageText = JsonUtils.toJson(chatMessage.getPropertiesMap());
        }
        
        return Message.builder()
                .messageId(chatMessage.getId())
                .message(StringUtils.defaultIfEmpty(messageText, ""))
                .type(type)
                .messageType(MessageType.TEXT)
                .createdTime(chatMessage.getTimestamp() != null ? chatMessage.getTimestamp() : System.currentTimeMillis())
                .requestInitTime(chatMessage.getTimestamp() != null ? chatMessage.getTimestamp() : System.currentTimeMillis())
                .build();
    }
    
    /**
     * Convert common Message to workflow ChatMessage.
     */
    @SneakyThrows
    private ChatMessage convertToChatMessage(String chatId, Message message) {
        ChatDomain.MessageType type = convertCommonTypeToMessageType(message.getType());
        
        // Create appropriate subclass based on type
        ChatMessage chatMessage;
        if (type == ChatDomain.MessageType.USER) {
            chatMessage = new ChatRequest();
        } else {
            chatMessage = new ChatResponse();
        }
        
        chatMessage.setId(StringUtils.defaultIfEmpty(message.getMessageId(), UUID.randomUUID().toString()));
        chatMessage.setChatId(chatId);
        chatMessage.setType(type);
        chatMessage.setTimestamp(message.getCreatedTime());
        
        // Try to parse JSON back to properties if the message looks like JSON
        String content = message.getMessage();
        if (JsonUtils.isJSON(content)) {
            try {
                Map<String, String> properties = JsonUtils.fromJson(content, Map.class);
                chatMessage.setPropertiesMap(properties);
            } catch (Exception e) {
                // Not valid JSON, store as "message" property
                chatMessage.updateOrAddProperty("message", content);
            }
        } else {
            chatMessage.updateOrAddProperty("message", content);
        }
        
        return chatMessage;
    }
    
    /**
     * Convert ChatDomain.MessageType to ChatMessageType.
     */
    private ChatMessageType convertMessageTypeToCommonType(ChatDomain.MessageType type) {
        if (type == null) {
            return ChatMessageType.USER;
        }
        switch (type) {
            case USER:
                return ChatMessageType.USER;
            case AI:
                return ChatMessageType.AI;
            case SYSTEM:
                return ChatMessageType.SYSTEM;
            default:
                log.warn("Unknown type: {}, defaulting to USER", type);
                return ChatMessageType.USER;
        }
    }
    
    /**
     * Convert ChatMessageType to ChatDomain.MessageType.
     */
    private ChatDomain.MessageType convertCommonTypeToMessageType(ChatMessageType type) {
        if (type == null) {
            return ChatDomain.MessageType.USER;
        }
        switch (type) {
            case USER:
                return ChatDomain.MessageType.USER;
            case AI:
                return ChatDomain.MessageType.AI;
            case SYSTEM:
                return ChatDomain.MessageType.SYSTEM;
            default:
                log.warn("Unknown type: {}, defaulting to USER", type);
                return ChatDomain.MessageType.USER;
        }
    }
}
package ai.driftkit.common.service.impl;

import ai.driftkit.common.domain.chat.ChatMessage;
import ai.driftkit.common.domain.chat.ChatMessage.MessageType;
import ai.driftkit.common.domain.chat.ChatRequest;
import ai.driftkit.common.domain.chat.ChatResponse;
import ai.driftkit.common.service.ChatStore;
import ai.driftkit.common.service.TextTokenizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of ChatStore.
 * Thread-safe implementation using ConcurrentHashMap.
 */
@Slf4j
@RequiredArgsConstructor
public class InMemoryChatStore implements ChatStore {
    
    private final TextTokenizer tokenizer;
    private final int defaultMaxTokens;
    
    // Storage: chatId -> list of messages
    private final Map<String, List<ChatMessage>> storage = new ConcurrentHashMap<>();
    
    // Message index: messageId -> message
    private final Map<String, ChatMessage> messageIndex = new ConcurrentHashMap<>();
    
    public InMemoryChatStore(TextTokenizer tokenizer) {
        this(tokenizer, 4096);
    }
    
    @Override
    public void add(String chatId, String content, MessageType type) {
        ChatMessage message = createMessage(chatId, type);
        message.updateOrAddProperty("message", content);
        add(message);
    }
    
    @Override
    public void add(String chatId, Map<String, String> properties, MessageType type) {
        ChatMessage message = createMessage(chatId, type);
        message.setPropertiesMap(properties);
        add(message);
    }
    
    @Override
    public void add(ChatMessage message) {
        if (message == null || message.getChatId() == null) {
            throw new IllegalArgumentException("Message and chatId cannot be null");
        }
        
        // Ensure message has an ID
        if (message.getId() == null) {
            message.setId(UUID.randomUUID().toString());
        }
        
        // Ensure timestamp
        if (message.getTimestamp() == null) {
            message.setTimestamp(System.currentTimeMillis());
        }
        
        // Add to storage
        storage.computeIfAbsent(message.getChatId(), k -> Collections.synchronizedList(new ArrayList<>()))
               .add(message);
        
        // Add to index
        messageIndex.put(message.getId(), message);
        
        log.debug("Added message {} to chat {}", message.getId(), message.getChatId());
    }
    
    @Override
    public void update(ChatMessage message) {
        if (message == null || message.getId() == null) {
            throw new IllegalArgumentException("Message and messageId cannot be null");
        }
        
        ChatMessage existing = messageIndex.get(message.getId());
        if (existing == null) {
            throw new IllegalArgumentException("Message not found: " + message.getId());
        }
        
        // Update in index
        messageIndex.put(message.getId(), message);
        
        // Update in storage
        List<ChatMessage> messages = storage.get(message.getChatId());
        if (messages != null) {
            for (int i = 0; i < messages.size(); i++) {
                if (messages.get(i).getId().equals(message.getId())) {
                    messages.set(i, message);
                    break;
                }
            }
        }
        
        log.debug("Updated message {} in chat {}", message.getId(), message.getChatId());
    }
    
    @Override
    public List<ChatMessage> getRecentWithinTokens(String chatId, int maxTokens) {
        List<ChatMessage> allMessages = getAll(chatId);
        if (allMessages.isEmpty()) {
            return allMessages;
        }
        
        List<ChatMessage> result = new ArrayList<>();
        int totalTokens = 0;
        
        // Iterate from most recent to oldest
        for (int i = allMessages.size() - 1; i >= 0; i--) {
            ChatMessage message = allMessages.get(i);
            int messageTokens = estimateTokens(message);
            
            if (totalTokens + messageTokens > maxTokens) {
                break;
            }
            
            result.add(0, message); // Add to beginning to maintain order
            totalTokens += messageTokens;
        }
        
        return result;
    }
    
    @Override
    public List<ChatMessage> getRecent(String chatId) {
        return getRecentWithinTokens(chatId, defaultMaxTokens);
    }
    
    @Override
    public List<ChatMessage> getRecent(String chatId, int limit) {
        List<ChatMessage> allMessages = getAll(chatId);
        if (allMessages.size() <= limit) {
            return new ArrayList<>(allMessages);
        }
        
        // Return last 'limit' messages
        return new ArrayList<>(allMessages.subList(allMessages.size() - limit, allMessages.size()));
    }
    
    @Override
    public List<ChatMessage> getAll(String chatId) {
        List<ChatMessage> messages = storage.get(chatId);
        return messages != null ? new ArrayList<>(messages) : new ArrayList<>();
    }
    
    @Override
    public void delete(String messageId) {
        ChatMessage message = messageIndex.remove(messageId);
        if (message != null) {
            List<ChatMessage> messages = storage.get(message.getChatId());
            if (messages != null) {
                messages.removeIf(m -> messageId.equals(m.getId()));
            }
            log.debug("Deleted message {}", messageId);
        }
    }
    
    @Override
    public void deleteAll(String chatId) {
        List<ChatMessage> messages = storage.remove(chatId);
        if (messages != null) {
            messages.forEach(m -> messageIndex.remove(m.getId()));
            log.debug("Deleted all {} messages from chat {}", messages.size(), chatId);
        }
    }
    
    @Override
    public int getTotalTokens(String chatId) {
        return getAll(chatId).stream()
                .mapToInt(this::estimateTokens)
                .sum();
    }
    
    @Override
    public boolean chatExists(String chatId) {
        return storage.containsKey(chatId) && !storage.get(chatId).isEmpty();
    }
    
    @Override
    public ChatMessage getById(String messageId) {
        return messageIndex.get(messageId);
    }
    
    private int estimateTokens(ChatMessage message) {
        // Get all text content from properties
        String content = message.getPropertiesMap().values().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
        
        return tokenizer.estimateTokens(content);
    }
    
    private ChatMessage createMessage(String chatId, MessageType type) {
        switch (type) {
            case USER:
                ChatRequest request = new ChatRequest();
                request.setId(UUID.randomUUID().toString());
                request.setChatId(chatId);
                request.setType(type);
                request.setTimestamp(System.currentTimeMillis());
                return request;
                
            case AI:
                ChatResponse response = new ChatResponse();
                response.setId(UUID.randomUUID().toString());
                response.setChatId(chatId);
                response.setType(MessageType.AI); // Always use AI for responses
                response.setTimestamp(System.currentTimeMillis());
                return response;
                
            default:
                ChatMessage message = new ChatMessage();
                message.setId(UUID.randomUUID().toString());
                message.setChatId(chatId);
                message.setType(type);
                message.setTimestamp(System.currentTimeMillis());
                return message;
        }
    }
}
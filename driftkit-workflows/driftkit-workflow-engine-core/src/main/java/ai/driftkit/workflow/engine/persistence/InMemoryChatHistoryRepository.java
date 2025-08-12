package ai.driftkit.workflow.engine.persistence;

import ai.driftkit.workflow.engine.chat.ChatDomain.ChatMessage;
import ai.driftkit.workflow.engine.chat.ChatDomain.MessageType;
import ai.driftkit.workflow.engine.domain.PageRequest;
import ai.driftkit.workflow.engine.domain.PageResult;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of ChatHistoryRepository.
 * Suitable for development and testing, not for production use.
 */
@Slf4j
public class InMemoryChatHistoryRepository implements ChatHistoryRepository {
    
    private final Map<String, List<ChatMessage>> chatHistories = new ConcurrentHashMap<>();
    
    @Override
    public void addMessage(String chatId, ChatMessage message) {
        List<ChatMessage> history = chatHistories.computeIfAbsent(chatId, k -> new ArrayList<>());
        synchronized (history) {
            history.add(message);
        }
        log.debug("Added message to chat {}: type={}", chatId, message.getType());
    }
    
    @Override
    public void addMessages(String chatId, List<ChatMessage> messages) {
        List<ChatMessage> history = chatHistories.computeIfAbsent(chatId, k -> new ArrayList<>());
        synchronized (history) {
            history.addAll(messages);
        }
        log.debug("Added {} messages to chat {}", messages.size(), chatId);
    }
    
    @Override
    public PageResult<ChatMessage> findByChatId(String chatId, PageRequest pageRequest, boolean includeContext) {
        List<ChatMessage> history = chatHistories.getOrDefault(chatId, new ArrayList<>());
        
        List<ChatMessage> filteredHistory = history.stream()
                .filter(msg -> includeContext || msg.getType() != MessageType.CONTEXT)
                .sorted(getMessageComparator(pageRequest))
                .collect(Collectors.toList());
        
        return createPageResult(filteredHistory, pageRequest);
    }
    
    @Override
    public List<ChatMessage> findAllByChatId(String chatId) {
        List<ChatMessage> history = chatHistories.getOrDefault(chatId, new ArrayList<>());
        return new ArrayList<>(history);
    }
    
    @Override
    public List<ChatMessage> findRecentByChatId(String chatId, int limit) {
        List<ChatMessage> history = chatHistories.getOrDefault(chatId, new ArrayList<>());
        
        List<ChatMessage> sortedHistory = history.stream()
                .sorted((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()))
                .limit(limit)
                .collect(Collectors.toList());
        
        // Reverse to get chronological order
        Collections.reverse(sortedHistory);
        return sortedHistory;
    }
    
    @Override
    public void deleteByChatId(String chatId) {
        chatHistories.remove(chatId);
        log.debug("Deleted chat history for: {}", chatId);
    }
    
    @Override
    public long countByChatId(String chatId) {
        List<ChatMessage> history = chatHistories.getOrDefault(chatId, new ArrayList<>());
        return history.size();
    }
    
    private Comparator<ChatMessage> getMessageComparator(PageRequest pageRequest) {
        Comparator<ChatMessage> comparator = switch (pageRequest.getSortBy()) {
            case "timestamp" -> Comparator.comparing(ChatMessage::getTimestamp);
            case "type" -> Comparator.comparing(msg -> msg.getType().toString());
            case "id" -> Comparator.comparing(ChatMessage::getId);
            default -> Comparator.comparing(ChatMessage::getTimestamp);
        };
        
        return pageRequest.getSortDirection() == PageRequest.SortDirection.DESC 
                ? comparator.reversed() 
                : comparator;
    }
    
    private PageResult<ChatMessage> createPageResult(List<ChatMessage> messages, PageRequest pageRequest) {
        int start = (int) pageRequest.getOffset();
        int end = Math.min((start + pageRequest.getPageSize()), messages.size());
        
        if (start >= messages.size()) {
            return PageResult.empty(pageRequest.getPageNumber(), pageRequest.getPageSize());
        }
        
        List<ChatMessage> pageContent = messages.subList(start, end);
        return PageResult.<ChatMessage>builder()
                .content(pageContent)
                .pageNumber(pageRequest.getPageNumber())
                .pageSize(pageRequest.getPageSize())
                .totalElements(messages.size())
                .build();
    }
}
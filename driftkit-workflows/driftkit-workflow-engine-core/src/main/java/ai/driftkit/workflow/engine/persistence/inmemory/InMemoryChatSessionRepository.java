package ai.driftkit.workflow.engine.persistence.inmemory;

import ai.driftkit.workflow.engine.domain.ChatSession;
import ai.driftkit.workflow.engine.domain.PageRequest;
import ai.driftkit.workflow.engine.domain.PageResult;
import ai.driftkit.workflow.engine.persistence.ChatSessionRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of ChatSessionRepository.
 * Suitable for development and testing, not for production use.
 */
@Slf4j
public class InMemoryChatSessionRepository implements ChatSessionRepository {
    
    private final Map<String, ChatSession> sessions = new ConcurrentHashMap<>();
    
    @Override
    public ChatSession save(ChatSession session) {
        sessions.put(session.getChatId(), session);
        log.debug("Saved chat session: {}", session.getChatId());
        return session;
    }
    
    @Override
    public Optional<ChatSession> findById(String chatId) {
        return Optional.ofNullable(sessions.get(chatId));
    }
    
    @Override
    public PageResult<ChatSession> findByUserId(String userId, PageRequest pageRequest) {
        List<ChatSession> userSessions = sessions.values().stream()
                .filter(session -> userId.equals(session.getUserId()))
                .sorted(getSortComparator(pageRequest))
                .collect(Collectors.toList());
        
        return createPageResult(userSessions, pageRequest);
    }
    
    @Override
    public PageResult<ChatSession> findActiveByUserId(String userId, PageRequest pageRequest) {
        List<ChatSession> activeSessions = sessions.values().stream()
                .filter(session -> userId.equals(session.getUserId()))
                .filter(session -> !session.isArchived())
                .sorted(getSortComparator(pageRequest))
                .collect(Collectors.toList());
        
        return createPageResult(activeSessions, pageRequest);
    }
    
    @Override
    public void deleteById(String chatId) {
        sessions.remove(chatId);
        log.debug("Deleted chat session: {}", chatId);
    }
    
    @Override
    public boolean existsById(String chatId) {
        return sessions.containsKey(chatId);
    }
    
    private Comparator<ChatSession> getSortComparator(PageRequest pageRequest) {
        Comparator<ChatSession> comparator = switch (pageRequest.getSortBy()) {
            case "lastMessageTime" -> Comparator.comparing(ChatSession::getLastMessageTime);
            case "createdAt" -> Comparator.comparing(ChatSession::getCreatedAt);
            case "name" -> Comparator.comparing(ChatSession::getName, String.CASE_INSENSITIVE_ORDER);
            default -> Comparator.comparing(ChatSession::getChatId);
        };
        
        return pageRequest.getSortDirection() == PageRequest.SortDirection.DESC 
                ? comparator.reversed() 
                : comparator;
    }
    
    private PageResult<ChatSession> createPageResult(List<ChatSession> sessions, PageRequest pageRequest) {
        int start = (int) pageRequest.getOffset();
        int end = Math.min((start + pageRequest.getPageSize()), sessions.size());
        
        if (start >= sessions.size()) {
            return PageResult.empty(pageRequest.getPageNumber(), pageRequest.getPageSize());
        }
        
        List<ChatSession> pageContent = sessions.subList(start, end);
        return PageResult.<ChatSession>builder()
                .content(pageContent)
                .pageNumber(pageRequest.getPageNumber())
                .pageSize(pageRequest.getPageSize())
                .totalElements(sessions.size())
                .build();
    }
}
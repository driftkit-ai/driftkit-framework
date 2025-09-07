package ai.driftkit.workflow.engine.spring.dto;

import ai.driftkit.workflow.engine.domain.ChatSession;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Pageable response wrapper for chat sessions.
 * Compatible with legacy AssistantController response format.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PageableResponseWithChat {
    
    private List<ChatInfo> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
    private String sort;
    private int numberOfElements;
    private boolean empty;
    
    public PageableResponseWithChat(HttpServletRequest request, Page<ChatSession> page) {
        this.content = page.getContent().stream()
                .map(session -> new ChatInfo(
                        session.getChatId(),
                        session.getLastMessageTime(),
                        session.getDescription(),
                        session.getUserId(),
                        session.getName()
                ))
                .collect(Collectors.toList());
        
        this.page = page.getNumber();
        this.size = page.getSize();
        this.totalElements = page.getTotalElements();
        this.totalPages = page.getTotalPages();
        this.first = page.isFirst();
        this.last = page.isLast();
        this.sort = page.getSort().toString();
        this.numberOfElements = page.getNumberOfElements();
        this.empty = page.isEmpty();
    }
    
    /**
     * Chat information DTO
     */
    @Data
    public static class ChatInfo {
        private String chatId;
        private Long lastMessageTime;
        private String lastMessage;
        private String userId;
        private String name;
        
        public ChatInfo(String chatId, Long lastMessageTime, String lastMessage, 
                       String userId, String name) {
            this.chatId = chatId;
            this.lastMessageTime = lastMessageTime;
            this.lastMessage = lastMessage;
            this.userId = userId;
            this.name = name;
        }
    }
}
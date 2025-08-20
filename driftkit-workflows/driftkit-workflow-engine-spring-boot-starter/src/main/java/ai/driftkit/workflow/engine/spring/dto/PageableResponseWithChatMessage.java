package ai.driftkit.workflow.engine.spring.dto;

import ai.driftkit.common.domain.chat.ChatMessage;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Pageable response wrapper for chat messages.
 * Compatible with legacy AssistantController response format.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PageableResponseWithChatMessage {
    
    private List<ChatMessage> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
    private String sort;
    private int numberOfElements;
    private boolean empty;
    
    public PageableResponseWithChatMessage(HttpServletRequest request, Page<ChatMessage> page) {
        this.content = page.getContent();
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
}
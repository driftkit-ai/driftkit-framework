package ai.driftkit.workflows.spring.domain;

import ai.driftkit.common.domain.Chat;
import ai.driftkit.common.domain.Language;
import ai.driftkit.common.domain.ModelRole;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * MongoDB entity wrapper for Chat with proper @Id annotation
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "chats")
public class ChatEntity extends Chat {
    
    @Override
    @Id
    public String getChatId() {
        return super.getChatId();
    }
    
    /**
     * Create entity from domain object
     */
    public static ChatEntity fromChat(Chat chat) {
        ChatEntity entity = new ChatEntity();
        entity.setChatId(chat.getChatId());
        entity.setName(chat.getName());
        entity.setSystemMessage(chat.getSystemMessage());
        entity.setLanguage(chat.getLanguage());
        entity.setMemoryLength(chat.getMemoryLength());
        entity.setModelRole(chat.getModelRole());
        entity.setCreatedTime(chat.getCreatedTime());
        entity.setHidden(chat.isHidden());
        return entity;
    }
    
    /**
     * Convert to domain object
     */
    public Chat toChat() {
        return Chat.builder()
                .chatId(this.getChatId())
                .name(this.getName())
                .systemMessage(this.getSystemMessage())
                .language(this.getLanguage())
                .memoryLength(this.getMemoryLength())
                .modelRole(this.getModelRole())
                .createdTime(this.getCreatedTime())
                .hidden(this.isHidden())
                .build();
    }
}
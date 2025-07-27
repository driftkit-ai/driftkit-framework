package ai.driftkit.chat.framework.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a chat session.
 * Stores metadata about chat sessions for display in chat list.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("chat_sessions")
public class ChatSession {
    /**
     * Unique identifier for the chat
     */
    @Id
    private String chatId;
    
    /**
     * Name of the chat (for display purposes)
     */
    private String name;
    
    /**
     * Description or snippet of the last message
     */
    private String description;
    
    /**
     * User ID of the chat owner
     */
    private String userId;
    
    /**
     * Timestamp of the last message
     */
    private Long lastMessageTime;
    
    /**
     * Workflow ID of the current workflow
     */
    private String workflowId;
    
    /**
     * Additional properties for the chat
     */
    @Builder.Default
    private Map<String, String> properties = new HashMap<>();
    
    /**
     * Creation timestamp
     */
    private Long createdTime;
    
    /**
     * Last updated timestamp
     */
    private Long updatedTime;
    
    /**
     * Whether the chat is archived
     */
    @Builder.Default
    private boolean archived = false;
}
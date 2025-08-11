package ai.driftkit.workflow.engine.domain;

import ai.driftkit.workflow.engine.chat.ChatContextHelper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSession {
    private String chatId;
    private String userId;
    private String name;
    private String description;
    private long createdAt;
    private long lastMessageTime;
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
    @Builder.Default
    private boolean archived = false;

    public static ChatSession create(String chatId, String userId, String name) {
        long now = System.currentTimeMillis();
        return ChatSession.builder()
                .chatId(chatId)
                .userId(userId)
                .name(name)
                .createdAt(now)
                .lastMessageTime(now)
                .build();
    }

    public ChatSession withLastMessageTime(Long time) {
        this.lastMessageTime = time;
        return this;
    }

    public ChatSession archive() {
        this.archived = true;
        return this;
    }
}

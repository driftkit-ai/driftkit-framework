package ai.driftkit.chat.framework.ai.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonInclude(Include.NON_NULL)
public class HistoryMessage {
    String messageId;
    String message;
    ChatMessageType type;
    MessageType messageType = MessageType.TEXT;
    String imageTaskId;
    String workflow;
    long createdTime;
    Long responseTime;

    @Builder
    public HistoryMessage(
            String messageId,
            String message,
            ChatMessageType type,
            MessageType messageType,
            String imageTaskId,
            String workflow,
            long createdTime,
            Long responseTime
    ) {
        this.messageId = messageId;
        this.message = message;
        this.type = type;
        this.messageType = messageType;
        this.imageTaskId = imageTaskId;
        this.workflow = workflow;
        this.createdTime = createdTime;
        this.responseTime = responseTime;
    }

    public enum MessageType {
        IMAGE,
        TEXT
    }
    
    public enum ChatMessageType {
        SYSTEM,
        USER,
        AI
    }
}
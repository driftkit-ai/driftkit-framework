package ai.driftkit.workflows.core.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;

@Data
@NoArgsConstructor
@JsonInclude(Include.NON_NULL)
public class Message {

    @Builder
    public Message(
            String messageId,
            String message,
            ChatMessageType type,
            MessageType messageType,
            String imageTaskId,
            Grade grade,
            String gradeComment,
            String workflow,
            String context,
            long createdTime,
            long requestInitTime,
            Long responseTime) {
        this.messageId = messageId;
        this.message = message;
        this.type = type;
        this.messageType = messageType;
        this.imageTaskId = imageTaskId;
        this.grade = grade;
        this.gradeComment = gradeComment;
        this.workflow = workflow;
        this.context = context;
        this.createdTime = createdTime;
        this.requestInitTime = requestInitTime;
        this.responseTime = responseTime;
    }
    
    @NotNull
    String messageId;
    
    @NotNull
    String message;
    
    @NotNull
    ChatMessageType type;
    
    @NotNull
    MessageType messageType = MessageType.TEXT;

    String imageTaskId;

    Grade grade;
    
    String gradeComment;

    String workflow;
    
    String context;

    @NotNull
    long createdTime;

    @NotNull
    long requestInitTime;

    Long responseTime;

    public ChatMessageType type() {
        return type;
    }

    public String text() {
        return message;
    }
}
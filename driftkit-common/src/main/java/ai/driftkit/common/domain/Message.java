package ai.driftkit.common.domain;

import ai.driftkit.common.domain.client.LogProbs;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;

@Data
@NoArgsConstructor
@JsonInclude(Include.NON_NULL)
public class Message implements ChatItem {

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
        this(messageId, message, type, messageType, imageTaskId, grade, gradeComment, 
             workflow, context, createdTime, requestInitTime, responseTime, null);
    }
    
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
            Long responseTime,
            LogProbs tokenLogprobs) {
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
        this.tokenLogprobs = tokenLogprobs;
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
    
    LogProbs tokenLogprobs;

    @Override
    public ChatMessageType type() {
        return type;
    }

    @Override
    public String text() {
        return message;
    }
}
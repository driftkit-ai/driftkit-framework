package ai.driftkit.workflows.spring.domain;

import ai.driftkit.common.domain.MessageTask;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "messageTasks")
public class MessageTaskEntity extends MessageTask {
    
    @Id
    @Override
    public String getMessageId() {
        return super.getMessageId();
    }
    
    public MessageTaskEntity(MessageTask task) {
        super(
            task.getMessageId(),
            task.getChatId(),
            task.getMessage(),
            task.getSystemMessage(),
            task.getGradeComment(),
            task.getGrade(),
            task.getCreatedTime(),
            task.getResponseTime(),
            task.getModelId(),
            task.getResult(),
            task.getImageTaskId(),
            task.getPromptIds(),
            task.getTemperature(),
            task.getWorkflow(),
            task.getContextJson(),
            task.getLanguage(),
            task.getVariables(),
            task.isJsonRequest(),
            task.isJsonResponse(),
            task.getResponseFormat(),
            task.getWorkflowStopEvent(),
            task.getLogprobs(),
            task.getTopLogprobs(),
            task.getLogProbs(),
            task.getPurpose(),
            task.getImageBase64(),
            task.getImageMimeType()
        );
    }
    
    public static MessageTaskEntity fromMessageTask(MessageTask task) {
        if (task == null) {
            return null;
        }
        if (task instanceof MessageTaskEntity) {
            return (MessageTaskEntity) task;
        }
        return new MessageTaskEntity(task);
    }
    
    public static MessageTask toMessageTask(MessageTaskEntity entity) {
        if (entity == null) {
            return null;
        }
        return MessageTask.builder()
            .messageId(entity.getMessageId())
            .chatId(entity.getChatId())
            .message(entity.getMessage())
            .systemMessage(entity.getSystemMessage())
            .gradeComment(entity.getGradeComment())
            .grade(entity.getGrade())
            .createdTime(entity.getCreatedTime())
            .responseTime(entity.getResponseTime())
            .modelId(entity.getModelId())
            .result(entity.getResult())
            .imageTaskId(entity.getImageTaskId())
            .promptIds(entity.getPromptIds())
            .temperature(entity.getTemperature())
            .workflow(entity.getWorkflow())
            .context(entity.getContextJson())
            .language(entity.getLanguage())
            .variables(entity.getVariables())
            .jsonRequest(entity.isJsonRequest())
            .jsonResponse(entity.isJsonResponse())
            .responseFormat(entity.getResponseFormat())
            .workflowStopEvent(entity.getWorkflowStopEvent())
            .logprobs(entity.getLogprobs())
            .topLogprobs(entity.getTopLogprobs())
            .logProbs(entity.getLogProbs())
            .purpose(entity.getPurpose())
            .imageBase64(entity.getImageBase64())
            .imageMimeType(entity.getImageMimeType())
            .build();
    }
}
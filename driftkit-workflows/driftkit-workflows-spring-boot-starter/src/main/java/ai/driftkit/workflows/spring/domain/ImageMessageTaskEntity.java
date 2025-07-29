package ai.driftkit.workflows.spring.domain;

import ai.driftkit.common.domain.ImageMessageTask;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * MongoDB entity wrapper for ImageMessageTask with proper @Id annotation
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "image_message_tasks")
public class ImageMessageTaskEntity extends ImageMessageTask {
    
    @Override
    @Id
    public String getMessageId() {
        return super.getMessageId();
    }
    
    /**
     * Create entity from domain object
     */
    public static ImageMessageTaskEntity fromImageMessageTask(ImageMessageTask task) {
        ImageMessageTaskEntity entity = new ImageMessageTaskEntity();
        entity.setMessageId(task.getMessageId());
        entity.setChatId(task.getChatId());
        entity.setMessage(task.getMessage());
        entity.setSystemMessage(task.getSystemMessage());
        entity.setGradeComment(task.getGradeComment());
        entity.setGrade(task.getGrade());
        entity.setCreatedTime(task.getCreatedTime());
        entity.setResponseTime(task.getResponseTime());
        entity.setModelId(task.getModelId());
        entity.setPromptIds(task.getPromptIds());
        entity.setJsonRequest(task.isJsonRequest());
        entity.setJsonResponse(task.isJsonResponse());
        entity.setResponseFormat(task.getResponseFormat());
        entity.setVariables(task.getVariables());
        entity.setWorkflow(task.getWorkflow());
        entity.setContextJson(task.getContextJson());
        entity.setWorkflowStopEvent(task.getWorkflowStopEvent());
        entity.setLanguage(task.getLanguage());
        entity.setImages(task.getImages());
        entity.setPurpose(task.getPurpose());
        entity.setLogprobs(task.getLogprobs());
        entity.setTopLogprobs(task.getTopLogprobs());
        entity.setImageBase64(task.getImageBase64());
        entity.setImageMimeType(task.getImageMimeType());
        return entity;
    }
    
    /**
     * Convert to domain object
     */
    public ImageMessageTask toImageMessageTask() {
        ImageMessageTask task = new ImageMessageTask();
        task.setMessageId(this.getMessageId());
        task.setChatId(this.getChatId());
        task.setMessage(this.getMessage());
        task.setSystemMessage(this.getSystemMessage());
        task.setGradeComment(this.getGradeComment());
        task.setGrade(this.getGrade());
        task.setCreatedTime(this.getCreatedTime());
        task.setResponseTime(this.getResponseTime());
        task.setModelId(this.getModelId());
        task.setPromptIds(this.getPromptIds());
        task.setJsonRequest(this.isJsonRequest());
        task.setJsonResponse(this.isJsonResponse());
        task.setResponseFormat(this.getResponseFormat());
        task.setVariables(this.getVariables());
        task.setWorkflow(this.getWorkflow());
        task.setContextJson(this.getContextJson());
        task.setWorkflowStopEvent(this.getWorkflowStopEvent());
        task.setLanguage(this.getLanguage());
        task.setImages(this.getImages());
        task.setPurpose(this.getPurpose());
        task.setLogprobs(this.getLogprobs());
        task.setTopLogprobs(this.getTopLogprobs());
        task.setImageBase64(this.getImageBase64());
        task.setImageMimeType(this.getImageMimeType());
        return task;
    }
}
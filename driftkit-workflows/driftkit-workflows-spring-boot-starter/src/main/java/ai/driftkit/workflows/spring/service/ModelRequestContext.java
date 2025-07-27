package ai.driftkit.workflows.spring.service;

import ai.driftkit.common.domain.MessageTask;
import ai.driftkit.common.domain.client.ResponseFormat;
import ai.driftkit.workflows.spring.domain.ModelRequestTrace.ContextType;
import ai.driftkit.workflows.spring.domain.ModelRequestTrace.RequestType;
import ai.driftkit.workflows.spring.domain.ModelRequestTrace.WorkflowInfo;
import ai.driftkit.common.domain.client.ModelImageResponse.ModelContentMessage;
import ai.driftkit.common.domain.client.ModelImageResponse.ModelContentMessage.ModelContentElement.ImageData;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;

@Data
@Builder
@Accessors(chain = true)
public class ModelRequestContext {
    private String contextId;
    private ContextType contextType;
    private RequestType requestType;
    private String promptText;
    private String promptId;
    private Map<String, Object> variables;
    // Note: Prompt class removed for now - can be added later if needed
    private MessageTask messageTask;
    private List<ImageData> imageData;
    private WorkflowInfo workflowInfo;
    private List<ModelContentMessage> contextMessages;
    private Double temperature;
    private String model;
    private String purpose;
    private String chatId;
    private ResponseFormat responseFormat;
    
    public static ModelRequestContextBuilder builder() {
        return new CustomModelRequestContextBuilder();
    }
    
    public static class CustomModelRequestContextBuilder extends ModelRequestContextBuilder {
        @Override
        public ModelRequestContext build() {
            ModelRequestContext context = super.build();
            
            // Prompt handling removed for now - can be added later if needed
            
            if (context.getMessageTask() != null) {
                MessageTask task = context.getMessageTask();
                if (StringUtils.isEmpty(context.getContextId())) {
                    context.setContextId(task.getMessageId());
                    context.setContextType(ContextType.MESSAGE_TASK);
                }
                if (StringUtils.isEmpty(context.getPromptText())) {
                    context.setPromptText(task.getMessage());
                }
                if (context.getVariables() == null) {
                    context.setVariables(task.getVariables());
                }
                if (StringUtils.isEmpty(context.getPromptId()) && 
                    CollectionUtils.isNotEmpty(task.getPromptIds())) {
                    context.setPromptId(task.getPromptIds().get(0));
                }
                if (StringUtils.isEmpty(context.getPurpose())) {
                    context.setPurpose(task.getPurpose());
                }
                if (StringUtils.isEmpty(context.getChatId())) {
                    context.setChatId(task.getChatId());
                }
                if (context.getResponseFormat() == null) {
                    context.setResponseFormat(task.getResponseFormat());
                }
            }
            
            if (context.getWorkflowInfo() != null) {
                if (context.getContextType() == null) {
                    context.setContextType(ContextType.WORKFLOW);
                }
                
                if (StringUtils.isEmpty(context.getContextId()) && 
                    StringUtils.isNotEmpty(context.getWorkflowInfo().getWorkflowId())) {
                    context.setContextId(context.getWorkflowInfo().getWorkflowId());
                }
            } else if (context.getContextType() == ContextType.WORKFLOW && 
                       StringUtils.isNotEmpty(context.getContextId())) {
                WorkflowInfo workflowInfo = WorkflowInfo.builder()
                    .workflowId(context.getContextId())
                    .build();
                context.setWorkflowInfo(workflowInfo);
            }
            
            if (context.getContextType() == null && StringUtils.isNotEmpty(context.getContextId())) {
                context.setContextType(ContextType.CUSTOM);
            }
            
            if (StringUtils.isEmpty(context.getPromptText()) && CollectionUtils.isEmpty(context.getImageData())) {
                throw new IllegalArgumentException("Either promptText or imageData must be provided");
            }
            
            return context;
        }
    }
}
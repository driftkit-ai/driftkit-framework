package ai.driftkit.workflows.spring;

import ai.driftkit.common.domain.Language;
import ai.driftkit.common.domain.Prompt;
import ai.driftkit.common.domain.client.ModelClient;
import ai.driftkit.common.domain.client.ModelImageResponse;
import ai.driftkit.common.domain.client.ModelTextResponse;
import ai.driftkit.context.core.service.PromptService;
import ai.driftkit.context.core.util.PromptUtils;
import ai.driftkit.workflows.core.domain.ExecutableWorkflow;
import ai.driftkit.workflows.core.domain.StartEvent;
import ai.driftkit.workflows.core.domain.WorkflowContext;
import ai.driftkit.workflows.core.service.WorkflowRegistry;
import ai.driftkit.workflows.spring.domain.ModelRequestTrace;
import ai.driftkit.workflows.spring.domain.ModelRequestTrace.ContextType;
import ai.driftkit.workflows.spring.domain.ModelRequestTrace.WorkflowInfo;
import ai.driftkit.workflows.spring.service.ModelRequestContext;
import ai.driftkit.workflows.spring.service.ModelRequestService;
import lombok.Getter;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;

public abstract class ModelWorkflow<I extends StartEvent, O> extends ExecutableWorkflow<I, O> {
    
    @Getter
    protected final ModelClient modelClient;
    protected final ModelRequestService modelRequestService;
    protected final PromptService promptService;
    
    public ModelWorkflow(ModelClient modelClient, ModelRequestService modelRequestService, PromptService promptService) {
        this.modelClient = modelClient;
        this.modelRequestService = modelRequestService;
        this.promptService = promptService;

        String clsName = this.getClass().getSimpleName();

        WorkflowRegistry.registerWorkflow(clsName, clsName, null, this);
    }
    
    /**
     * Send a text request using a prompt ID from the prompt service
     *
     * @param params The request parameters 
     * @param context The workflow context
     * @return The model response
     */
    protected ModelTextResponse sendTextToText(ModelRequestParams params, WorkflowContext context) {
        if (StringUtils.isBlank(params.getPromptId())) {
            throw new IllegalArgumentException("promptId must be provided");
        }
        
        Language language = getLanguageFromContext(context);
        Prompt prompt = promptService.getCurrentPromptOrThrow(params.getPromptId(), language);
        String promptText = prompt.getMessage();
        
        if (MapUtils.isNotEmpty(params.getVariables())) {
            promptText = PromptUtils.applyVariables(promptText, params.getVariables());
        }
        
        ModelRequestContext requestContext = buildRequestContext(
            ModelRequestTrace.RequestType.TEXT_TO_TEXT,
            promptText, 
            params.getPromptId(),
            params.getVariables(), 
            context
        );
        
        applyParamsToContext(params, requestContext);
        
        return modelRequestService.textToText(modelClient, requestContext);
    }
    
    /**
     * Send a text request using the provided prompt text
     *
     * @param params The request parameters
     * @param context The workflow context
     * @return The model response
     */
    protected ModelTextResponse sendPromptText(ModelRequestParams params, WorkflowContext context) {
        if (StringUtils.isBlank(params.getPromptText())) {
            throw new IllegalArgumentException("promptText must be provided");
        }
        
        String actualText = params.getPromptText();
        if (MapUtils.isNotEmpty(params.getVariables())) {
            actualText = PromptUtils.applyVariables(actualText, params.getVariables());
        }
        
        ModelRequestContext requestContext = buildRequestContext(
            ModelRequestTrace.RequestType.TEXT_TO_TEXT,
            actualText, 
            params.getPromptId(),
            params.getVariables(), 
            context
        );
        
        applyParamsToContext(params, requestContext);
        
        if (context.getTask() != null) {
            if (requestContext.getTemperature() == null) {
                requestContext.setTemperature(context.getTask().getTemperature());
            }

            if (requestContext.getModel() == null) {
                requestContext.setModel(context.getTask().getModelId());
            }
        }

        if (context.getTask() != null) {
            requestContext.setChatId(context.getTask().getChatId());
        }

        return modelRequestService.textToText(modelClient, requestContext);
    }
    
    /**
     * Send a text request with context messages for conversation history
     *
     * @param params The request parameters, must include contextMessages
     * @param context The workflow context
     * @return The model response
     */
    protected ModelTextResponse sendPromptTextWithHistory(ModelRequestParams params, WorkflowContext context) {
        if (StringUtils.isBlank(params.getPromptText())) {
            throw new IllegalArgumentException("promptText must be provided");
        }
        
        if (params.getContextMessages() == null) {
            throw new IllegalArgumentException("contextMessages must be provided");
        }
        
        String actualText = params.getPromptText();
        if (MapUtils.isNotEmpty(params.getVariables())) {
            actualText = PromptUtils.applyVariables(actualText, params.getVariables());
        }
        
        ModelRequestContext requestContext = buildRequestContext(
            ModelRequestTrace.RequestType.TEXT_TO_TEXT,
            actualText, 
            params.getPromptId(),
            params.getVariables(), 
            context
        );
        
        requestContext.setContextMessages(params.getContextMessages());
        applyParamsToContext(params, requestContext);
        
        return modelRequestService.textToText(modelClient, requestContext);
    }
    
    /**
     * Send a text-to-image request using a prompt ID
     *
     * @param params The request parameters
     * @param context The workflow context
     * @return The model image response
     */
    protected ModelImageResponse sendTextToImage(ModelRequestParams params, WorkflowContext context) {
        if (StringUtils.isBlank(params.getPromptId())) {
            throw new IllegalArgumentException("promptId must be provided");
        }
        
        Language language = getLanguageFromContext(context);
        Prompt prompt = promptService.getCurrentPromptOrThrow(params.getPromptId(), language);
        String promptText = prompt.getMessage();
        
        if (MapUtils.isNotEmpty(params.getVariables())) {
            promptText = PromptUtils.applyVariables(promptText, params.getVariables());
        }
        
        ModelRequestContext requestContext = buildRequestContext(
            ModelRequestTrace.RequestType.TEXT_TO_IMAGE,
            promptText, 
            params.getPromptId(),
            params.getVariables(), 
            context
        );
        
        applyParamsToContext(params, requestContext);
        
        return modelRequestService.textToImage(modelClient, requestContext);
    }
    
    /**
     * Send a text-to-image request using prompt text
     *
     * @param params The request parameters
     * @param context The workflow context
     * @return The model image response
     */
    protected ModelImageResponse sendImagePrompt(ModelRequestParams params, WorkflowContext context) {
        if (StringUtils.isBlank(params.getPromptText())) {
            throw new IllegalArgumentException("promptText must be provided");
        }
        
        String actualText = params.getPromptText();
        if (MapUtils.isNotEmpty(params.getVariables())) {
            actualText = PromptUtils.applyVariables(actualText, params.getVariables());
        }
        
        ModelRequestContext requestContext = buildRequestContext(
            ModelRequestTrace.RequestType.TEXT_TO_IMAGE,
            actualText, 
            params.getPromptId(),
            params.getVariables(), 
            context
        );
        
        applyParamsToContext(params, requestContext);
        
        return modelRequestService.textToImage(modelClient, requestContext);
    }
    
    /**
     * Send an image-to-text request using a prompt ID
     *
     * @param params The request parameters, must include imageData
     * @param context The workflow context
     * @return The model text response
     */
    protected ModelTextResponse sendImageToText(ModelRequestParams params, WorkflowContext context) {
        if (StringUtils.isBlank(params.getPromptId())) {
            throw new IllegalArgumentException("promptId must be provided");
        }
        
        if (params.getImageData() == null) {
            throw new IllegalArgumentException("imageData must be provided");
        }
        
        Language language = getLanguageFromContext(context);
        Prompt prompt = promptService.getCurrentPromptOrThrow(params.getPromptId(), language);
        String promptText = prompt.getMessage();
        
        if (MapUtils.isNotEmpty(params.getVariables())) {
            promptText = PromptUtils.applyVariables(promptText, params.getVariables());
        }
        
        ModelRequestContext requestContext = buildRequestContext(
            ModelRequestTrace.RequestType.IMAGE_TO_TEXT,
            promptText, 
            params.getPromptId(),
            params.getVariables(), 
            context
        );
        
        requestContext.setImageData(List.of(params.getImageData()));
        applyParamsToContext(params, requestContext);
        
        return modelRequestService.imageToText(modelClient, requestContext);
    }
    
    /**
     * Send an image-to-text request with custom text prompt
     *
     * @param params The request parameters, must include imageData
     * @param context The workflow context
     * @return The model text response
     */
    protected ModelTextResponse sendImageWithText(ModelRequestParams params, WorkflowContext context) {
        if (params.getImageData() == null) {
            throw new IllegalArgumentException("imageData must be provided");
        }
        
        String actualText = params.getPromptText() != null ? params.getPromptText() : "";
        if (MapUtils.isNotEmpty(params.getVariables()) && StringUtils.isNotBlank(actualText)) {
            actualText = PromptUtils.applyVariables(actualText, params.getVariables());
        }
        
        ModelRequestContext requestContext = buildRequestContext(
            ModelRequestTrace.RequestType.IMAGE_TO_TEXT,
            actualText, 
            params.getPromptId(),
            params.getVariables(), 
            context
        );
        
        requestContext.setImageData(List.of(params.getImageData()));
        applyParamsToContext(params, requestContext);
        
        return modelRequestService.imageToText(modelClient, requestContext);
    }
    
    private void applyParamsToContext(ModelRequestParams params, ModelRequestContext requestContext) {
        if (params.getTemperature() != null) {
            requestContext.setTemperature(params.getTemperature());
        }
        
        if (StringUtils.isNotBlank(params.getModel())) {
            requestContext.setModel(params.getModel());
        }
    }
    
    private Language getLanguageFromContext(WorkflowContext context) {
        Language language = context.get("language");
        return language != null ? language : Language.GENERAL;
    }
    
    private ModelRequestContext buildRequestContext(ModelRequestTrace.RequestType requestType,
                                                  String promptText, String promptId, 
                                                  Map<String, Object> variables, WorkflowContext context) {
        String step = context.getCurrentStep();
        
        WorkflowInfo workflowInfo = WorkflowInfo.builder()
                .workflowId(context.getWorkflowId())
                .workflowType(this.getClass().getSimpleName())
                .workflowStep(step)
                .build();
        
        ModelRequestContext.ModelRequestContextBuilder builder = ModelRequestContext.builder()
                .requestType(requestType)
                .contextType(ContextType.WORKFLOW)
                .contextId(context.getWorkflowId())
                .promptText(promptText)
                .promptId(promptId)
                .variables(variables)
                .workflowInfo(workflowInfo);
                
        // Include task from context if available to extract logprobs, chatId and other parameters
        if (context.getTask() != null) {
            builder.messageTask(context.getTask());
            builder.chatId(context.getTask().getChatId());
        }
        
        return builder.build();
    }
}
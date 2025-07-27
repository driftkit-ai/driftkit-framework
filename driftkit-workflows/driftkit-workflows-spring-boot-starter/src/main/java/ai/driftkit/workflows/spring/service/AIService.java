package ai.driftkit.workflows.spring.service;

import ai.driftkit.clients.openai.client.OpenAIModelClient;
import ai.driftkit.common.domain.Language;
import ai.driftkit.common.domain.MessageTask;
import ai.driftkit.workflows.spring.domain.MessageTaskEntity;
import ai.driftkit.common.domain.client.ModelClient;
import ai.driftkit.clients.core.ModelClientFactory;
import ai.driftkit.common.domain.client.ModelImageResponse;
import ai.driftkit.common.domain.client.ResponseFormat;
import ai.driftkit.common.domain.client.Role;
import ai.driftkit.config.EtlConfig;
import ai.driftkit.config.EtlConfig.VaultConfig;
import ai.driftkit.common.domain.client.ModelTextResponse;
import ai.driftkit.context.core.util.PromptUtils;
import ai.driftkit.workflows.core.domain.LLMRequestEvent;
import ai.driftkit.workflows.core.domain.StopEvent;
import ai.driftkit.workflows.core.domain.WorkflowContext;
import ai.driftkit.workflows.core.service.WorkflowRegistry;
import ai.driftkit.workflows.spring.repository.MessageTaskRepository;
import ai.driftkit.common.utils.JsonUtils;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Future;

@Slf4j
@Service
public class AIService {
    public static final String IMAGE_TASK_MARKER = "image:";
    public static final String SYSTEM_PROMPT = "Please respond to the user request [%s], think step by step. " +
            "Your response MUST be in [%s] language. " +
            "Put your thoughts before the response in the <thoughts /> tag.";
    public static final String THOUGHTS_START = "<thoughts>";
    public static final String THOUGHTS_END = "</thoughts>";
    public static final String THOUGHTS = "<thoughts />";

    @Autowired
    private ImageModelService imageService;

    @Autowired
    private WorkflowRegistry workflowRegistry;

    @Autowired
    private EtlConfig config;

    @Autowired
    private MessageTaskRepository messageTaskRepository;

    @Getter
    @Autowired
    private ModelRequestService modelRequestService;

    @Getter
    private ModelClient modelClient;

    private VaultConfig modelConfig;

    @PostConstruct
    public void init() {
        this.modelConfig = config.getVault().get(0);
        this.modelClient = ModelClientFactory.fromConfig(modelConfig);
    }

    @SneakyThrows
    public MessageTask chat(MessageTask task) {
        String message = task.getMessage();

        if (message.startsWith(IMAGE_TASK_MARKER)) {
            String[] marker2number = message.substring(0, IMAGE_TASK_MARKER.length() + 1).split(":");
            int images = 1;

            if (marker2number.length == 2 && StringUtils.isNumeric(marker2number[1])) {
                images = Integer.parseInt(marker2number[1]);
            }

            String msg = PromptUtils.applyVariables(task.getMessage(), task.getVariables());
            String query = msg.substring(IMAGE_TASK_MARKER.length() + 1).trim();

            return imageService.generateImage(task, query, images);
        }

        String result;

        try {
            if (task.isJsonRequest() && JsonUtils.isJSON(message)) {
                message = JsonUtils.fixIncompleteJSON(message);
            }

            // Use GENERAL as default language if not specified
            String languageStr = Optional.ofNullable(task.getLanguage()).orElse(Language.GENERAL).name();
            String workflowId = task.getWorkflow();

            // Check if the workflow is registered
            if (workflowId != null && workflowRegistry.hasWorkflow(workflowId)) {
                WorkflowContext workflowContext = new WorkflowContext(task);
                
                // For all workflows, use LLMRequestEvent
                StopEvent<?> stopEvent = workflowRegistry.executeWorkflow(
                        workflowId, 
                        new LLMRequestEvent(task), 
                        workflowContext
                );
                
                result = stopEvent.getResult();
                task.setWorkflowStopEvent(stopEvent);
                
                task.setModelId(workflowId);
            } else {
                // Default behavior with direct model call
                String systemMsg = SYSTEM_PROMPT.formatted(
                        message,
                        languageStr
                );
                String msg = systemMsg.replace("[", "\"").replace("]", "\"");

                msg = PromptUtils.applyVariables(msg, task.getVariables());

                List<ModelImageResponse.ModelContentMessage> messages = new ArrayList<>();
                messages.add(ModelImageResponse.ModelContentMessage.create(Role.user, msg));

                if (StringUtils.isNotBlank(task.getSystemMessage())) {
                    messages.add(ModelImageResponse.ModelContentMessage.create(Role.system, task.getSystemMessage()));
                }

                String model = Optional.ofNullable(task.getModelId()).orElse(modelConfig.getModel());

                if (StringUtils.isBlank(model)) {
                    model = OpenAIModelClient.GPT_DEFAULT;
                }

                boolean isImageToText = task.getImageBase64() != null && !task.getImageBase64().isEmpty() && StringUtils.isNotBlank(task.getImageMimeType());

                ModelRequestContext.ModelRequestContextBuilder contextBuilder = ModelRequestContext.builder()
                        .contextId(task.getMessageId())
                        .promptText(msg)
                        .messageTask(task)
                        .contextMessages(messages)
                        .temperature(modelConfig.getTemperature())
                        .model(model)
                        .chatId(task.getChatId());

                if (isImageToText) {
                    List<ModelImageResponse.ModelContentMessage.ModelContentElement.ImageData> images = new ArrayList<>();
                    for (String base64Image : task.getImageBase64()) {
                        byte[] imageBytes = java.util.Base64.getDecoder().decode(base64Image);
                        images.add(new ModelImageResponse.ModelContentMessage.ModelContentElement.ImageData(
                                imageBytes,
                                task.getImageMimeType()
                        ));
                    }
                    contextBuilder.imageData(images);
                }

                ModelRequestContext requestContext = contextBuilder.build();

                ModelTextResponse response;
                if (isImageToText) {
                    response = modelRequestService.imageToText(modelClient, requestContext);
                } else {
                    response = modelRequestService.textToText(modelClient, requestContext);
                }
                
                result = response.getResponse();
                
                task.updateWithResponseLogprobs(response);
                task.setModelId(model);

                result = result.replace(THOUGHTS, "");

                if (result.contains(THOUGHTS_START)) {
                    String thoughts = getThoughts(result);

                    result = getResultWoThoughts(result);

                    task.setContextJson(thoughts);
                    task.setResult(result);
                }
            }

        } catch (Exception e) {
            log.error("Model issue [{}]".formatted(task.getMessageId()), e);
            throw e;
        }

        // Determine if we should attempt JSON fixing
        boolean shouldFixJson = false;
        
        if (task.getResponseFormat() != null) {
            // If responseFormat is specified, only fix JSON for JSON response types
            ResponseFormat.ResponseType responseType = task.getResponseFormat().getType();
            shouldFixJson = responseType == ResponseFormat.ResponseType.JSON_OBJECT || 
                           responseType == ResponseFormat.ResponseType.JSON_SCHEMA;
        } else {
            // Fall back to the old behavior if no responseFormat is specified
            shouldFixJson = task.isJsonResponse() || JsonUtils.isJSON(result);
        }
        
        if (shouldFixJson && result != null) {
            result = JsonUtils.fixIncompleteJSON(result);
        }

        task.setResponseTime(System.currentTimeMillis());
        task.setResult(result);
        MessageTaskEntity entity = MessageTaskEntity.fromMessageTask(task);
        messageTaskRepository.save(entity);

        log.info("[llm] Result for message with id [{}] in chat [{}]: [{}]", task.getMessageId(), task.getChatId(), result);

        return task;
    }

    @org.jetbrains.annotations.NotNull
    public static String getResultWoThoughts(String result) {
        return result.substring(result.lastIndexOf(THOUGHTS_END) + THOUGHTS_END.length());
    }

    @org.jetbrains.annotations.NotNull
    public static String getThoughts(String result) {
        return result.substring(result.indexOf(THOUGHTS_START) + THOUGHTS_START.length(), result.lastIndexOf(THOUGHTS_END));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LLMTaskFuture {
        private String messageId;
        private Future<MessageTask> future;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Assistant {
        String modelId;
        AssistantBase instance;
    }

    public interface AssistantBase {
        String chat(String taskId, String message);
    }
}
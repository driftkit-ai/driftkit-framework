package ai.driftkit.workflows.spring.service;

import ai.driftkit.clients.openai.client.OpenAIModelClient;
import ai.driftkit.common.domain.MessageTask;
import ai.driftkit.common.domain.client.*;
import ai.driftkit.common.domain.client.ResponseFormat;
import ai.driftkit.context.core.util.PromptUtils;
import ai.driftkit.workflows.spring.domain.ModelRequestTrace;
import ai.driftkit.common.domain.client.ModelTextRequest.ModelTextRequestBuilder;
import ai.driftkit.workflows.spring.repository.ModelRequestTraceRepository;
import ai.driftkit.common.utils.AIUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelRequestService {

    private final ModelRequestTraceRepository traceRepository;
    private final Executor traceExecutor;
    
    public ModelTextResponse textToText(ModelClient modelClient, ModelRequestContext context) {
        if (context.getRequestType() == null) {
            context.setRequestType(ModelRequestTrace.RequestType.TEXT_TO_TEXT);
        }
        
        String resolvedPrompt = context.getPromptText();
        if (context.getVariables() != null && !context.getVariables().isEmpty()) {
            resolvedPrompt = PromptUtils.applyVariables(resolvedPrompt, context.getVariables());
        }
        
        // Build request with context messages, temperature, model, and responseFormat if they exist
        ModelTextRequest request = buildTextRequest(modelClient, resolvedPrompt, context.getContextMessages(),
                                                  context.getTemperature(), context.getModel(), context.getResponseFormat());
        
        ModelTextResponse response;
        try {
            response = modelClient.textToText(request);

            MessageTask task = context.getMessageTask();
            if (task != null) {
                task.updateWithResponseLogprobs(response);
            }
        } catch (Exception e) {
            log.error("Error sending text-to-text request to model", e);
            throw e;
        }
        
        if (response != null && response.getTrace() != null && StringUtils.isNotEmpty(context.getContextId())) {
            ModelRequestTrace trace = buildTextTrace(modelClient, context, request, response);
            CompletableFuture.runAsync(() -> saveTrace(trace), traceExecutor);
        }
        
        return response;
    }
    
    public ModelImageResponse textToImage(ModelClient modelClient, ModelRequestContext context) {
        if (context.getRequestType() == null) {
            context.setRequestType(ModelRequestTrace.RequestType.TEXT_TO_IMAGE);
        }
        
        String resolvedPrompt = context.getPromptText();
        if (context.getVariables() != null && !context.getVariables().isEmpty()) {
            resolvedPrompt = PromptUtils.applyVariables(resolvedPrompt, context.getVariables());
        }
        
        // Extract model, quality, and size from context if available
        String model = context.getModel();
        String quality = context.getQuality();
        String size = context.getSize();
        
        ModelImageRequest request = buildImageRequest(modelClient, resolvedPrompt, model, quality, size);
        
        return textToImage(modelClient, request, context);
    }
    
    public ModelImageResponse textToImage(ModelClient modelClient, ModelImageRequest request, ModelRequestContext context) {
        if (context.getRequestType() == null) {
            context.setRequestType(ModelRequestTrace.RequestType.TEXT_TO_IMAGE);
        }
        
        ModelImageResponse response;
        try {
            response = modelClient.textToImage(request);
        } catch (Exception e) {
            log.error("Error sending text-to-image request to model", e);
            throw e;
        }
        
        if (response != null && response.getTrace() != null && StringUtils.isNotEmpty(context.getContextId())) {
            ModelRequestTrace trace = buildImageTrace(modelClient, context, request, response);
            CompletableFuture.runAsync(() -> saveTrace(trace), traceExecutor);
        }
        
        return response;
    }
    
    public ModelTextResponse imageToText(ModelClient modelClient, ModelRequestContext context) {
        if (context.getRequestType() == null) {
            context.setRequestType(ModelRequestTrace.RequestType.IMAGE_TO_TEXT);
        }
        
        String textPrompt = "";
        if (StringUtils.isNotEmpty(context.getPromptText())) {
            textPrompt = context.getPromptText();
            if (context.getVariables() != null && !context.getVariables().isEmpty()) {
                textPrompt = PromptUtils.applyVariables(textPrompt, context.getVariables());
            }
        }
        
        ModelTextRequest request = buildImageTextRequest(
            modelClient, 
            textPrompt, 
            context.getImageData(),
            context.getTemperature(),
            context.getModel()
        );
        
        ModelTextResponse response;
        try {
            response = modelClient.imageToText(request);

            MessageTask task = context.getMessageTask();
            if (task != null) {
                task.updateWithResponseLogprobs(response);
            }
        } catch (Exception e) {
            log.error("Error sending image-to-text request to model", e);
            throw e;
        }
        
        if (response != null && response.getTrace() != null && StringUtils.isNotEmpty(context.getContextId())) {
            ModelRequestTrace trace = buildTextTrace(modelClient, context, request, response);
            CompletableFuture.runAsync(() -> saveTrace(trace), traceExecutor);
        }
        
        return response;
    }
    
    private ModelTextRequest buildTextRequest(ModelClient modelClient, String text) {
        return buildTextRequest(modelClient, text, null, null, null, null);
    }
    
    private ModelTextRequest buildTextRequest(ModelClient modelClient, String text, List<ModelImageResponse.ModelContentMessage> contextMessages) {
        return buildTextRequest(modelClient, text, contextMessages, null, null, null);
    }
    
    private ModelTextRequest buildTextRequest(ModelClient modelClient, String text, List<ModelImageResponse.ModelContentMessage> contextMessages, Double temperature) {
        return buildTextRequest(modelClient, text, contextMessages, temperature, null, null);
    }
    
    private ModelTextRequest buildTextRequest(ModelClient modelClient, String text, List<ModelImageResponse.ModelContentMessage> contextMessages, Double temperature, String model) {
        return buildTextRequest(modelClient, text, contextMessages, temperature, model, null);
    }
    
    private ModelTextRequest buildTextRequest(ModelClient modelClient, String text, List<ModelImageResponse.ModelContentMessage> contextMessages, Double temperature, String model, ResponseFormat responseFormat) {
        ModelTextRequestBuilder builder = ModelTextRequest.builder()
                .model(StringUtils.isNotBlank(model) ? model : Optional.ofNullable(modelClient.getModel()).orElse(OpenAIModelClient.GPT_DEFAULT))
                .temperature(temperature != null ? temperature : modelClient.getTemperature());
                
        if (responseFormat != null) {
            builder.responseFormat(responseFormat);
        }
                
        if (CollectionUtils.isNotEmpty(contextMessages)) {
            List<ModelImageResponse.ModelContentMessage> messages = new ArrayList<>(contextMessages);
            if (StringUtils.isNotBlank(text)) {
                messages.add(ModelImageResponse.ModelContentMessage.create(Role.user, text));
            }
            builder.messages(messages);
        } else {
            builder.messages(List.of(ModelImageResponse.ModelContentMessage.create(Role.user, text)));
        }
                
        return builder.build();
    }
    
    private ModelImageRequest buildImageRequest(ModelClient modelClient, String prompt) {
        return buildImageRequest(modelClient, prompt, null, null, null);
    }
    
    private ModelImageRequest buildImageRequest(ModelClient modelClient, String prompt, String model, String quality, String size) {
        return ModelImageRequest.builder()
                .model(StringUtils.isNotBlank(model) ? model : modelClient.getModel())
                .prompt(prompt)
                .quality(quality)
                .size(size)
                .build();
    }
    
    private ModelTextRequest buildImageTextRequest(ModelClient modelClient, String text, 
                                                 ModelImageResponse.ModelContentMessage.ModelContentElement.ImageData imageData) {
        return buildImageTextRequest(modelClient, text, imageData, null, null);
    }
    
    private ModelTextRequest buildImageTextRequest(ModelClient modelClient, String text, 
                                                 ModelImageResponse.ModelContentMessage.ModelContentElement.ImageData imageData,
                                                 Double temperature, String model) {
        return ModelTextRequest.builder()
                .model(StringUtils.isNotBlank(model) ? model : Optional.ofNullable(modelClient.getModel()).orElse(OpenAIModelClient.GPT_DEFAULT))
                .temperature(temperature != null ? temperature : modelClient.getTemperature())
                .messages(List.of(ModelImageResponse.ModelContentMessage.create(Role.user, text, imageData)))
                .build();
    }
    
    private ModelTextRequest buildImageTextRequest(ModelClient modelClient, String text, 
                                                 List<ModelImageResponse.ModelContentMessage.ModelContentElement.ImageData> imageData,
                                                 Double temperature, String model) {
        return ModelTextRequest.builder()
                .model(StringUtils.isNotBlank(model) ? model : Optional.ofNullable(modelClient.getModel()).orElse(OpenAIModelClient.GPT_DEFAULT))
                .temperature(temperature != null ? temperature : modelClient.getTemperature())
                .messages(List.of(ModelImageResponse.ModelContentMessage.create(Role.user, List.of(text), imageData)))
                .build();
    }
    
    private ModelRequestTrace buildTextTrace(ModelClient modelClient, ModelRequestContext context, 
                                          ModelTextRequest request, ModelTextResponse response) {
        String modelId = request.getModel() != null ? request.getModel() : modelClient.getModel();
        
        return ModelRequestTrace.fromTextResponse(
                context.getContextId(),
                context.getContextType(),
                context.getRequestType(),
                context.getPromptText(),
                context.getPromptId(),
                context.getVariables(),
                modelId,
                response,
                context.getWorkflowInfo(),
                context.getPurpose(),
                context.getChatId()
        );
    }
    
    private ModelRequestTrace buildImageTrace(ModelClient modelClient, ModelRequestContext context, 
                                           ModelImageRequest request, ModelImageResponse response) {
        String modelId = request.getModel() != null ? request.getModel() : modelClient.getModel();
        
        return ModelRequestTrace.fromImageResponse(
                context.getContextId(),
                context.getContextType(),
                context.getPromptText(),
                context.getPromptId(),
                context.getVariables(),
                modelId,
                response,
                context.getWorkflowInfo(),
                context.getPurpose(),
                context.getChatId()
        );
    }
    
    private void saveTrace(ModelRequestTrace trace) {
        try {
            traceRepository.save(trace);
        } catch (Exception e) {
            log.error("Error saving model request trace", e);
        }
    }
}
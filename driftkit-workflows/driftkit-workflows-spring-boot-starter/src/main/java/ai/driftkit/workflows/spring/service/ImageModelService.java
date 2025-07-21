package ai.driftkit.workflows.spring.service;

import ai.driftkit.common.domain.Grade;
import ai.driftkit.common.domain.ImageMessageTask;
import ai.driftkit.common.domain.ImageMessageTask.GeneratedImage;
import ai.driftkit.common.domain.LLMRequest;
import ai.driftkit.common.domain.MessageTask;
import ai.driftkit.common.domain.client.ModelClient;
import ai.driftkit.clients.core.ModelClientFactory;
import ai.driftkit.common.domain.client.ModelImageRequest;
import ai.driftkit.common.domain.client.ModelImageRequest.Quality;
import ai.driftkit.common.domain.client.ModelImageResponse;
import ai.driftkit.config.EtlConfig;
import ai.driftkit.workflows.spring.repository.ImageTaskRepository;
import ai.driftkit.workflows.spring.repository.MessageTaskRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ImageModelService {
    @Autowired
    private ImageTaskRepository imageTaskRepository;

    @Autowired
    private MessageTaskRepository messageTaskRepository;
    
    @Autowired
    private EtlConfig etlConfig;
    
    @Autowired
    private ModelRequestService modelRequestService;

    private ModelClient modelClient;
    private ExecutorService exec;

    @PostConstruct
    public void init() {
        this.modelClient = ModelClientFactory.fromConfig(etlConfig.getVault().get(0));

        this.exec = Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors() / 3));
    }

    public MessageTask generateImage(MessageTask task, String query, int imagesNumber) {
        ImageMessageTask imageTask = addTaskSync(task, query, imagesNumber);

        task.setResponseTime(imageTask.getResponseTime());
        task.setImageTaskId(imageTask.getMessageId());

        messageTaskRepository.save(task);
        return task;
    }

    public ImageMessageTask addTaskSync(MessageTask task, String query, int images) {
        try {
            ImageMessageTask imageMessageTask = ImageMessageTask.builder()
                    .messageId(task.getMessageId())
                    .chatId(task.getChatId())
                    .message(query)
                    .promptIds(task.getPromptIds())
                    .systemMessage(task.getSystemMessage())
                    .createdTime(System.currentTimeMillis())
                    .build();

            imageTaskRepository.save(imageMessageTask);

            return generate(task, imageMessageTask, Math.max(1, images));
        } catch (Exception e) {
            log.error("Request to image generator is failed, chatId: [%s]".formatted(task.getChatId()), e);
            throw e;
        }
    }

    public ImageMessageTask rate(String messageId, Grade grade, String comment) {
        ImageMessageTask message = getImageMessageById(messageId).orElse(null);

        if (message == null) {
            return null;
        }

        message.setGradeComment(comment);
        message.setGrade(grade);
        imageTaskRepository.save(message);
        return message;
    }

    public Optional<ImageMessageTask> getImageMessageById(String messageId) {
        return imageTaskRepository.findById(messageId);
    }

    public void describe(byte[] image) {
    }

    public ImageMessageTask generate(MessageTask task, ImageMessageTask request, int images) {
        // Create ModelImageRequest for the TraceableModelClient
        ModelImageRequest imageRequest = ModelImageRequest.builder()
                .prompt(request.getMessage())
                .n(images)
                .quality(Quality.low)
                .build();
                
        // Create request context for tracing
        ModelRequestContext requestContext = ModelRequestContext.builder()
                .contextId(request.getMessageId())
                .promptText(request.getMessage())
                .model(modelClient.getModel())
                .chatId(task.getChatId())
                .purpose(task.getPurpose() != null ? task.getPurpose() : "image_generation")
                .build();
                
        // Use ModelRequestService to handle the request (it uses TraceableModelClient internally)
        ModelImageResponse response = modelRequestService.textToImage(modelClient, imageRequest, requestContext);
        
        // Process the response
        if (response.getBytes() == null || response.getBytes().isEmpty()) {
            throw new RuntimeException("Empty image generation result for [%s]".formatted(request.getMessageId()));
        }
        
        request.setResponseTime(System.currentTimeMillis());
        request.setImages(response.getBytes()
                .stream()
                .map(imageData -> {
                    byte[] image = imageData.getImage();
                    String mimeType = imageData.getMimeType();
                    
                    return GeneratedImage.builder()
                            .data(image)
                            .mimeType(mimeType)
                            .revisedPrompt(response.getRevisedPrompt())
                            .build();
            })
                .collect(Collectors.toList())
        );

        imageTaskRepository.save(request);
        return request;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageRequest extends LLMRequest {
        int images;

        public ImageRequest(
                String chatId,
                String message,
                List<String> promptIds,
                String systemMessage,
                String workflow,
                int images
        ) {
            super(chatId, message, promptIds, systemMessage, workflow, false, null, null, null, null, null, null, null, null);
            this.images = images;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageTaskFuture {
        private String messageId;
        private Future<ImageMessageTask> future;
    }
}
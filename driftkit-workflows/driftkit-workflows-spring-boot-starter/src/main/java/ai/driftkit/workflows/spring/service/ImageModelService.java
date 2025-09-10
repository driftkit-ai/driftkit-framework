package ai.driftkit.workflows.spring.service;

import ai.driftkit.clients.core.ModelClientFactory;
import ai.driftkit.common.domain.Grade;
import ai.driftkit.common.domain.ImageMessageTask;
import ai.driftkit.common.domain.ImageMessageTask.GeneratedImage;
import ai.driftkit.common.domain.LLMRequest;
import ai.driftkit.common.domain.MessageTask;
import ai.driftkit.common.domain.client.ModelClient;
import ai.driftkit.common.domain.client.ModelImageRequest;
import ai.driftkit.common.domain.client.ModelImageResponse;
import ai.driftkit.config.EtlConfig;
import ai.driftkit.workflows.spring.domain.ImageMessageTaskEntity;
import ai.driftkit.workflows.spring.domain.MessageTaskEntity;
import ai.driftkit.workflows.spring.repository.ImageTaskRepository;
import ai.driftkit.workflows.spring.repository.MessageTaskRepositoryV1;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
    private MessageTaskRepositoryV1 messageTaskRepository;
    
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

        MessageTaskEntity entity = MessageTaskEntity.fromMessageTask(task);
        messageTaskRepository.save(entity);
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

            ImageMessageTaskEntity entity = ImageMessageTaskEntity.fromImageMessageTask(imageMessageTask);
            imageTaskRepository.save(entity);

            return generate(task, imageMessageTask, Math.max(1, images));
        } catch (Exception e) {
            log.error("Request to image generator is failed, chatId: [%s]".formatted(task.getChatId()), e);
            throw e;
        }
    }

    public ImageMessageTask rate(String messageId, Grade grade, String comment) {
        Optional<ImageMessageTaskEntity> entityOpt = imageTaskRepository.findById(messageId);
        
        if (entityOpt.isEmpty()) {
            return null;
        }
        
        ImageMessageTaskEntity entity = entityOpt.get();
        entity.setGradeComment(comment);
        entity.setGrade(grade);
        imageTaskRepository.save(entity);
        return entity.toImageMessageTask();
    }

    public Optional<ImageMessageTask> getImageMessageById(String messageId) {
        return imageTaskRepository.findById(messageId)
                .map(ImageMessageTaskEntity::toImageMessageTask);
    }

    public void describe(byte[] image) {
    }

    public ImageMessageTask generate(MessageTask task, ImageMessageTask request, int images) {
        // Get config for image model and quality
        EtlConfig.VaultConfig vaultConfig = etlConfig.getVault().get(0);
        
        // Create ModelImageRequest for the TraceableModelClient
        ModelImageRequest imageRequest = ModelImageRequest.builder()
                .prompt(request.getMessage())
                .n(images)
                .model(task.getModelId() != null ? task.getModelId() : vaultConfig.getImageModel())
                .quality(vaultConfig.getImageQuality())
                .size(vaultConfig.getImageSize())
                .build();
                
        // Create request context for tracing
        ModelRequestContext requestContext = ModelRequestContext.builder()
                .contextId(request.getMessageId())
                .promptText(request.getMessage())
                .model(imageRequest.getModel())
                .quality(imageRequest.getQuality())
                .size(imageRequest.getSize())
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

        ImageMessageTaskEntity entity = ImageMessageTaskEntity.fromImageMessageTask(request);
        imageTaskRepository.save(entity);
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
            super(chatId, message, promptIds, systemMessage, workflow, false, null, null, null, null, null, null, null, null, null);
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
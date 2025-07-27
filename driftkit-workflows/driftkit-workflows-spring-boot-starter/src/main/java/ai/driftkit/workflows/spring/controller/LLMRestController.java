package ai.driftkit.workflows.spring.controller;

import ai.driftkit.common.domain.*;
import ai.driftkit.common.domain.ImageMessageTask.GeneratedImage;
import ai.driftkit.common.domain.Language;
import ai.driftkit.context.core.service.PromptService;
import ai.driftkit.common.utils.AIUtils;
import ai.driftkit.workflows.spring.repository.MessageTaskRepository;
import ai.driftkit.workflows.spring.service.AIService.LLMTaskFuture;
import ai.driftkit.workflows.spring.service.ChatService;
import ai.driftkit.workflows.spring.service.ImageModelService;
import ai.driftkit.workflows.spring.service.TasksService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotNull;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping(path = "/data/v1.0/admin/llm/")
public class LLMRestController {

    @Autowired
    private ChatService chatService;

    @Autowired
    private TasksService tasksService;

    @Autowired
    private MessageTaskRepository messageTaskRepository;

    @Autowired
    private ImageModelService imageGenerationService;

    @Autowired
    private PromptService promptService;


    @GetMapping("/message/fixed")
    public @ResponseBody RestResponse<List<MessageTask>> getFixedMessages(
            @RequestParam("page") Integer page,
            @RequestParam("limit") Integer limit
    ) {
        List<MessageTask> messageTasks = messageTaskRepository.findMessageTasksWithFixes(
                PageRequest.of(page, limit)
        );

        return new RestResponse<>(
                true,
                messageTasks
        );
    }

    @PutMapping("/chat")
    public @ResponseBody RestResponse<Chat> createChat(
            @RequestBody ChatRequest request
    ) {
        Chat chat = chatService.createChat(request);

        return new RestResponse<>(
                true,
                chat
        );
    }

    @PostMapping("/chat")
    public @ResponseBody RestResponse<Chat> updateChat(
            @RequestBody Chat request
    ) {
        Chat chat = chatService.save(request);

        return new RestResponse<>(
                true,
                chat
        );
    }

    @GetMapping("/chats")
    public @ResponseBody RestResponse<List<Chat>> getChats() {
        return new RestResponse<>(
                true,
                chatService.getChats()
                        .stream()
                        .filter(e -> !e.isHidden())
                        .sorted(Comparator.comparing(Chat::getCreatedTime).reversed())
                        .collect(Collectors.toList())
        );
    }

    @GetMapping("/languages")
    public @ResponseBody RestResponse<Language[]> getLanguages() {
        return new RestResponse<>(
                true,
                Language.values()
        );
    }

    @PostMapping("/message")
    public @ResponseBody RestResponse<MessageId> sendMessage(
            @RequestBody LLMRequest request
    ) {
        LLMTaskFuture future = tasksService.addTask(
                MessageTask.builder()
                        .messageId(AIUtils.generateId())
                        .message(request.getMessage())
                        .language(request.getLanguage())
                        .chatId(request.getChatId())
                        .workflow(request.getWorkflow())
                        .jsonResponse(request.isJsonResponse())
                        .responseFormat(request.getResponseFormat())
                        .systemMessage(request.getSystemMessage())
                        .variables(request.getVariables())
                        .modelId(request.getModel())
                        .logprobs(request.getLogprobs())
                        .topLogprobs(request.getTopLogprobs())
                        .purpose(request.getPurpose())
                        .imageBase64(request.getImagesBase64())
                        .imageMimeType(request.getImageMimeType())
                        .createdTime(System.currentTimeMillis())
                    .build()
        );

        return new RestResponse<>(
                true,
                new MessageId(future.getMessageId())
        );
    }

    @PostMapping("/message/sync")
    public @ResponseBody RestResponse<MessageTask> sendMessageSync(
            @RequestBody LLMRequest request
    ) throws ExecutionException, InterruptedException {
        LLMTaskFuture future = tasksService.addTask(
                MessageTask.builder()
                        .messageId(AIUtils.generateId())
                        .message(request.getMessage())
                        .language(request.getLanguage())
                        .chatId(request.getChatId())
                        .workflow(request.getWorkflow())
                        .jsonResponse(request.isJsonResponse())
                        .responseFormat(request.getResponseFormat())
                        .systemMessage(request.getSystemMessage())
                        .variables(request.getVariables())
                        .logprobs(request.getLogprobs())
                        .topLogprobs(request.getTopLogprobs())
                        .purpose(request.getPurpose())
                        .imageBase64(request.getImagesBase64())
                        .imageMimeType(request.getImageMimeType())
                        .createdTime(System.currentTimeMillis())
                        .build()
        );

        return new RestResponse<>(
                true,
                future.getFuture().get()
        );
    }

    @PostMapping("/prompt/message")
    public @ResponseBody RestResponse<MessageId> sendPromptMessage(
            @RequestBody PromptRequest request
    ) {
        MessageTask llmRequest = promptService.getTaskFromPromptRequest(request);

        LLMTaskFuture future = tasksService.addTask(llmRequest);

        return new RestResponse<>(
                true,
                new MessageId(future.getMessageId())
        );
    }

    @PostMapping("/prompt/message/sync")
    public @ResponseBody RestResponse<MessageTask> sendPromptMessageSync(
            @RequestBody PromptRequest request
    ) throws ExecutionException, InterruptedException {
        if (StringUtils.isBlank(request.getWorkflow())) {
            request.setWorkflow(null);
        }
        MessageTask llmRequest = promptService.getTaskFromPromptRequest(request);

        LLMTaskFuture future = tasksService.addTask(llmRequest);

        return new RestResponse<>(
                true,
                future.getFuture().get()
        );
    }


    @GetMapping("/message/{messageId}")
    public @ResponseBody RestResponse<MessageTask> getMessage(
            @PathVariable String messageId
    ) {
        Optional<MessageTask> message = tasksService.getTaskByMessageId(messageId);

        return new RestResponse<>(
                message.isPresent(),
                message.orElse(null)
        );
    }
    
    @GetMapping("/messageTask/byContext")
    public @ResponseBody RestResponse<List<MessageTask>> getMessageTasksByContextIds(
            @RequestParam String contextIds
    ) {
        // Since contextId is the same as messageId, we can use it directly
        String[] messageIds = contextIds.split(",");
        List<MessageTask> tasks = messageTaskRepository.findAllById(List.of(messageIds));

        return new RestResponse<>(
                true,
                tasks
        );
    }

    @PostMapping("/message/{messageId}/rate")
    public @ResponseBody RestResponse<MessageTask> rateMessage(
            @PathVariable String messageId,
            @RequestBody MessageRate request
    ) {
        MessageTask msg = tasksService.rate(
                messageId,
                request.getGrade(),
                request.getGradeComment()
        );

        return new RestResponse<>(
                true,
                msg
        );
    }

    @GetMapping("/image/{messageId}/resource/{index}")
    @ResponseBody
    public ResponseEntity<InputStreamResource> getImageResource(@PathVariable String messageId, @PathVariable Integer index) {
        log.info("Request for image resource with id: {}, index: {}", messageId, index);
        
        ImageMessageTask msg = imageGenerationService
                .getImageMessageById(messageId)
                .orElse(null);

        if (msg == null) {
            log.error("Image message is not found for id [{}]", messageId);
            throw new RuntimeException("Image message is not found for id [%s]".formatted(messageId));
        }

        if (msg.getImages() == null || msg.getImages().isEmpty()) {
            log.error("Image list is empty for task id [{}]", messageId);
            throw new RuntimeException("Image list is empty for task id [%s]".formatted(messageId));
        }
        
        if (index >= msg.getImages().size()) {
            log.error("Image index {} is out of bounds for task id [{}] with {} images", 
                     index, messageId, msg.getImages().size());
            throw new RuntimeException("Image index %s is out of bounds for task id [%s]"
                                      .formatted(index, messageId));
        }

        GeneratedImage image = msg.getImages().get(index);
        
        if (image.getData() == null || image.getData().length == 0) {
            log.error("Image data is empty for task id [{}], index [{}]", messageId, index);
            throw new RuntimeException("Image data is empty for task id [%s], index [%s]"
                                      .formatted(messageId, index));
        }

        InputStream in = new ByteArrayInputStream(image.getData());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.getMimeType()))
                .body(new InputStreamResource(in));
    }

    @GetMapping("/image/{messageId}")
    public @ResponseBody RestResponse<ImageMessageTask> message(
            @PathVariable String messageId
    ) {
        ImageMessageTask msg = imageGenerationService
                .getImageMessageById(messageId)
                .orElse(null);

        if (msg != null) {
            msg.getImages().forEach(e -> e.setData(null));
        }

        return new RestResponse<>(
                msg != null,
                msg
        );
    }

    @PostMapping("/image/{messageId}/rate")
    public @ResponseBody RestResponse<ImageMessageTask> rateImage(
            @PathVariable String messageId,
            @RequestBody MessageRate request
    ) {
        ImageMessageTask msg = imageGenerationService.rate(
                messageId,
                request.getGrade(),
                request.getGradeComment()
        );

        return new RestResponse<>(
                true,
                msg
        );
    }

    @GetMapping(value = {"/chat/{chatId}/asTasks", "/chat/{chatId}/asMessages"})
    public @ResponseBody RestResponse<List<MessageTask>> getMessagesFromDb(
            @PathVariable String chatId,
            @RequestParam("skip") Integer skip,
            @RequestParam("limit") Integer limit,
            @RequestParam(value = "direction", required = false, defaultValue = "DESC") Direction direction
    ) {
        if (skip == null) {
            skip = 0;
        }

        if (limit == null) {
            limit = 10;
        }

        List<MessageTask> chat = tasksService.getTasksByChatId(chatId, skip, limit, direction);

        return new RestResponse<>(
                true,
                chat.stream().sorted(Comparator.comparing(AITask::getCreatedTime)).collect(Collectors.toList())
        );
    }

    @GetMapping("/chat/asTasks")
    public @ResponseBody RestResponse<List<MessageTask>> getMessagesFromDb(
            @RequestParam("skip") Integer skip,
            @RequestParam("limit") Integer limit,
            @RequestParam(value = "direction", required = false, defaultValue = "DESC") Direction direction
    ) {
        if (skip == null) {
            skip = 0;
        }

        if (limit == null) {
            limit = 10;
        }

        List<MessageTask> chat = tasksService.getTasks(skip, limit, direction);

        return new RestResponse<>(
                true,
                chat.stream().sorted(Comparator.comparing(AITask::getCreatedTime)).collect(Collectors.toList())
        );
    }

    @GetMapping("/chat/{chatId}/item")
    public @ResponseBody RestResponse<Chat> getChat(
            @PathVariable String chatId
    ) {
        Optional<Chat> chat = chatService.getChat(chatId);

        return new RestResponse<>(
                chat.isPresent(),
                chat.orElse(null)
        );
    }

    @GetMapping("/chat/{chatId}")
    public @ResponseBody RestResponse<List<Message>> getMessages(
            @PathVariable String chatId,
            @RequestParam(value = "skip", required = false) Integer skip,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "direction", required = false, defaultValue = "DESC") Direction direction
    ) {
        if (skip == null) {
            skip = 0;
        }

        if (limit == null) {
            limit = 10;
        }

        List<Message> chat = tasksService.getMessagesList(chatId, skip, limit, direction);

        return new RestResponse<>(
                true,
                chat.stream().sorted(Comparator.comparing(Message::getCreatedTime)).collect(Collectors.toList())
        );
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageId {
        @NotNull
        private String messageId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageRate {
        @NotNull
        private String gradeComment;
        @NotNull
        private Grade grade;
    }
}
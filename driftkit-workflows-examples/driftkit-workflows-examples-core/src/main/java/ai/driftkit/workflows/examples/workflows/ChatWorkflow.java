package ai.driftkit.workflows.examples.workflows;

import ai.driftkit.common.domain.*;
import ai.driftkit.common.domain.client.ModelTextResponse;
import ai.driftkit.common.service.ChatMemory;
import ai.driftkit.config.EtlConfig;
import ai.driftkit.config.EtlConfig.VaultConfig;
import ai.driftkit.clients.core.ModelClientFactory;
import ai.driftkit.clients.openai.client.OpenAIModelClient;
import ai.driftkit.context.core.util.PromptUtils;
import ai.driftkit.vector.spring.domain.Index;
import ai.driftkit.vector.spring.service.IndexService;
import ai.driftkit.workflows.core.domain.*;
import ai.driftkit.context.core.service.PromptService;
import ai.driftkit.workflows.examples.domain.RoutedMessage;
import ai.driftkit.workflows.spring.ModelWorkflow;
import ai.driftkit.workflows.spring.ModelRequestParams;
import ai.driftkit.workflows.examples.workflows.ChatWorkflow.ChatResult;
import ai.driftkit.workflows.examples.workflows.ChatWorkflow.ChatStartEvent;
import ai.driftkit.workflows.examples.workflows.RouterWorkflow.*;
import ai.driftkit.vector.core.domain.Document;
import ai.driftkit.vector.core.domain.DocumentsResult;
import ai.driftkit.workflows.spring.service.*;
import ai.driftkit.common.utils.JsonUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import java.nio.charset.Charset;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class ChatWorkflow extends ModelWorkflow<ChatStartEvent, ChatResult> {

    public static final String CURRENT_MESSAGE_NAME = "currentMessage";
    private final VaultConfig modelConfig;
    private final ImageModelService imageService;
    private final ChatService chatService;
    private final IndexService indexService;
    private final LLMMemoryProvider memoryProvider;

    public static final String RAG_CHAT_WITH_CONTEXT = "chat_with_context";

    public ChatWorkflow(EtlConfig config, PromptService promptService, ModelRequestService modelRequestService, ChatService chatService, TasksService tasksService, ImageModelService imageService, IndexService indexService) throws Exception {
        super(ModelClientFactory.fromConfig(config.getModelConfig(OpenAIModelClient.OPENAI_PREFIX).orElseThrow()),
              modelRequestService, 
              promptService);
        this.modelConfig = config.getModelConfig(OpenAIModelClient.OPENAI_PREFIX).orElseThrow();
        this.chatService = chatService;
        this.imageService = imageService;
        this.indexService = indexService;
        this.memoryProvider = new LLMMemoryProvider(chatService, tasksService);

        promptService.createIfNotExists(
                PromptService.DEFAULT_STARTING_PROMPT,
                PromptService.STARTING_PROMPT,
                null,
                false,
                Language.GENERAL
        );
        
        promptService.createIfNotExists(
                RAG_CHAT_WITH_CONTEXT,
                IOUtils.resourceToString("/prompts/dictionary/rag/%s.prompt".formatted(RAG_CHAT_WITH_CONTEXT), Charset.defaultCharset()),
                null,
                true,
                Language.GENERAL
        );
    }

    @Override
    public Class<ChatStartEvent> getInputType() {
        return ChatStartEvent.class;
    }

    @Override
    public Class<ChatResult> getOutputType() {
        return ChatResult.class;
    }

    @Step(name = "start")
    @StepInfo(description = "Starting step of the workflow")
    public ExternalEvent<RouterStartEvent> start(ChatStartEvent startEvent, WorkflowContext workflowContext) throws Exception {
        MessageTask task = startEvent.getTask();

        Chat chat = getChatOrCreate(startEvent);
        
        // Parse message to extract routes and actual message content
        MessageWithRoutes parsedMessage = parseMessage(task.getMessage());
        String query = getQuery(parsedMessage.getMessage(), task, chat.getLanguage());

        List<Index> indexes = indexService.getIndexList().stream()
                .filter(e -> !e.isDisabled())
                .filter(e -> e.getLanguage() == Language.GENERAL || e.getLanguage() == chat.getLanguage())
                .collect(Collectors.toList());

        log.info("Executing start step with query: {}, chatId: {}", query, chat.getChatId());

        ChatMemory chatMemory = memoryProvider.get(chat.getChatId());

        task.setMessage(query);

        Message message = new Message(
                task.getMessageId(),
                query,
                ChatMessageType.USER,
                MessageType.TEXT,
                null,
                null,
                null,
                null,
                null,
                task.getCreatedTime(),
                task.getCreatedTime(),
                null
        );
        chatMemory.add(message);

        memoryProvider.update(chat.getChatId(), List.of(task));

        workflowContext.put("query", query);
        workflowContext.put("currentMessage", task);
        workflowContext.put("context", chatMemory);

        // Use routes extracted from message
        return new ExternalEvent<>(
                RouterWorkflow.class,
                new RouterStartEvent(chatMemory.messages(), parsedMessage.getRoutes(), indexes),
                "processResponse"
        );
    }

    @Step(name = "processResponse")
    @StepInfo(description = "Check router result")
    public WorkflowEvent processResponse(DataEvent<RouterResult> routerResult, WorkflowContext workflowContext) throws Exception {
        RouterResult result = routerResult.getResult();

        String model = Optional.ofNullable(modelConfig.getModel()).orElse(OpenAIModelClient.GPT_DEFAULT);
        String query = workflowContext.get("query");
        MessageTask task = workflowContext.get(CURRENT_MESSAGE_NAME);

        Set<RouterDefaultInputTypes> inputTypes = result.getInputTypes()
                .stream()
                .map(RouterDecision::getDecision)
                .collect(Collectors.toSet());

        if (inputTypes.contains(RouterDefaultInputTypes.CUSTOM)) {
            return StopEvent.ofObject(ChatResult.create(result));
        } else if (inputTypes.contains(RouterDefaultInputTypes.ESCALATION) || inputTypes.contains(RouterDefaultInputTypes.PRODUCT_ISSUE)) {
            return StopEvent.ofObject(ChatResult.create(result));
        } else if (inputTypes.contains(RouterDefaultInputTypes.IMAGE_GENERATION)) {
            MessageTask messageTask = imageService.generateImage(task, query, 1);

            return StopEvent.ofObject(ChatResult.createImage(result, messageTask.getImageTaskId()));
        } 
        
        Map<String, Object> variables = task.getVariables();
        ModelTextResponse response;
        
        // Use related documents to improve answer if available
        if (CollectionUtils.isNotEmpty(result.getRelatedDocs())) {
            log.info("Using RAG with {} related document collections", result.getRelatedDocs().size());
            
            // Extract and format context from related documents
            StringBuilder contextBuilder = new StringBuilder();
            for (DocumentsResult docsResult : result.getRelatedDocs()) {
                for (Document doc : docsResult.documents()) {
                    contextBuilder.append("--- Source: ").append(doc.getId()).append(" ---\n");
                    contextBuilder.append(doc.getPageContent()).append("\n\n");
                }
            }
            
            String context = contextBuilder.toString();
            
            // Add context to variables for the prompt
            Map<String, Object> ragVariables = new HashMap<>(variables);
            ragVariables.put("context", context);
            ragVariables.put("query", query);
            
            // Use RAG-specific prompt with context
            response = sendTextToText(
                ModelRequestParams.create()
                    .setPromptId(RAG_CHAT_WITH_CONTEXT)
                    .setVariables(ragVariables)
                    .setModel(model),
                workflowContext);
        } else {
            // Standard query without context
            response = sendPromptText(
                ModelRequestParams.create()
                    .setPromptText(query)
                    .setVariables(variables),
                workflowContext);
        }

        return StopEvent.ofObject(ChatResult.create(result, response.getResponse()));
    }

    /**
     * Parse the message to extract potential routing information
     * @param messageContent Original message content
     * @return MessageWithRoutes containing the actual message and any routes found
     */
    private MessageWithRoutes parseMessage(String messageContent) {
        if (!JsonUtils.isJSON(messageContent)) {
            return MessageWithRoutes.of(messageContent);
        }
        
        try {
            RoutedMessage routedMessage = JsonUtils.safeParse(messageContent, RoutedMessage.class);
            
            // If valid RoutedMessage with content, extract message and routes
            if (routedMessage != null && StringUtils.isNotBlank(routedMessage.getMessage())) {
                return new MessageWithRoutes(
                    routedMessage.getMessage(),
                    CollectionUtils.isEmpty(routedMessage.getRoutes()) ? Collections.emptyList() : routedMessage.getRoutes()
                );
            }
        } catch (Exception e) {
            log.error("Failed to parse message as RoutedMessage: {}", e.getMessage());
        }
        
        // Default to original message with no routes
        return MessageWithRoutes.of(messageContent);
    }
    
    private String getQuery(String message, MessageTask task, Language language) {
        if (MapUtils.isNotEmpty(task.getVariables())) {
            message = PromptUtils.applyVariables(message, task.getVariables());
        }

        Prompt check = promptService.getCurrentPromptOrThrow(PromptService.DEFAULT_STARTING_PROMPT, Language.GENERAL);
        message = check.applyVariables(Map.of(
                "query", message,
                "language", language.name()
        ));

        return message;
    }

    private Chat getChatOrCreate(ChatStartEvent startEvent) {
        MessageTask task = startEvent.getTask();

        if (StringUtils.isBlank(task.getChatId())) {
            return chatService.createChat(
                    ChatRequest.builder()
                            .id(UUID.randomUUID().toString())
                            .memoryLength(startEvent.getMemoryLength())
                            .language(task.getLanguage())
                            .name(task.getMessage())
                       .build()
            );
        } else {
            Optional<Chat> chat = chatService.getChat(task.getChatId());

            if (chat.isEmpty()) {
                return chatService.createChat(
                        ChatRequest.builder()
                                .id(task.getChatId())
                                .memoryLength(startEvent.getMemoryLength())
                                .language(task.getLanguage())
                                .name(task.getMessage())
                                .build()
                );
            }

            return chat.orElseThrow();
        }
    }

    /**
     * Helper class to hold parsed message content and routes
     */
    @Data
    @AllArgsConstructor
    private static class MessageWithRoutes {
        private String message;
        private List<Route> routes;

        public static MessageWithRoutes of(String message) {
            return new MessageWithRoutes(message, Collections.emptyList());
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatResult {
        RouterResult route;
        String responce;
        String imageId;

        public static ChatResult create(RouterResult route) {
            return new ChatResult(route, null, null);
        }

        public static ChatResult create(RouterResult route, String responce) {
            return new ChatResult(route, responce, null);
        }

        public static ChatResult createImage(RouterResult route, String imageId) {
            return new ChatResult(route, null, imageId);
        }
    }

    @Data
    public static class ChatStartEvent extends StartEvent {
        private MessageTask task;
        private int memoryLength;

        @Builder
        public ChatStartEvent(MessageTask task, int memoryLength) {
            this.task = task;
            this.memoryLength = memoryLength;
        }
    }
}
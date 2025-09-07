# DriftKit Workflows Examples Module

## Overview

The `driftkit-workflows-examples` module provides comprehensive reference implementations demonstrating the capabilities of the new DriftKit workflow engine. It includes production-ready examples for chat systems, RAG (Retrieval-Augmented Generation), reasoning workflows, prompt engineering, and intelligent routing - all built on the `driftkit-workflow-engine-core` orchestration engine.

## Spring Boot Initialization

To use the workflows examples module in your Spring Boot application:

```java
@SpringBootApplication
@ComponentScan(basePackages = {"ai.driftkit.workflows.examples"}) // Scan example workflows
@EnableMongoRepositories(basePackages = "ai.driftkit.workflows.repository") // For persistence
public class YourApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourApplication.class, args);
    }
}
```

The module provides:
- **Example workflows**: Ready-to-use workflow implementations in `ai.driftkit.workflows.examples.workflows`
- **Service wrappers**: Spring services that wrap workflow execution
- **Configuration**: Workflow instances configured via `EtlConfig`
- **Dependencies**: Requires other DriftKit modules to be configured

Example workflow registration:

```java
@Configuration
public class WorkflowConfig {
    
    @Bean
    public ReasoningWorkflow reasoningWorkflow(EtlConfig config, 
                                             PromptService promptService,
                                             ModelRequestService modelRequestService) throws IOException {
        return new ReasoningWorkflow(config, promptService, modelRequestService);
    }
    
    @Bean 
    public ChatWorkflow chatWorkflow(ModelClient modelClient,
                                   ModelRequestService modelRequestService,
                                   PromptService promptService) {
        return new ChatWorkflow(modelClient, modelRequestService, promptService);
    }
}
```

## Architecture

### Module Structure

```
driftkit-workflows-examples/
├── driftkit-workflows-examples-core/        # Core workflow implementations
│   ├── domain/                              # Domain objects
│   └── workflows/                           # Example workflow classes
│       ├── ChatWorkflow.java                # Conversational AI with memory
│       ├── RouterWorkflow.java              # Intelligent message routing
│       ├── RAGModifyWorkflow.java           # Document ingestion for RAG
│       ├── RAGSearchWorkflow.java           # Vector similarity search
│       ├── ReasoningWorkflow.java           # Multi-step reasoning
│       ├── EnhancedReasoningWorkflow.java   # Advanced reasoning with validation
│       ├── PromptEngineerWorkflow.java      # Automated prompt engineering
│       └── ModelWorkflow.java               # Base class for AI workflows
├── driftkit-workflows-examples-spring-boot-starter/ # Spring Boot integration
│   └── service/                             # Spring service wrappers
└── pom.xml                                  # Parent module configuration
```

### Key Dependencies

The module integrates extensively with DriftKit components:

- **driftkit-workflow-engine-core** - Core workflow orchestration engine with fluent builder API
- **driftkit-workflow-engine-agents** - Multi-agent patterns and LLM agents
- **driftkit-workflow-test-framework** - Testing utilities and mock builders
- **DriftKit Clients** - AI model client abstractions
- **DriftKit Vector** - Vector storage and similarity search
- **DriftKit Embedding** - Text embedding generation
- **DriftKit Context Engineering** - Prompt management
- **DriftKit Common** - Shared utilities and domain objects

## Base Architecture

### ModelWorkflow Base Class

All AI-powered workflows in these examples extend the `ModelWorkflow` base class (example implementation), which provides common patterns and utilities for AI interactions. Note that actual workflows should use the `driftkit-workflow-engine-core` fluent API:

```java
public abstract class ModelWorkflow<I extends StartEvent, O> extends ExecutableWorkflow<I, O> {
    
    protected final ModelClient modelClient;
    protected final ModelRequestService modelRequestService;
    protected final PromptService promptService;
    
    // Core text-to-text operations
    protected ModelTextResponse sendTextToText(ModelRequestParams params, WorkflowContext context) throws Exception {
        if (StringUtils.isNotBlank(params.getPromptId())) {
            return executePromptById(params, context);
        } else {
            return executeCustomPrompt(params, context);
        }
    }
    
    // Text-to-image operations
    protected ModelImageResponse sendTextToImage(ModelRequestParams params, WorkflowContext context) throws Exception {
        ModelImageRequest request = ModelImageRequest.builder()
            .prompt(params.getPromptText())
            .quality(ModelImageRequest.Quality.standard)
            .n(1)
            .build();
            
        return modelClient.textToImage(request);
    }
    
    // Image-to-text operations
    protected ModelTextResponse sendImageToText(ModelRequestParams params, WorkflowContext context) throws Exception {
        List<ModelImageResponse.ModelContentMessage> messages = buildMultiModalMessages(params);
        
        ModelTextRequest request = ModelTextRequest.builder()
            .messages(messages)
            .temperature(params.getTemperature())
            .maxTokens(params.getMaxTokens())
            .build();
            
        return modelClient.imageToText(request);
    }
    
    // Helper for building multi-modal messages
    private List<ModelImageResponse.ModelContentMessage> buildMultiModalMessages(ModelRequestParams params) {
        List<ModelContentElement> content = new ArrayList<>();
        
        // Add text content
        if (StringUtils.isNotBlank(params.getPromptText())) {
            content.add(ModelContentElement.builder()
                .type(ModelTextRequest.MessageType.text)
                .text(params.getPromptText())
                .build());
        }
        
        // Add image content
        if (CollectionUtils.isNotEmpty(params.getImageData())) {
            for (String imageData : params.getImageData()) {
                content.add(ModelContentElement.builder()
                    .type(ModelTextRequest.MessageType.image)
                    .image(ModelContentElement.ImageData.builder()
                        .image(imageData.getBytes())
                        .mimeType("image/jpeg")
                        .build())
                    .build());
            }
        }
        
        return List.of(
            ModelImageResponse.ModelContentMessage.builder()
                .role(Role.user)
                .content(content)
                .build()
        );
    }
}
```

### ModelRequestParams Builder

Fluent builder for configuring AI model requests:

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelRequestParams {
    private String promptId;
    private String promptText;
    private Map<String, Object> variables = new HashMap<>();
    private Double temperature;
    private String model;
    private List<ModelImageResponse.ModelContentMessage> contextMessages;
    private List<String> imageData;
    private Integer maxTokens;
    
    public static ModelRequestParams create() {
        return new ModelRequestParams();
    }
    
    public ModelRequestParams withVariable(String key, Object value) {
        if (this.variables == null) {
            this.variables = new HashMap<>();
        }
        this.variables.put(key, value);
        return this;
    }
    
    // Fluent setters for all properties
    public ModelRequestParams promptId(String promptId) {
        this.promptId = promptId;
        return this;
    }
    
    public ModelRequestParams promptText(String promptText) {
        this.promptText = promptText;
        return this;
    }
    
    public ModelRequestParams temperature(Double temperature) {
        this.temperature = temperature;
        return this;
    }
}
```

## Workflow Examples

### 1. ChatWorkflow - Conversational AI with Memory

Advanced conversational AI system with routing, memory management, and multi-modal capabilities:

```java
@Component
public class ChatWorkflow extends ModelWorkflow<ChatWorkflow.ChatStartEvent, ChatWorkflow.ChatResult> {
    
    private final LLMMemoryProvider memoryProvider;
    private final WorkflowRegistry workflowRegistry;
    
    @Step(name = "start")
    public ExternalEvent<RouterWorkflow.RouterStartEvent> start(ChatStartEvent startEvent, WorkflowContext workflowContext) throws Exception {
        MessageTask task = startEvent.getTask();
        
        // Check for image generation request
        if (task.getMessage().startsWith("image:")) {
            return handleImageGeneration(task, workflowContext);
        }
        
        // Route through RouterWorkflow for intent classification
        RouterWorkflow.RouterStartEvent routerEvent = new RouterWorkflow.RouterStartEvent();
        routerEvent.setMessage(task.getMessage());
        routerEvent.setChatId(task.getChatId());
        routerEvent.setRoutes(extractCustomRoutes(task));
        
        return new ExternalEvent<>(
            "router-workflow",
            routerEvent,
            "processResponse"
        );
    }
    
    @Step(name = "processResponse")
    public WorkflowEvent processResponse(DataEvent<RouterWorkflow.RouterResult> routerResult, WorkflowContext workflowContext) throws Exception {
        RouterWorkflow.RouterResult result = routerResult.getData();
        ChatStartEvent startEvent = workflowContext.get("startEvent", ChatStartEvent.class);
        MessageTask task = startEvent.getTask();
        
        // Handle different routing outcomes
        if (result.getRoutes().stream().anyMatch(r -> r.getValue() == RouterWorkflow.RouterDefaultOutputTypes.RAG)) {
            return handleRAGResponse(result, task, workflowContext);
        } else if (result.getRoutes().stream().anyMatch(r -> r.getValue() == RouterWorkflow.RouterDefaultOutputTypes.REASONING)) {
            return handleReasoningResponse(result, task, workflowContext);
        } else {
            return handleChatResponse(result, task, workflowContext);
        }
    }
    
    private WorkflowEvent handleRAGResponse(RouterWorkflow.RouterResult result, MessageTask task, WorkflowContext workflowContext) throws Exception {
        // Build context from retrieved documents
        StringBuilder context = new StringBuilder();
        for (DocumentsResult docs : result.getRelatedDocs()) {
            for (Document doc : docs.documents()) {
                context.append(doc.getPageContent()).append("\n\n");
            }
        }
        
        // Create prompt with RAG context
        Map<String, Object> variables = new HashMap<>();
        variables.put("user_message", task.getMessage());
        variables.put("context", context.toString());
        variables.put("chat_history", getRecentChatHistory(task.getChatId()));
        
        ModelRequestParams params = ModelRequestParams.create()
            .promptId("rag-response")
            .withVariable("user_message", task.getMessage())
            .withVariable("context", context.toString())
            .temperature(0.7);
            
        ModelTextResponse response = sendTextToText(params, workflowContext);
        
        // Create result
        ChatResult chatResult = new ChatResult();
        chatResult.setRoute(result);
        chatResult.setResponse(response.getChoices().get(0).getMessage().getContent());
        
        return StopEvent.of(chatResult);
    }
    
    private WorkflowEvent handleImageGeneration(MessageTask task, WorkflowContext workflowContext) throws Exception {
        String prompt = task.getMessage().substring(6); // Remove "image:" prefix
        
        ModelRequestParams params = ModelRequestParams.create()
            .promptText(prompt)
            .temperature(0.8);
            
        ModelImageResponse response = sendTextToImage(params, workflowContext);
        
        // Save image and return reference
        String imageId = saveGeneratedImage(response.getBytes().get(0).getImage());
        
        ChatResult result = new ChatResult();
        result.setImageId(imageId);
        result.setResponse("Image generated successfully");
        
        return StopEvent.of(result);
    }
    
    // Data classes
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ChatStartEvent extends StartEvent {
        private MessageTask task;
        private int memoryLength = 10;
    }
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ChatResult {
        private RouterWorkflow.RouterResult route;
        private String response;
        private String imageId;
    }
}
```

### 2. RouterWorkflow - Intelligent Message Routing

Sophisticated intent classification and routing system:

```java
@Component
public class RouterWorkflow extends ModelWorkflow<RouterWorkflow.RouterStartEvent, RouterWorkflow.RouterResult> {
    
    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;
    
    @Step(name = "start")
    public DataEvent<String> start(RouterStartEvent startEvent, WorkflowContext workflowContext) throws Exception {
        String message = startEvent.getMessage();
        List<Route> customRoutes = startEvent.getRoutes();
        
        // Build routing prompt with available options
        Map<String, Object> variables = new HashMap<>();
        variables.put("user_message", message);
        variables.put("custom_routes", formatCustomRoutes(customRoutes));
        variables.put("default_input_types", Arrays.toString(RouterDefaultInputTypes.values()));
        variables.put("default_output_types", Arrays.toString(RouterDefaultOutputTypes.values()));
        
        ModelRequestParams params = ModelRequestParams.create()
            .promptId("router-classification")
            .variables(variables)
            .temperature(0.1); // Low temperature for consistent classification
            
        ModelTextResponse response = sendTextToText(params, workflowContext);
        String routingDecision = response.getChoices().get(0).getMessage().getContent();
        
        return DataEvent.of(routingDecision, "processRouting");
    }
    
    @Step(name = "processRouting")
    public DataEvent<RouterResult> processRouting(DataEvent<String> routingData, WorkflowContext workflowContext) throws Exception {
        String routingJson = routingData.getData();
        RouterStartEvent startEvent = workflowContext.get("startEvent", RouterStartEvent.class);
        
        // Parse routing decision
        ObjectMapper mapper = new ObjectMapper();
        JsonNode routingNode = mapper.readTree(routingJson);
        
        RouterResult result = new RouterResult();
        
        // Extract input type classifications
        JsonNode inputTypes = routingNode.get("input_types");
        if (inputTypes != null && inputTypes.isArray()) {
            Set<RouterDecision<RouterDefaultInputTypes>> inputDecisions = new HashSet<>();
            for (JsonNode inputType : inputTypes) {
                RouterDecision<RouterDefaultInputTypes> decision = new RouterDecision<>();
                decision.setValue(RouterDefaultInputTypes.valueOf(inputType.get("type").asText()));
                decision.setConfidence(inputType.get("confidence").asDouble());
                inputDecisions.add(decision);
            }
            result.setInputTypes(inputDecisions);
        }
        
        // Extract routing decisions
        JsonNode routes = routingNode.get("routes");
        if (routes != null && routes.isArray()) {
            Set<RouterDecision<RouterDefaultOutputTypes>> routeDecisions = new HashSet<>();
            for (JsonNode route : routes) {
                RouterDecision<RouterDefaultOutputTypes> decision = new RouterDecision<>();
                decision.setValue(RouterDefaultOutputTypes.valueOf(route.get("type").asText()));
                decision.setConfidence(route.get("confidence").asDouble());
                routeDecisions.add(decision);
            }
            result.setRoutes(routeDecisions);
        }
        
        // If RAG routing is detected, perform document retrieval
        boolean needsRAG = result.getRoutes().stream()
            .anyMatch(r -> r.getValue() == RouterDefaultOutputTypes.RAG);
            
        if (needsRAG) {
            return DataEvent.of(result, "performRAGSearch");
        } else {
            return DataEvent.of(result, "finalizeResult");
        }
    }
    
    @Step(name = "performRAGSearch")
    public DataEvent<RouterResult> performRAGSearch(DataEvent<RouterResult> routerData, WorkflowContext workflowContext) throws Exception {
        RouterResult result = routerData.getData();
        RouterStartEvent startEvent = workflowContext.get("startEvent", RouterStartEvent.class);
        
        // Generate embedding for the user message
        TextSegment segment = TextSegment.from(startEvent.getMessage());
        Response<Embedding> embeddingResponse = embeddingModel.embed(segment);
        
        // Search for relevant documents across multiple indexes
        List<DocumentsResult> allResults = new ArrayList<>();
        
        // Search default indexes
        for (String index : getDefaultIndexes()) {
            try {
                DocumentsResult docs = vectorStore.findRelevant(
                    index, 
                    embeddingResponse.content().vector(), 
                    5
                );
                if (!docs.isEmpty()) {
                    allResults.add(docs);
                }
            } catch (Exception e) {
                log.warn("Failed to search index: {}", index, e);
            }
        }
        
        result.setRelatedDocs(allResults);
        
        return DataEvent.of(result, "finalizeResult");
    }
    
    @Step(name = "finalizeResult")
    public StopEvent<RouterResult> finalizeResult(DataEvent<RouterResult> routerData, WorkflowContext workflowContext) {
        RouterResult result = routerData.getData();
        
        // Apply confidence thresholds and quality checks
        result.setRoutes(filterByConfidence(result.getRoutes(), 0.6));
        result.setInputTypes(filterByConfidence(result.getInputTypes(), 0.7));
        
        return StopEvent.of(result);
    }
    
    // Enums and data classes
    public enum RouterDefaultInputTypes {
        GREETING, INFORMATION_REQUEST, CLARIFICATION, CHAT,
        IMAGE_GENERATION, FEEDBACK, ESCALATION, SALES_SUPPORT,
        PRODUCT_ISSUE, CUSTOM, UNKNOWN
    }
    
    public enum RouterDefaultOutputTypes {
        RAG, SUPPORT_REQUEST, REDO_WITH_SMARTER_MODEL,
        REASONING, SALES_REQUEST, CHAT
    }
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RouterStartEvent extends StartEvent {
        private String message;
        private String chatId;
        private List<Route> routes = new ArrayList<>();
    }
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RouterResult {
        private Set<RouterDecision<RouterDefaultInputTypes>> inputTypes = new HashSet<>();
        private Set<RouterDecision<RouterDefaultOutputTypes>> routes = new HashSet<>();
        private Set<RouterDecision<String>> customRoutes = new HashSet<>();
        private Set<RouterDecision<String>> indexes = new HashSet<>();
        private List<DocumentsResult> relatedDocs = new ArrayList<>();
    }
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RouterDecision<T> {
        private T value;
        private double confidence;
        private String reasoning;
    }
}
```

### 3. RAGModifyWorkflow - Document Ingestion

Comprehensive document processing and vector storage workflow:

```java
@Component
public class RAGModifyWorkflow extends ModelWorkflow<RAGModifyWorkflow.DocumentsEvent, RAGModifyWorkflow.DocumentSaveResult> {
    
    private final UnifiedParser unifiedParser;
    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;
    private final DocumentSplitter documentSplitter;
    private final ThreadPoolExecutor executor;
    
    public RAGModifyWorkflow() {
        this.documentSplitter = DocumentSplitter.builder()
            .maxChunkSize(512)
            .overlapSize(50)
            .tokenizer(new SimpleTokenizer())
            .build();
            
        this.executor = new ThreadPoolExecutor(
            4, 8, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100)
        );
    }
    
    @Step
    public DataEvent<ParsedContent> parseInput(DocumentsEvent startEvent, WorkflowContext workflowContext) throws Exception {
        ParserInput input = createParserInput(startEvent);
        ParsedContent parsed = unifiedParser.parse(input);
        
        // Enhance metadata
        Map<String, Object> metadata = new HashMap<>(parsed.getMetadata());
        metadata.put("index_id", startEvent.getIndexId());
        metadata.put("source_type", startEvent.getSourceType());
        metadata.put("processing_time", System.currentTimeMillis());
        
        ParsedContent enriched = ParsedContent.builder()
            .content(parsed.getContent())
            .contentType(parsed.getContentType())
            .metadata(metadata)
            .build();
            
        return DataEvent.of(enriched);
    }
    
    @Step
    public StopEvent<DocumentSaveResult> ingestDocument(DataEvent<ParsedContent> documentEvent, WorkflowContext workflowContext) throws Exception {
        ParsedContent parsed = documentEvent.getData();
        DocumentsEvent startEvent = workflowContext.get("startEvent", DocumentsEvent.class);
        
        // Split content into chunks
        List<String> chunks = documentSplitter.split(parsed.getContent());
        
        // Process chunks concurrently
        List<CompletableFuture<Document>> futures = new ArrayList<>();
        
        for (int i = 0; i < chunks.size(); i++) {
            final int chunkIndex = i;
            final String chunk = chunks.get(i);
            
            CompletableFuture<Document> future = CompletableFuture.supplyAsync(() -> {
                try {
                    // Generate embedding
                    TextSegment segment = TextSegment.from(chunk);
                    Response<Embedding> embeddingResponse = embeddingModel.embed(segment);
                    
                    // Create document
                    Document document = new Document();
                    document.setId(UUID.randomUUID().toString());
                    document.setPageContent(chunk);
                    document.setVector(embeddingResponse.content().vector());
                    
                    // Add chunk metadata
                    Map<String, Object> chunkMetadata = new HashMap<>(parsed.getMetadata());
                    chunkMetadata.put("chunk_index", chunkIndex);
                    chunkMetadata.put("total_chunks", chunks.size());
                    chunkMetadata.put("chunk_size", chunk.length());
                    
                    document.setMetadata(chunkMetadata);
                    
                    return document;
                } catch (Exception e) {
                    throw new RuntimeException("Failed to process chunk " + chunkIndex, e);
                }
            }, executor);
            
            futures.add(future);
        }
        
        // Wait for all chunks to be processed
        List<Document> documents = futures.stream()
            .map(CompletableFuture::join)
            .collect(Collectors.toList());
        
        // Store documents in vector database
        List<String> documentIds = vectorStore.addDocuments(startEvent.getIndexId(), documents);
        
        // Create result
        DocumentSaveResult result = DocumentSaveResult.builder()
            .documentIds(documentIds)
            .totalDocuments(documents.size())
            .indexId(startEvent.getIndexId())
            .contentType(parsed.getContentType())
            .originalSize(parsed.getContent().length())
            .totalChunks(chunks.size())
            .build();
            
        return StopEvent.of(result);
    }
    
    private ParserInput createParserInput(DocumentsEvent event) {
        switch (event.getSourceType()) {
            case "youtube":
                return YoutubeIdParserInput.builder()
                    .youtubeId(event.getYoutubeId())
                    .languages(event.getLanguages())
                    .build();
                    
            case "file":
                return ByteArrayParserInput.builder()
                    .data(event.getFileData())
                    .contentType(ContentType.fromMimeType(event.getMimeType()))
                    .metadata(event.getMetadata())
                    .build();
                    
            case "text":
            default:
                return StringParserInput.builder()
                    .content(event.getTextContent())
                    .metadata(event.getMetadata())
                    .build();
        }
    }
    
    // Data classes
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentsEvent extends StartEvent {
        private String indexId;
        private String sourceType; // "youtube", "file", "text"
        
        // YouTube specific
        private String youtubeId;
        private List<String> languages;
        
        // File specific
        private byte[] fileData;
        private String mimeType;
        
        // Text specific
        private String textContent;
        
        // Common metadata
        private Map<String, Object> metadata = new HashMap<>();
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentSaveResult {
        private List<String> documentIds;
        private int totalDocuments;
        private String indexId;
        private ContentType contentType;
        private int originalSize;
        private int totalChunks;
        private long processingTimeMs;
    }
}
```

### 4. EnhancedReasoningWorkflow - Advanced Reasoning with Quality Control

Sophisticated reasoning system with planning, validation, and quality control:

```java
@Component
public class EnhancedReasoningWorkflow extends ModelWorkflow<StartEvent, EnhancedReasoningWorkflow.EnhancedReasoningResult> {
    
    private static final double SATISFACTION_THRESHOLD = 0.8;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final double MIN_RESULT_LENGTH_RATIO = 0.5;
    
    private final ChecklistService checklistService;
    private final WorkflowRegistry workflowRegistry;
    
    @Step(name = "start")
    public DataEvent<EnhancedReasoningPlan> start(StartEvent startEvent, WorkflowContext workflowContext) throws Exception {
        String task = startEvent.getData();
        
        // Generate reasoning plan with checklist
        Map<String, Object> variables = new HashMap<>();
        variables.put("task", task);
        
        ModelRequestParams params = ModelRequestParams.create()
            .promptId("enhanced-reasoning-plan")
            .variables(variables)
            .temperature(0.3);
            
        ModelTextResponse response = sendTextToText(params, workflowContext);
        String planJson = response.getChoices().get(0).getMessage().getContent();
        
        // Parse plan
        ObjectMapper mapper = new ObjectMapper();
        JsonNode planNode = mapper.readTree(planJson);
        
        EnhancedReasoningPlan plan = new EnhancedReasoningPlan();
        plan.setPlan(planNode.get("plan").asText());
        plan.setPlanConfidence(planNode.get("confidence").asDouble());
        plan.setTask(task);
        plan.setAttemptCount(1);
        
        // Extract and save checklist items
        JsonNode checklistNode = planNode.get("checklist");
        if (checklistNode != null && checklistNode.isArray()) {
            String checklistId = UUID.randomUUID().toString();
            plan.setChecklistId(checklistId);
            
            for (JsonNode item : checklistNode) {
                checklistService.addChecklistItem(checklistId, item.asText());
            }
        }
        
        return DataEvent.of(plan, "executeStep");
    }
    
    @Step(name = "executeStep", invocationLimit = 5)
    public DataEvent<EnhancedReasoningExecution> executeStep(DataEvent<EnhancedReasoningPlan> planEvent, WorkflowContext workflowContext) throws Exception {
        EnhancedReasoningPlan plan = planEvent.getData();
        
        // Build execution context
        Map<String, Object> variables = new HashMap<>();
        variables.put("task", plan.getTask());
        variables.put("plan", plan.getPlan());
        
        // Add checklist items if available
        if (plan.getChecklistId() != null) {
            List<ChecklistItemEntity> checklistItems = checklistService.getChecklistItems(plan.getChecklistId());
            List<String> checklist = checklistItems.stream()
                .map(ChecklistItemEntity::getContent)
                .collect(Collectors.toList());
            variables.put("checklist", checklist);
        }
        
        ModelRequestParams params = ModelRequestParams.create()
            .promptId("enhanced-reasoning-execution")
            .variables(variables)
            .temperature(0.7);
            
        ModelTextResponse response = sendTextToText(params, workflowContext);
        String executionJson = response.getChoices().get(0).getMessage().getContent();
        
        // Parse execution result
        ObjectMapper mapper = new ObjectMapper();
        JsonNode executionNode = mapper.readTree(executionJson);
        
        EnhancedReasoningExecution execution = new EnhancedReasoningExecution();
        execution.setResult(executionNode.get("result").asText());
        execution.setResultConfidence(executionNode.get("confidence").asDouble());
        execution.setChecklist(plan.getChecklistId());
        execution.setPlan(plan);
        
        return DataEvent.of(execution, "validateStep");
    }
    
    @Step(name = "validateStep", invocationLimit = 3)
    public WorkflowEvent validateStep(DataEvent<EnhancedReasoningExecution> executionEvent, WorkflowContext workflowContext) throws Exception {
        EnhancedReasoningExecution execution = executionEvent.getData();
        
        // Validate result quality
        Map<String, Object> variables = new HashMap<>();
        variables.put("task", execution.getPlan().getTask());
        variables.put("plan", execution.getPlan().getPlan());
        variables.put("result", execution.getResult());
        
        ModelRequestParams params = ModelRequestParams.create()
            .promptId("enhanced-reasoning-validation")
            .variables(variables)
            .temperature(0.2);
            
        ModelTextResponse response = sendTextToText(params, workflowContext);
        String validationJson = response.getChoices().get(0).getMessage().getContent();
        
        // Parse validation result
        ObjectMapper mapper = new ObjectMapper();
        JsonNode validationNode = mapper.readTree(validationJson);
        
        EnhancedReasoningValidation validation = new EnhancedReasoningValidation();
        validation.setValidation(validationNode.get("validation").asText());
        validation.setSatisfied(validationNode.get("satisfied").asBoolean());
        validation.setSatisfactionScore(validationNode.get("satisfaction_score").asDouble());
        validation.setExecution(execution);
        
        // Check quality thresholds
        boolean passesQualityCheck = validation.getSatisfactionScore() >= SATISFACTION_THRESHOLD &&
                                   validation.isSatisfied() &&
                                   execution.getResult().length() >= (execution.getPlan().getTask().length() * MIN_RESULT_LENGTH_RATIO);
        
        if (passesQualityCheck) {
            // Success - create final result
            EnhancedReasoningResult finalResult = EnhancedReasoningResult.builder()
                .plan(execution.getPlan().getPlan())
                .planValidation("Plan executed successfully")
                .planConfidence(execution.getPlan().getPlanConfidence())
                .result(execution.getResult())
                .resultValidation(validation.getValidation())
                .resultConfidence(execution.getResultConfidence())
                .isSatisfied(true)
                .attemptHistory(List.of("Attempt " + execution.getPlan().getAttemptCount() + ": Success"))
                .build();
                
            return StopEvent.of(finalResult);
        } else if (execution.getPlan().getAttemptCount() < MAX_RETRY_ATTEMPTS) {
            // Retry with improvements
            return DataEvent.of(validation, "retryStep");
        } else {
            // Fallback to original ReasoningWorkflow
            return DataEvent.of(validation, "fallbackStep");
        }
    }
    
    @Step(name = "retryStep")
    public WorkflowEvent retryStep(DataEvent<EnhancedReasoningValidation> validationEvent, WorkflowContext workflowContext) throws Exception {
        EnhancedReasoningValidation validation = validationEvent.getData();
        EnhancedReasoningPlan originalPlan = validation.getExecution().getPlan();
        
        // Improve plan based on validation feedback
        Map<String, Object> variables = new HashMap<>();
        variables.put("original_task", originalPlan.getTask());
        variables.put("original_plan", originalPlan.getPlan());
        variables.put("previous_result", validation.getExecution().getResult());
        variables.put("validation_feedback", validation.getValidation());
        
        ModelRequestParams params = ModelRequestParams.create()
            .promptId("enhanced-reasoning-improvement")
            .variables(variables)
            .temperature(0.4);
            
        ModelTextResponse response = sendTextToText(params, workflowContext);
        String improvedPlanJson = response.getChoices().get(0).getMessage().getContent();
        
        // Parse improved plan
        ObjectMapper mapper = new ObjectMapper();
        JsonNode planNode = mapper.readTree(improvedPlanJson);
        
        EnhancedReasoningPlan improvedPlan = new EnhancedReasoningPlan();
        improvedPlan.setPlan(planNode.get("improved_plan").asText());
        improvedPlan.setPlanConfidence(planNode.get("confidence").asDouble());
        improvedPlan.setTask(originalPlan.getTask());
        improvedPlan.setAttemptCount(originalPlan.getAttemptCount() + 1);
        improvedPlan.setChecklistId(originalPlan.getChecklistId());
        
        return DataEvent.of(improvedPlan, "executeStep");
    }
    
    @Step(name = "fallbackStep")
    public WorkflowEvent fallbackStep(DataEvent<EnhancedReasoningValidation> validationEvent, WorkflowContext workflowContext) throws Exception {
        EnhancedReasoningValidation validation = validationEvent.getData();
        String task = validation.getExecution().getPlan().getTask();
        
        // Fallback to original ReasoningWorkflow
        StartEvent fallbackEvent = new StartEvent() {
            @Override
            public String getData() {
                return task;
            }
        };
        
        ExternalEvent<StartEvent> externalEvent = new ExternalEvent<>(
            "reasoning-workflow",
            fallbackEvent,
            "finalizeFallback"
        );
        
        return externalEvent;
    }
    
    @FinalStep(name = "finalizeFallback")
    public StopEvent<EnhancedReasoningResult> finalizeFallback(DataEvent<String> fallbackResult, WorkflowContext workflowContext) {
        String result = fallbackResult.getData();
        
        EnhancedReasoningResult finalResult = EnhancedReasoningResult.builder()
            .plan("Fallback to standard reasoning")
            .planValidation("Enhanced reasoning failed, used fallback")
            .planConfidence(0.6)
            .result(result)
            .resultValidation("Fallback result")
            .resultConfidence(0.7)
            .isSatisfied(false)
            .fallbackWorkflowId("reasoning-workflow")
            .attemptHistory(List.of("Enhanced reasoning failed, used fallback workflow"))
            .build();
            
        return StopEvent.of(finalResult);
    }
    
    // Data classes
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnhancedReasoningPlan {
        private String plan;
        private String task;
        private double planConfidence;
        private String checklistId;
        private int attemptCount;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnhancedReasoningExecution {
        private String result;
        private double resultConfidence;
        private String checklist;
        private EnhancedReasoningPlan plan;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnhancedReasoningValidation {
        private String validation;
        private boolean satisfied;
        private double satisfactionScore;
        private EnhancedReasoningExecution execution;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnhancedReasoningResult {
        private String plan;
        private String planValidation;
        private Double planConfidence;
        private String result;
        private String resultValidation;
        private Double resultConfidence;
        private Boolean isSatisfied;
        private String fallbackWorkflowId;
        private List<String> attemptHistory;
        private Map<String, Object> metadata;
    }
}
```

### 5. PromptEngineerWorkflow - Automated Prompt Engineering

Intelligent prompt creation, editing, and testing system:

```java
@Component
public class PromptEngineerWorkflow extends ModelWorkflow<StartEvent, PromptEngineerWorkflow.PromptEngineerResult> {
    
    private final WorkflowRegistry workflowRegistry;
    private final VariableExtractor variableExtractor;
    
    @Step(name = "start")
    public DataEvent<PromptParameters> start(StartEvent startEvent, WorkflowContext workflowContext) throws Exception {
        String userRequest = startEvent.getData();
        
        // Extract parameters from user request
        Map<String, Object> variables = new HashMap<>();
        variables.put("user_request", userRequest);
        
        ModelRequestParams params = ModelRequestParams.create()
            .promptId("prompt-parameter-extraction")
            .variables(variables)
            .temperature(0.2);
            
        ModelTextResponse response = sendTextToText(params, workflowContext);
        String parametersJson = response.getChoices().get(0).getMessage().getContent();
        
        // Parse parameters
        ObjectMapper mapper = new ObjectMapper();
        JsonNode parametersNode = mapper.readTree(parametersJson);
        
        PromptParameters promptParams = new PromptParameters();
        promptParams.setTask(parametersNode.get("task").asText());
        promptParams.setPromptId(parametersNode.path("prompt_id").asText(null));
        promptParams.setWorkflowId(parametersNode.path("workflow_id").asText(null));
        promptParams.setLanguage(Language.valueOf(
            parametersNode.path("language").asText("GENERAL").toUpperCase()
        ));
        
        // Parse additional parameters
        JsonNode paramsNode = parametersNode.get("parameters");
        if (paramsNode != null) {
            Map<String, Object> additionalParams = mapper.convertValue(paramsNode, Map.class);
            promptParams.setParameters(additionalParams);
        }
        
        return DataEvent.of(promptParams, "engineerPrompt");
    }
    
    @Step(name = "engineerPrompt")
    public DataEvent<String> engineerPrompt(DataEvent<PromptParameters> parametersEvent, WorkflowContext workflowContext) throws Exception {
        PromptParameters promptParams = parametersEvent.getData();
        
        String promptAction = promptParams.getPromptId() != null ? "edit_prompt" : "create_prompt";
        
        // Build context for prompt engineering
        Map<String, Object> variables = new HashMap<>();
        variables.put("task", promptParams.getTask());
        variables.put("action", promptAction);
        variables.put("language", promptParams.getLanguage().toString());
        
        if (promptParams.getPromptId() != null) {
            // Get existing prompt for editing
            Optional<Prompt> existingPrompt = promptService.getPromptById(promptParams.getPromptId());
            if (existingPrompt.isPresent()) {
                variables.put("existing_prompt", existingPrompt.get().getMessage());
                variables.put("existing_system_message", existingPrompt.get().getSystemMessage());
            }
        }
        
        if (promptParams.getParameters() != null) {
            variables.putAll(promptParams.getParameters());
        }
        
        // Use EnhancedReasoningWorkflow for sophisticated prompt engineering
        StartEvent reasoningEvent = new StartEvent() {
            @Override
            public String getData() {
                return String.format(
                    "Engineer a high-quality prompt for the following task: %s. " +
                    "Action: %s. Language: %s. Consider best practices for prompt engineering.",
                    promptParams.getTask(), promptAction, promptParams.getLanguage()
                );
            }
        };
        
        ExternalEvent<StartEvent> externalEvent = new ExternalEvent<>(
            "enhanced-reasoning-workflow",
            reasoningEvent,
            "processEngineeredPrompt"
        );
        
        return DataEvent.of(externalEvent, "processEngineeredPrompt");
    }
    
    @Step(name = "processEngineeredPrompt")
    public DataEvent<String> processEngineeredPrompt(DataEvent<EnhancedReasoningWorkflow.EnhancedReasoningResult> reasoningResult, WorkflowContext workflowContext) throws Exception {
        EnhancedReasoningWorkflow.EnhancedReasoningResult result = reasoningResult.getData();
        String engineeredPrompt = result.getResult();
        
        // Extract variables from the engineered prompt
        Set<String> variables = variableExtractor.extractVariables(engineeredPrompt);
        
        // Parse the engineered prompt if it's structured
        String finalPrompt = engineeredPrompt;
        String systemMessage = "";
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode promptNode = mapper.readTree(engineeredPrompt);
            
            if (promptNode.has("prompt")) {
                finalPrompt = promptNode.get("prompt").asText();
            }
            if (promptNode.has("system_message")) {
                systemMessage = promptNode.get("system_message").asText();
            }
        } catch (Exception e) {
            // Not JSON, use as-is
        }
        
        // Create or update prompt
        PromptParameters promptParams = workflowContext.get("promptParameters", PromptParameters.class);
        
        Prompt prompt = new Prompt();
        if (promptParams.getPromptId() != null) {
            // Update existing prompt
            Optional<Prompt> existing = promptService.getPromptById(promptParams.getPromptId());
            if (existing.isPresent()) {
                prompt = existing.get();
            }
        } else {
            // Create new prompt
            prompt.setId(UUID.randomUUID().toString());
            prompt.setMethod("generated-" + UUID.randomUUID().toString().substring(0, 8));
            prompt.setCreatedTime(System.currentTimeMillis());
            prompt.setState(Prompt.State.MODERATION);
        }
        
        prompt.setMessage(finalPrompt);
        prompt.setSystemMessage(systemMessage);
        prompt.setLanguage(promptParams.getLanguage());
        prompt.setUpdatedTime(System.currentTimeMillis());
        
        // Save prompt
        Prompt savedPrompt = promptService.savePrompt(prompt);
        
        return DataEvent.of(savedPrompt.getId(), "testPrompt");
    }
    
    @Step(name = "testPrompt")
    public StopEvent<PromptEngineerResult> testPrompt(DataEvent<String> promptIdEvent, WorkflowContext workflowContext) throws Exception {
        String promptId = promptIdEvent.getData();
        PromptParameters promptParams = workflowContext.get("promptParameters", PromptParameters.class);
        
        // Generate test variables based on the prompt
        Optional<Prompt> promptOpt = promptService.getPromptById(promptId);
        if (promptOpt.isEmpty()) {
            throw new IllegalStateException("Prompt not found: " + promptId);
        }
        
        Prompt prompt = promptOpt.get();
        Set<String> variables = variableExtractor.extractVariables(prompt.getMessage());
        
        // Auto-generate test values
        Map<String, Object> testVariables = new HashMap<>();
        for (String variable : variables) {
            testVariables.put(variable, generateTestValue(variable));
        }
        
        Object testResult = null;
        
        // Test the prompt
        if (promptParams.getWorkflowId() != null && workflowRegistry.hasWorkflow(promptParams.getWorkflowId())) {
            // Test with specific workflow
            testResult = testWithWorkflow(promptParams.getWorkflowId(), promptId, testVariables);
        } else {
            // Test with direct prompt execution
            testResult = testWithDirectExecution(promptId, testVariables);
        }
        
        // Create result
        PromptEngineerResult result = new PromptEngineerResult();
        result.setPrompt(prompt.getMessage());
        result.setTestResult(testResult);
        
        return StopEvent.of(result);
    }
    
    private Object testWithWorkflow(String workflowId, String promptId, Map<String, Object> variables) throws Exception {
        // Create appropriate start event for the workflow
        StartEvent startEvent = createWorkflowStartEvent(workflowId, promptId, variables);
        
        // Execute workflow
        StopEvent<?> result = workflowRegistry.executeWorkflow(workflowId, startEvent, new WorkflowContext());
        
        return result.getResult();
    }
    
    private Object testWithDirectExecution(String promptId, Map<String, Object> variables) throws Exception {
        ModelRequestParams params = ModelRequestParams.create()
            .promptId(promptId)
            .variables(variables)
            .temperature(0.7);
            
        ModelTextResponse response = sendTextToText(params, new WorkflowContext());
        return response.getChoices().get(0).getMessage().getContent();
    }
    
    private String generateTestValue(String variableName) {
        // Simple heuristic-based test value generation
        String lowerName = variableName.toLowerCase();
        
        if (lowerName.contains("name")) {
            return "John Doe";
        } else if (lowerName.contains("email")) {
            return "john.doe@example.com";
        } else if (lowerName.contains("date")) {
            return "2024-01-15";
        } else if (lowerName.contains("age")) {
            return "25";
        } else if (lowerName.contains("message") || lowerName.contains("text")) {
            return "Sample message for testing";
        } else if (lowerName.contains("number") || lowerName.contains("count")) {
            return "42";
        } else {
            return "test_value_" + variableName;
        }
    }
    
    // Data classes
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PromptParameters {
        private String promptId;
        private String task;
        private String workflowId;
        private Language language;
        private Map<String, Object> parameters;
    }
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PromptEngineerResult {
        private String prompt;
        private Object testResult;
    }
}
```

## Spring Boot Integration

### Service Layer Pattern

All workflow examples are wrapped in Spring services for dependency injection and configuration:

```java
@Service
public class ChatWorkflowService extends ChatWorkflow {
    
    public ChatWorkflowService(
            EtlConfig config,
            PromptService promptService,
            ModelRequestService modelRequestService,
            LLMMemoryProvider memoryProvider,
            WorkflowRegistry workflowRegistry) throws Exception {
        
        super(
            ModelClientFactory.fromConfig(config.getVault().get(0)),
            promptService,
            modelRequestService,
            memoryProvider,
            workflowRegistry
        );
    }
}
```

### Configuration Integration

Workflows automatically integrate with DriftKit configuration:

```yaml
driftkit:
  vault:
    - name: "primary"
      type: "openai"
      apiKey: "${OPENAI_API_KEY}"
      model: "gpt-4"
      temperature: 0.7
    - name: "claude"
      type: "claude"
      apiKey: "${CLAUDE_API_KEY}"
      model: "claude-sonnet-4-20250514"
      temperature: 0.7
      
  vectorStores:
    - name: "primary"
      type: "pinecone"
      apiKey: "${PINECONE_API_KEY}"
      
  embeddingServices:
    - name: "primary"
      type: "openai"
      apiKey: "${OPENAI_API_KEY}"
      model: "text-embedding-ada-002"
```

## Usage Patterns

### Basic Workflow Execution

```java
@Service
public class WorkflowOrchestrationService {
    
    private final WorkflowRegistry workflowRegistry;
    
    public String processUserMessage(String message, String chatId) throws Exception {
        ChatWorkflow.ChatStartEvent startEvent = new ChatWorkflow.ChatStartEvent();
        startEvent.setTask(MessageTask.builder()
            .message(message)
            .chatId(chatId)
            .build());
        
        StopEvent<ChatWorkflow.ChatResult> result = workflowRegistry.executeWorkflow(
            "chat-workflow",
            startEvent,
            new WorkflowContext()
        );
        
        return result.getResult().getResponse();
    }
}
```

### Document Ingestion Pipeline

```java
@Service
public class DocumentIngestionService {
    
    private final WorkflowRegistry workflowRegistry;
    
    public DocumentSaveResult ingestDocument(MultipartFile file, String indexId) throws Exception {
        RAGModifyWorkflow.DocumentsEvent startEvent = RAGModifyWorkflow.DocumentsEvent.builder()
            .indexId(indexId)
            .sourceType("file")
            .fileData(file.getBytes())
            .mimeType(file.getContentType())
            .metadata(Map.of(
                "filename", file.getOriginalFilename(),
                "size", file.getSize()
            ))
            .build();
        
        StopEvent<RAGModifyWorkflow.DocumentSaveResult> result = workflowRegistry.executeWorkflow(
            "rag-modify-workflow",
            startEvent,
            new WorkflowContext()
        );
        
        return result.getResult();
    }
}
```

### Intelligent Routing

```java
@Service
public class MessageRoutingService {
    
    private final WorkflowRegistry workflowRegistry;
    
    public RouterResult routeMessage(String message, String chatId) throws Exception {
        RouterWorkflow.RouterStartEvent startEvent = new RouterWorkflow.RouterStartEvent();
        startEvent.setMessage(message);
        startEvent.setChatId(chatId);
        
        StopEvent<RouterWorkflow.RouterResult> result = workflowRegistry.executeWorkflow(
            "router-workflow",
            startEvent,
            new WorkflowContext()
        );
        
        return result.getResult();
    }
}
```

## Testing and Quality Assurance

### Workflow Testing Framework

```java
@SpringBootTest
class WorkflowIntegrationTest {
    
    @Autowired
    private WorkflowRegistry workflowRegistry;
    
    @Test
    void shouldExecuteChatWorkflow() throws Exception {
        ChatWorkflow.ChatStartEvent startEvent = new ChatWorkflow.ChatStartEvent();
        startEvent.setTask(MessageTask.builder()
            .message("Hello, how can you help me?")
            .chatId("test-chat")
            .build());
        
        StopEvent<ChatWorkflow.ChatResult> result = workflowRegistry.executeWorkflow(
            "chat-workflow",
            startEvent,
            new WorkflowContext()
        );
        
        assertThat(result.getResult()).isNotNull();
        assertThat(result.getResult().getResponse()).isNotBlank();
    }
    
    @Test
    void shouldIngestDocument() throws Exception {
        RAGModifyWorkflow.DocumentsEvent startEvent = RAGModifyWorkflow.DocumentsEvent.builder()
            .indexId("test-index")
            .sourceType("text")
            .textContent("This is a test document for ingestion.")
            .build();
        
        StopEvent<RAGModifyWorkflow.DocumentSaveResult> result = workflowRegistry.executeWorkflow(
            "rag-modify-workflow",
            startEvent,
            new WorkflowContext()
        );
        
        assertThat(result.getResult().getTotalDocuments()).isGreaterThan(0);
        assertThat(result.getResult().getDocumentIds()).isNotEmpty();
    }
}
```

## Performance Considerations

### Concurrent Processing

The RAGModifyWorkflow demonstrates concurrent processing for document chunking and embedding generation:

```java
// Process chunks concurrently using ThreadPoolExecutor
List<CompletableFuture<Document>> futures = chunks.stream()
    .map(chunk -> CompletableFuture.supplyAsync(() -> {
        // Process chunk asynchronously
        return processChunk(chunk);
    }, executor))
    .collect(Collectors.toList());

// Wait for all chunks to complete
List<Document> documents = futures.stream()
    .map(CompletableFuture::join)
    .collect(Collectors.toList());
```

### Memory Management

Workflows implement proper resource cleanup and memory management:

```java
@PreDestroy
public void cleanup() {
    if (executor != null && !executor.isShutdown()) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
```

## Extension Points

### Custom Workflow Development

Developers should use the `driftkit-workflow-engine-core` fluent API to create custom workflows:

```java
@Component
public class CustomWorkflowBuilder {
    
    public Workflow<CustomInput, CustomOutput> buildWorkflow() {
        return WorkflowBuilder.<CustomInput, CustomOutput>create("custom-workflow")
            .withDescription("Custom business workflow")
            .step("process", ProcessStep.class)
            .asyncStep("analyze", AnalysisStep.class, 
                AsyncConfig.builder()
                    .timeout(Duration.ofMinutes(5))
                    .build())
            .branch("decision", DecisionStep.class,
                branchBuilder -> branchBuilder
                    .branch("option1", Option1Step.class)
                    .branch("option2", Option2Step.class)
                    .otherwise(DefaultStep.class))
            .step("finalize", FinalStep.class)
            .build();
    }
}
```

### Integration with External Services

Workflows can easily integrate with external APIs and services:

```java
@Step(name = "callExternalService")
public DataEvent<String> callExternalService(DataEvent<String> input, WorkflowContext context) throws Exception {
    ExternalServiceRequest request = new ExternalServiceRequest(input.getData());
    ExternalServiceResponse response = externalServiceClient.call(request);
    
    return DataEvent.of(response.getData(), "processResponse");
}
```

This comprehensive documentation demonstrates the power and flexibility of the DriftKit workflows framework through practical, production-ready examples. Each workflow showcases different aspects of AI application development, from conversational interfaces to document processing and intelligent reasoning systems.
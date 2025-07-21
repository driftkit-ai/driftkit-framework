# DriftKit Workflows Module

## Overview

The `driftkit-workflows` module provides a comprehensive workflow orchestration engine for AI applications. It supports annotation-driven workflow definitions, event-based execution, conditional branching, retry mechanisms, and seamless integration with AI models and external services.

## Spring Boot Initialization

To use the workflows module in your Spring Boot application, the module will be automatically configured:

```java
@SpringBootApplication
// No additional annotations needed - auto-configuration handles everything
public class YourApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourApplication.class, args);
    }
}
```

The module provides:
- **Auto-configuration class**: `WorkflowAutoConfiguration`
- **Component scanning**: Automatically scans `ai.driftkit.workflows` package
- **Async Configuration**: Automatic `AsyncConfig` import with thread pool executors
- **MongoDB repositories**: Auto-enabled for workflow persistence
- **Services**: All workflow services automatically registered
- **REST Controllers**: Workflow execution and management endpoints
- **Model Support**: `ModelWorkflow` and `AgentWorkflow` base classes

## Architecture

### Module Structure

```
driftkit-workflows/
├── driftkit-workflows-core/              # Core workflow engine
│   ├── domain/                          # Core domain objects and annotations
│   │   ├── ExecutableWorkflow.java      # Base workflow class
│   │   ├── WorkflowContext.java         # Execution context
│   │   ├── events/                      # Event system
│   │   └── annotations/                 # Workflow annotations
│   └── service/                         # Workflow analysis and execution
│       ├── WorkflowAnalyzer.java        # Reflection-based workflow analysis
│       ├── ExecutableWorkflowGraph.java # Graph execution engine
│       └── WorkflowRegistry.java        # Workflow management
├── driftkit-workflows-spring-boot-starter/ # Spring Boot integration
│   ├── controller/                      # REST API endpoints
│   ├── service/                         # Business services
│   ├── repository/                      # Data access layer
│   └── config/                          # Configuration classes
└── pom.xml                             # Parent module configuration
```

### Key Dependencies

- **Commons JEXL** - Expression language for conditional logic
- **Spring Boot** - Web framework and dependency injection
- **MongoDB** - Document persistence
- **DriftKit Common** - Shared utilities and domain objects
- **DriftKit Clients** - AI model integration
- **DriftKit Context Engineering** - Prompt management

## Core Concepts

### ExecutableWorkflow

The base class for all workflow implementations. It provides generic type safety for input and output types, automatic graph building using reflection, a built-in graph traversal and execution logic, and thread-safe context sharing between steps.

**Key Features:**
- **Generic Type Safety** - Input and output types are enforced at compile time
- **Automatic Graph Building** - Uses reflection to analyze workflow structure
- **Execution Engine** - Built-in graph traversal and execution logic
- **Context Management** - Thread-safe context sharing between steps

### WorkflowContext

Thread-safe execution context for sharing data between workflow steps:

```java
@Data
public class WorkflowContext {
    private final Map<String, Object> context = new ConcurrentHashMap<>();
    private final Map<String, List<Object>> lists = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();
    
    // Context data management
    public void put(String name, Object value) {
        context.put(name, value);
    }
    
    public <T> T get(String name, Class<T> clazz) {
        Object value = context.get(name);
        return value != null ? clazz.cast(value) : null;
    }
    
    // List operations with thread safety
    public <T> void add(String name, T result) {
        lists.computeIfAbsent(name, k -> new CopyOnWriteArrayList<>()).add(result);
    }
    
    // Counter operations for step invocation tracking
    public void addCounter(String name, int amount) {
        counters.computeIfAbsent(name, k -> new AtomicInteger(0)).addAndGet(amount);
    }
    
    // Step execution tracking
    public void onStepInvocation(String method, WorkflowEvent event) {
        addCounter(method, 1);
        put(method + "_lastEvent", event);
        add(method + "_events", event);
    }
}
```

**Usage Example:**
```java
@Step
public DataEvent<String> processData(DataEvent<String> input, WorkflowContext context) {
    String data = input.getData();
    context.put("processedData", data.toUpperCase());
    context.addCounter("processCount", 1);
    
    return DataEvent.of("Processed: " + data);
}
```

## Event System

### Core Event Types

#### StartEvent
Base event for workflow initiation:

```java
public interface StartEvent extends WorkflowEvent {
}
```

#### DataEvent
Event for passing data between workflow steps:

```java
@Data
@AllArgsConstructor
public class DataEvent<T> implements WorkflowEvent {
    private T data;
    private String nextStepName;
    
    public static <T> DataEvent<T> of(T data) {
        return new DataEvent<>(data, null);
    }
    
    public static <T> DataEvent<T> of(T data, String nextStepName) {
        return new DataEvent<>(data, nextStepName);
    }
}
```

#### StopEvent
Event for workflow completion:

```java
@Data
@AllArgsConstructor
public class StopEvent<T> implements WorkflowEvent {
    private T result;
    
    public static <T> StopEvent<T> of(T result) {
        return new StopEvent<>(result);
    }
}
```

#### LLMRequestEvent
Specialized event for AI model requests:

```java
@Data
@AllArgsConstructor
public class LLMRequestEvent implements StartEvent {
    private MessageTask messageTask;
    
    // Additional properties for LLM-specific handling
    private String modelId;
    private Double temperature;
    private Integer maxTokens;
}
```

### Advanced Event Types

#### CombinedEvent
For complex data combinations:

```java
@Data
@AllArgsConstructor
public class CombinedEvent implements WorkflowEvent {
    private Map<String, Object> data;
    private String nextStepName;
}
```

#### ExternalEvent
For calling external workflows:

```java
@Data
@AllArgsConstructor
public class ExternalEvent implements WorkflowEvent {
    private String workflowId;
    private WorkflowEvent inputEvent;
    private String nextStepName;
}
```

## Workflow Annotations

### @Step Annotation

Basic step definition with retry and invocation policies:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Step {
    String name() default "";                           // Step identifier
    RetryPolicy retryPolicy() default @RetryPolicy;     // Retry configuration
    int invocationLimit() default 5;                    // Maximum invocations
    OnInvocationsLimit onInvocationsLimit() default OnInvocationsLimit.STOP; // Behavior on limit
    String nextStep() default "";                       // Default next step
}
```

**Usage Example:**
```java
@Step(name = "validateInput", invocationLimit = 3, nextStep = "processData")
public DataEvent<String> validateInput(StartEvent event, WorkflowContext context) {
    // Validation logic
    return DataEvent.of("validated");
}
```

### @LLMRequest Annotation

Specialized annotation for AI model interactions:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LLMRequest {
    String prompt();                     // Template prompt with variables
    String modelName();                  // AI model identifier
    String nextStep() default "";        // Default next step
    String condition() default "";       // JEXL condition for branching
    String trueStep() default "";        // Next step if condition is true
    String falseStep() default "";       // Next step if condition is false
}
```

**Usage Example:**
```java
@LLMRequest(
    prompt = "Analyze the following text: {{input.data}}. Provide sentiment analysis.",
    modelName = "gpt-4",
    condition = "result.sentiment == 'positive'",
    trueStep = "handlePositive",
    falseStep = "handleNegative"
)
public DataEvent<String> analyzeSentiment(DataEvent<String> input, WorkflowContext context) {
    // LLM request handling is automatic
    return null; // Return value ignored for LLMRequest steps
}
```

### @InlineStep Annotation

For expression-based steps using JEXL:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface InlineStep {
    String expression();              // JEXL expression to evaluate
    String nextStep() default "";     // Next step name
}
```

**Usage Example:**
```java
@InlineStep(
    expression = "context.get('score') > 0.8 ? 'approved' : 'rejected'",
    nextStep = "processResult"
)
public DataEvent<String> evaluateScore(DataEvent<Integer> input, WorkflowContext context) {
    return null; // Expression result becomes the output
}
```

### @FinalStep Annotation

For workflow termination steps:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)  
public @interface FinalStep {
    String name() default "";
}
```

## Workflow Analysis and Execution

### WorkflowAnalyzer

Reflection-based workflow analysis for automatic graph building. It scans all methods for workflow annotations (@LLMRequest, @Step, @InlineStep, @FinalStep), detects implicit steps based on return types, builds connections between workflow methods, and creates an executable workflow graph.

### ExecutableWorkflowGraph

Graph execution engine with advanced features including maximum depth protection to prevent infinite loops (1000 step limit), retry mechanisms with configurable policies and exponential backoff, conditional routing using JEXL-based conditional step navigation, external workflow support for invoking other workflows, variable substitution for template-based prompt processing, and comprehensive exception management.

**Key Features:**
- **Maximum Depth Protection** - Prevents infinite loops (1000 step limit)
- **Retry Mechanisms** - Configurable retry policies with exponential backoff
- **Conditional Routing** - JEXL-based conditional step navigation
- **External Workflow Support** - Ability to invoke other workflows
- **Variable Substitution** - Template-based prompt processing
- **Error Handling** - Comprehensive exception management

## Spring Boot Integration

### REST API Controllers

#### WorkflowController

Basic workflow management endpoints:

```java
@RestController
@RequestMapping("/workflows")
public class WorkflowController {
    
    private final WorkflowRegistry workflowRegistry;
    
    @GetMapping
    public ResponseEntity<RestResponse<List<Map<String, String>>>> getWorkflows() {
        List<Map<String, String>> workflows = workflowRegistry.getAllWorkflows()
            .stream()
            .map(WorkflowRegistry.RegisteredWorkflow::toMap)
            .collect(Collectors.toList());
            
        return ResponseEntity.ok(RestResponse.success(workflows));
    }
    
    @PostMapping("/{workflowId}/execute")
    public ResponseEntity<RestResponse<Object>> executeWorkflow(
            @PathVariable String workflowId,
            @RequestBody Map<String, Object> input) {
        
        try {
            StartEvent startEvent = createStartEvent(input);
            WorkflowContext context = new WorkflowContext();
            
            StopEvent<?> result = workflowRegistry.executeWorkflow(workflowId, startEvent, context);
            
            return ResponseEntity.ok(RestResponse.success(result.getResult()));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(RestResponse.error("Workflow execution failed: " + e.getMessage()));
        }
    }
}
```

#### LLMRestController

Comprehensive LLM and workflow integration:

```java
@RestController
@RequestMapping("/data/v1.0/admin/llm")
public class LLMRestController {
    
    private final TasksService tasksService;
    private final ChatService chatService;
    
    @PostMapping("/message")
    public ResponseEntity<RestResponse<String>> processMessage(@RequestBody MessageTask task) {
        try {
            tasksService.processMessageTaskAsync(task);
            return ResponseEntity.ok(RestResponse.success("Message queued for processing"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(RestResponse.error("Failed to process message: " + e.getMessage()));
        }
    }
    
    @PostMapping("/message/sync")
    public ResponseEntity<RestResponse<MessageTask>> processMessageSync(@RequestBody MessageTask task) {
        try {
            MessageTask result = tasksService.processMessageTaskSync(task);
            return ResponseEntity.ok(RestResponse.success(result));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(RestResponse.error("Failed to process message: " + e.getMessage()));
        }
    }
    
    @PostMapping("/prompt/message")
    public ResponseEntity<RestResponse<MessageTask>> processPromptMessage(
            @RequestBody PromptMessageRequest request) {
        try {
            MessageTask result = tasksService.processPromptMessage(request);
            return ResponseEntity.ok(RestResponse.success(result));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(RestResponse.error("Failed to process prompt message: " + e.getMessage()));
        }
    }
    
    @GetMapping("/chat/{chatId}")
    public ResponseEntity<RestResponse<List<Message>>> getChatMessages(@PathVariable String chatId) {
        try {
            List<Message> messages = chatService.getChatMessages(chatId);
            return ResponseEntity.ok(RestResponse.success(messages));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(RestResponse.error("Failed to retrieve chat messages: " + e.getMessage()));
        }
    }
}
```

### Core Services

#### AIService

Central service for AI model integration. It handles workflow execution routing, supports image generation requests, provides default LLM processing, manages conversation context building, integrates request tracing capabilities, and extracts structured thoughts from responses.

#### TasksService

Asynchronous task processing with thread pool management:

```java
@Service
@Slf4j
public class TasksService {
    
    private final AIService aiService;
    private final ChatService chatService;
    private final ThreadPoolExecutor executor;
    
    public TasksService(AIService aiService, ChatService chatService) {
        this.aiService = aiService;
        this.chatService = chatService;
        
        // Configure async executor
        this.executor = new ThreadPoolExecutor(
            5,    // corePoolSize
            15,   // maximumPoolSize
            60L,  // keepAliveTime
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000)
        );
    }
    
    public void processMessageTaskAsync(MessageTask task) {
        executor.submit(() -> {
            try {
                MessageTask processedTask = processMessageTaskSync(task);
                
                // Save to chat history
                if (processedTask.getChatId() != null) {
                    chatService.saveMessage(processedTask);
                }
                
                log.info("Message task processed successfully: {}", task.getMessageId());
            } catch (Exception e) {
                log.error("Failed to process message task: {}", task.getMessageId(), e);
            }
        });
    }
    
    public MessageTask processMessageTaskSync(MessageTask task) {
        // Set default language if not specified
        if (task.getLanguage() == null) {
            task.setLanguage(Language.SPANISH);
        }
        
        // Process with AI service
        MessageTask result = aiService.chat(task);
        
        // Set processing timestamps
        result.setResponseTime(System.currentTimeMillis());
        
        return result;
    }
    
    public MessageTask processPromptMessage(PromptMessageRequest request) {
        MessageTask task = MessageTask.builder()
            .messageId(UUID.randomUUID().toString())
            .chatId(request.getChatId())
            .message(request.getMessage())
            .promptId(request.getPromptId())
            .variables(request.getVariables())
            .language(request.getLanguage())
            .workflowId(request.getWorkflowId())
            .requestInitTime(System.currentTimeMillis())
            .build();
            
        return processMessageTaskSync(task);
    }
}
```

## Advanced Features

### Enhanced Reasoning

#### EnhancedReasoningResult

Sophisticated reasoning result tracking:

```java
@Data
@Builder
public class EnhancedReasoningResult {
    private String plan;                    // The reasoning plan
    private String planValidation;          // Validation of the plan
    private Double planConfidence;          // Confidence in the plan (0.0-1.0)
    private String result;                  // Final reasoning result
    private String resultValidation;        // Validation of the result
    private Double resultConfidence;        // Confidence in the result (0.0-1.0)
    private Boolean isSatisfied;           // Whether the result is satisfactory
    private String fallbackWorkflowId;     // Fallback workflow if unsatisfied
    private List<String> attemptHistory;   // History of reasoning attempts
    private Map<String, Object> metadata;  // Additional reasoning metadata
}
```

### Checklist Management

#### ChecklistService

Advanced checklist management with similarity detection:

```java
@Service
@Slf4j
public class ChecklistService {
    
    private static final double SIMILARITY_THRESHOLD = 0.85;
    private final ChecklistItemRepository repository;
    private final Cache<String, List<ChecklistItemEntity>> cache;
    
    public ChecklistService(ChecklistItemRepository repository) {
        this.repository = repository;
        this.cache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();
    }
    
    public void addChecklistItem(String checklistId, String content) {
        // Normalize content for comparison
        String normalizedContent = normalizeText(content);
        
        // Check for similar existing items
        List<ChecklistItemEntity> existingItems = getChecklistItems(checklistId);
        
        boolean isDuplicate = existingItems.stream()
            .anyMatch(item -> calculateSimilarity(
                normalizeText(item.getContent()), 
                normalizedContent
            ) > SIMILARITY_THRESHOLD);
            
        if (!isDuplicate) {
            ChecklistItemEntity item = ChecklistItemEntity.builder()
                .id(UUID.randomUUID().toString())
                .checklistId(checklistId)
                .content(content)
                .createdAt(LocalDateTime.now())
                .build();
                
            repository.save(item);
            invalidateCache(checklistId);
        } else {
            log.debug("Duplicate checklist item detected, skipping: {}", content);
        }
    }
    
    private double calculateSimilarity(String text1, String text2) {
        // Implement similarity calculation (Levenshtein distance, etc.)
        return TextSimilarityUtil.combinedSimilarity(text1, text2);
    }
    
    private String normalizeText(String text) {
        return text.toLowerCase()
            .replaceAll("[^a-záéíóúñü\\s]", "")
            .replaceAll("\\s+", " ")
            .trim();
    }
    
    @Scheduled(fixedRate = 3600000) // Every hour
    public void cleanupDuplicates() {
        // Periodic cleanup of duplicate items
        List<ChecklistItemEntity> allItems = repository.findAll();
        
        Map<String, List<ChecklistItemEntity>> itemsByChecklist = allItems.stream()
            .collect(Collectors.groupingBy(ChecklistItemEntity::getChecklistId));
            
        for (Map.Entry<String, List<ChecklistItemEntity>> entry : itemsByChecklist.entrySet()) {
            removeDuplicatesFromChecklist(entry.getValue());
        }
    }
}
```

### Model Request Tracing

#### ModelRequestTrace

Comprehensive request tracing for debugging and monitoring:

```java
@Document(collection = "model_request_traces")
@Data
@Builder
public class ModelRequestTrace {
    @Id
    private String id;
    private String contextId;
    private ContextType contextType;
    private RequestType requestType;
    private String promptTemplate;
    private Map<String, Object> promptVariables;
    private String resolvedPrompt;
    private String modelId;
    private String response;
    private ModelTrace trace;
    private WorkflowInfo workflowInfo;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private String errorMessage;
    
    @Data
    @Builder
    public static class WorkflowInfo {
        private String workflowId;
        private String stepName;
        private String stepType;
        private Integer executionDepth;
        private Map<String, Object> contextSnapshot;
    }
}
```

#### ModelRequestService

Service for managing model request lifecycle and tracing:

```java
@Service
@Slf4j
public class ModelRequestService {
    
    private final ModelRequestTraceRepository traceRepository;
    private final ModelRequestContext requestContext;
    
    public ModelTextResponse executeWithTracing(ModelTextRequest request, 
                                               String contextId, 
                                               ContextType contextType) {
        
        ModelRequestTrace trace = ModelRequestTrace.builder()
            .id(UUID.randomUUID().toString())
            .contextId(contextId)
            .contextType(contextType)
            .requestType(RequestType.TEXT_COMPLETION)
            .createdAt(LocalDateTime.now())
            .build();
            
        try {
            // Set request context
            requestContext.setCurrentTrace(trace);
            
            // Execute request
            ModelTextResponse response = modelClient.textToText(request);
            
            // Update trace with response
            trace.setResponse(response.getChoices().get(0).getMessage().getContent());
            trace.setCompletedAt(LocalDateTime.now());
            
            // Save trace
            traceRepository.save(trace);
            
            return response;
            
        } catch (Exception e) {
            trace.setErrorMessage(e.getMessage());
            trace.setCompletedAt(LocalDateTime.now());
            traceRepository.save(trace);
            throw e;
        } finally {
            requestContext.clearCurrentTrace();
        }
    }
}
```

## Usage Patterns

### Simple Workflow Definition

```java
@Component
public class DataProcessingWorkflow extends ExecutableWorkflow<StartEvent, String> {
    
    @Step(name = "validateInput", nextStep = "processData")
    public DataEvent<String> validateInput(StartEvent event, WorkflowContext context) {
        // Validation logic
        String input = event.getData();
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("Input cannot be empty");
        }
        
        context.put("validatedInput", input.trim());
        return DataEvent.of(input.trim());
    }
    
    @Step(name = "processData", nextStep = "formatOutput")
    public DataEvent<String> processData(DataEvent<String> input, WorkflowContext context) {
        String data = input.getData();
        String processed = data.toUpperCase().replace(" ", "_");
        
        context.put("processedData", processed);
        return DataEvent.of(processed);
    }
    
    @FinalStep(name = "formatOutput")
    public StopEvent<String> formatOutput(DataEvent<String> input, WorkflowContext context) {
        String processed = input.getData();
        String result = "RESULT: " + processed;
        
        return StopEvent.of(result);
    }
}
```

### LLM-Based Workflow

```java
@Component
public class SentimentAnalysisWorkflow extends ExecutableWorkflow<LLMRequestEvent, String> {
    
    @LLMRequest(
        prompt = """
            Analyze the sentiment of the following text: "{{messageTask.message}}"
            
            Provide your analysis in JSON format:
            {
                "sentiment": "positive|negative|neutral",
                "confidence": 0.0-1.0,
                "reasoning": "explanation"
            }
            """,
        modelName = "gpt-4",
        condition = "result.sentiment == 'positive'",
        trueStep = "handlePositive",
        falseStep = "handleNegative"
    )
    public void analyzeSentiment(LLMRequestEvent event, WorkflowContext context) {
        // LLM processing is automatic
    }
    
    @Step(name = "handlePositive")
    public DataEvent<String> handlePositive(DataEvent<String> input, WorkflowContext context) {
        // Handle positive sentiment
        context.put("sentiment", "positive");
        return DataEvent.of("Positive sentiment detected", "generateResponse");
    }
    
    @Step(name = "handleNegative")
    public DataEvent<String> handleNegative(DataEvent<String> input, WorkflowContext context) {
        // Handle negative sentiment
        context.put("sentiment", "negative");
        return DataEvent.of("Negative sentiment detected", "generateResponse");
    }
    
    @LLMRequest(
        prompt = """
            Based on the sentiment analysis result: {{context.sentiment}}
            Generate an appropriate response to the user's message: "{{messageTask.message}}"
            
            Be empathetic and helpful.
            """,
        modelName = "gpt-4"
    )
    public void generateResponse(DataEvent<String> input, WorkflowContext context) {
        // Response generation
    }
    
    @FinalStep
    public StopEvent<String> finalizeResponse(DataEvent<String> input, WorkflowContext context) {
        return StopEvent.of(input.getData());
    }
}
```

### Complex Conditional Workflow

```java
@Component
public class DocumentReviewWorkflow extends ExecutableWorkflow<StartEvent, String> {
    
    @Step(name = "extractContent")
    public DataEvent<String> extractContent(StartEvent event, WorkflowContext context) {
        // Extract content from document
        String content = extractDocumentContent(event.getData());
        context.put("originalContent", content);
        return DataEvent.of(content, "analyzeComplexity");
    }
    
    @InlineStep(
        expression = "input.data.length() > 1000 ? 'complexAnalysis' : 'simpleAnalysis'"
    )
    public DataEvent<String> analyzeComplexity(DataEvent<String> input, WorkflowContext context) {
        return null; // Next step determined by expression
    }
    
    @LLMRequest(
        prompt = """
            Perform a detailed analysis of this complex document:
            {{input.data}}
            
            Provide:
            1. Summary
            2. Key points
            3. Recommendations
            4. Risk assessment
            """,
        modelName = "gpt-4",
        nextStep = "validateAnalysis"
    )
    public void complexAnalysis(DataEvent<String> input, WorkflowContext context) {
        // Complex analysis via LLM
    }
    
    @LLMRequest(
        prompt = """
            Provide a brief analysis of this document:
            {{input.data}}
            
            Focus on the main points and conclusions.
            """,
        modelName = "gpt-3.5-turbo",
        nextStep = "validateAnalysis"
    )
    public void simpleAnalysis(DataEvent<String> input, WorkflowContext context) {
        // Simple analysis via LLM
    }
    
    @Step(name = "validateAnalysis")
    public DataEvent<String> validateAnalysis(DataEvent<String> input, WorkflowContext context) {
        String analysis = input.getData();
        
        // Validation logic
        if (analysis.length() < 100) {
            throw new IllegalStateException("Analysis too short, retrying");
        }
        
        return DataEvent.of(analysis, "generateReport");
    }
    
    @FinalStep(name = "generateReport")
    public StopEvent<String> generateReport(DataEvent<String> input, WorkflowContext context) {
        String analysis = input.getData();
        String originalContent = context.get("originalContent", String.class);
        
        String report = String.format(
            "Document Review Report\n" +
            "=====================\n" +
            "Original Length: %d characters\n" +
            "Analysis:\n%s",
            originalContent.length(),
            analysis
        );
        
        return StopEvent.of(report);
    }
}
```

### External Workflow Integration

```java
@Component
public class MasterWorkflow extends ExecutableWorkflow<StartEvent, String> {
    
    @Step(name = "preprocessData")
    public DataEvent<String> preprocessData(StartEvent event, WorkflowContext context) {
        // Preprocessing logic
        String data = event.getData();
        String preprocessed = data.trim().toLowerCase();
        
        return DataEvent.of(preprocessed, "callSubWorkflow");
    }
    
    @Step(name = "callSubWorkflow")
    public ExternalEvent callSubWorkflow(DataEvent<String> input, WorkflowContext context) {
        // Create input for sub-workflow
        StartEvent subWorkflowInput = new StartEvent() {
            public String getData() { return input.getData(); }
        };
        
        return new ExternalEvent(
            "sentiment-analysis-workflow", 
            subWorkflowInput, 
            "processSubResult"
        );
    }
    
    @Step(name = "processSubResult")
    public DataEvent<String> processSubResult(DataEvent<String> input, WorkflowContext context) {
        String subResult = input.getData();
        context.put("subWorkflowResult", subResult);
        
        return DataEvent.of(subResult, "finalizeResult");
    }
    
    @FinalStep(name = "finalizeResult")
    public StopEvent<String> finalizeResult(DataEvent<String> input, WorkflowContext context) {
        String result = "Master workflow completed. Sub-result: " + input.getData();
        return StopEvent.of(result);
    }
}
```

## Configuration

### Workflow Registration

```java
@Configuration
public class WorkflowConfiguration {
    
    @Bean
    public WorkflowRegistry workflowRegistry() {
        WorkflowRegistry registry = new WorkflowRegistry();
        
        // Register workflows
        registry.registerWorkflow(
            "data-processing",
            "Data Processing Workflow",
            "Processes and validates input data",
            new DataProcessingWorkflow()
        );
        
        registry.registerWorkflow(
            "sentiment-analysis",
            "Sentiment Analysis Workflow", 
            "Analyzes sentiment and generates responses",
            new SentimentAnalysisWorkflow()
        );
        
        return registry;
    }
}
```

### Application Configuration

```yaml
driftkit:
  workflows:
    enabled: true
    maxExecutionDepth: 1000
    defaultRetryPolicy:
      maxAttempts: 3
      initialDelay: 1000
      backoffMultiplier: 2.0
    
  vault:
    - name: "primary-openai"
      type: "openai"
      apiKey: "${OPENAI_API_KEY}"
      model: "gpt-4"
      temperature: 0.7
      
spring:
  data:
    mongodb:
      uri: "${MONGODB_URI}"
      database: "driftkit"
      
  task:
    execution:
      pool:
        core-size: 5
        max-size: 15
        queue-capacity: 1000
```

## Testing

### Unit Tests

```java
@ExtendWith(MockitoExtension.class)
class WorkflowTest {
    
    @Mock
    private ModelClient mockModelClient;
    
    @Test
    void shouldExecuteWorkflowSuccessfully() throws Exception {
        DataProcessingWorkflow workflow = new DataProcessingWorkflow();
        WorkflowContext context = new WorkflowContext();
        StartEvent startEvent = new StartEvent() {
            public String getData() { return "test input"; }
        };
        
        StopEvent<String> result = workflow.execute(startEvent, context);
        
        assertThat(result.getResult()).isEqualTo("RESULT: TEST_INPUT");
        assertThat(context.get("validatedInput", String.class)).isEqualTo("test input");
        assertThat(context.get("processedData", String.class)).isEqualTo("TEST_INPUT");
    }
    
    @Test
    void shouldHandleRetryOnFailure() throws Exception {
        // Test retry mechanism
        WorkflowContext context = new WorkflowContext();
        
        // Configure to fail first attempt, succeed on second
        AtomicInteger attemptCount = new AtomicInteger(0);
        
        // Verify retry behavior
        assertThat(attemptCount.get()).isEqualTo(2);
    }
}
```

### Integration Tests

```java
@SpringBootTest
@TestPropertySource(properties = {
    "driftkit.workflows.enabled=true"
})
class WorkflowIntegrationTest {
    
    @Autowired
    private WorkflowRegistry workflowRegistry;
    
    @Test
    void shouldRegisterAndExecuteWorkflow() throws Exception {
        // Verify workflow registration
        assertThat(workflowRegistry.hasWorkflow("data-processing")).isTrue();
        
        // Execute workflow
        StartEvent startEvent = new StartEvent() {
            public String getData() { return "integration test"; }
        };
        
        StopEvent<?> result = workflowRegistry.executeWorkflow(
            "data-processing", 
            startEvent, 
            new WorkflowContext()
        );
        
        assertThat(result.getResult()).isEqualTo("RESULT: INTEGRATION_TEST");
    }
}
```

## Performance Considerations

### Thread Pool Configuration

```java
@Configuration
public class AsyncConfiguration {
    
    @Bean(name = "workflowExecutor")
    public ThreadPoolTaskExecutor workflowExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("workflow-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
    
    @Bean(name = "traceExecutor")
    public ThreadPoolTaskExecutor traceExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("trace-");
        executor.initialize();
        return executor;
    }
}
```

### Memory Management

```java
@Service
public class WorkflowMemoryManager {
    
    private final Cache<String, WorkflowContext> contextCache;
    
    public WorkflowMemoryManager() {
        this.contextCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .removalListener(this::onContextRemoval)
            .build();
    }
    
    private void onContextRemoval(String key, WorkflowContext context, RemovalCause cause) {
        if (context != null) {
            // Cleanup resources
            context.clear();
        }
    }
    
    public void manageContext(String workflowId, WorkflowContext context) {
        contextCache.put(workflowId, context);
    }
}
```

This comprehensive documentation covers all aspects of the driftkit-workflows module, providing detailed information about workflow orchestration, event-driven execution, AI model integration, and Spring Boot services.

## Demo Examples

### 1. Chat Workflow with External Router

This example shows how to use external workflows and manage chat context.

```java
@Component
public class ChatWorkflow extends ModelWorkflow<ChatStartEvent, ChatResult> {
    
    private final ChatService chatService;
    
    public ChatWorkflow(EtlConfig config, 
                       PromptService promptService,
                       ModelRequestService modelRequestService,
                       ChatService chatService) throws Exception {
        super(ModelClientFactory.fromConfig(
            config.getModelConfig(OpenAIModelClient.OPENAI_PREFIX).orElseThrow()),
            modelRequestService,
            promptService);
        this.chatService = chatService;
    }
    
    @Step(name = "start")
    @StepInfo(description = "Starting step of the workflow")
    public ExternalEvent<RouterStartEvent> start(ChatStartEvent startEvent, 
                                                WorkflowContext context) {
        MessageTask task = startEvent.getTask();
        
        // Get or create chat
        Chat chat = chatService.getOrCreateChat(task.getChatId());
        
        // Store in context
        context.put("query", task.getMessage());
        context.put("currentMessage", task);
        context.put("chatId", chat.getChatId());
        
        // Call external RouterWorkflow
        RouterStartEvent routerInput = RouterStartEvent.builder()
            .messages(chatService.getChatHistory(chat.getChatId()))
            .customRoutes(startEvent.getRoutes())
            .build();
        
        return new ExternalEvent<>(
            RouterWorkflow.class,
            routerInput,
            "processRouterResult"
        );
    }
    
    @Step(name = "processRouterResult")
    @StepInfo(description = "Process router decision")
    public WorkflowEvent processRouterResult(DataEvent<RouterResult> routerResult, 
                                           WorkflowContext context) {
        RouterResult result = routerResult.getData();
        String query = context.get("query", String.class);
        
        // Check routing decision
        if (result.isInputType(RouterDefaultInputTypes.IMAGE_GENERATION)) {
            // Route to image generation
            return DataEvent.of(result, "generateImage");
        } else if (result.hasRelatedDocuments()) {
            // Use RAG with context
            return DataEvent.of(result, "generateWithContext");
        } else {
            // Standard response
            return DataEvent.of(result, "generateStandardResponse");
        }
    }
    
    @Step(name = "generateWithContext")
    public DataEvent<String> generateWithContext(DataEvent<RouterResult> input,
                                               WorkflowContext context) {
        RouterResult routerResult = input.getData();
        
        // Build context from related documents
        String contextText = routerResult.getRelatedDocs().stream()
            .flatMap(docs -> docs.getDocuments().stream())
            .map(Document::getPageContent)
            .collect(Collectors.joining("\n\n"));
        
        Map<String, Object> variables = Map.of(
            "query", context.get("query"),
            "context", contextText
        );
        
        // Use RAG prompt
        ModelTextResponse response = sendTextToText(
            ModelRequestParams.create()
                .setPromptId("chat_with_context")
                .setVariables(variables),
            context
        );
        
        return DataEvent.of(response.getResponse(), "saveAndFinish");
    }
    
    @Step(name = "generateStandardResponse")
    public DataEvent<String> generateStandardResponse(DataEvent<RouterResult> input,
                                                    WorkflowContext context) {
        String query = context.get("query", String.class);
        
        ModelTextResponse response = sendPromptText(
            ModelRequestParams.create()
                .setPromptText(query),
            context
        );
        
        return DataEvent.of(response.getResponse(), "saveAndFinish");
    }
    
    @Step(name = "generateImage")
    public DataEvent<String> generateImage(DataEvent<RouterResult> input,
                                         WorkflowContext context) {
        // Simple image generation placeholder
        String imageId = UUID.randomUUID().toString();
        context.put("imageId", imageId);
        
        return DataEvent.of("Generated image: " + imageId, "saveAndFinish");
    }
    
    @Step(name = "saveAndFinish")
    public StopEvent<ChatResult> saveAndFinish(DataEvent<String> input,
                                              WorkflowContext context) {
        String response = input.getData();
        
        // Save to chat history
        chatService.saveMessage(context.get("chatId", String.class), response);
        
        ChatResult result = ChatResult.builder()
            .response(response)
            .imageId(context.get("imageId", String.class))
            .build();
        
        return StopEvent.of(result);
    }
    
    @Data
    @Builder
    public static class ChatStartEvent implements StartEvent {
        private MessageTask task;
        private List<Route> routes;
    }
    
    @Data
    @Builder
    public static class ChatResult {
        private String response;
        private String imageId;
    }
}
```

### 2. Router Workflow with Retry Logic

This example demonstrates conditional routing and retry with different models.

```java
@Component
public class RouterWorkflow extends ModelWorkflow<RouterStartEvent, RouterResult> {
    
    private final RAGSearchWorkflow searchWorkflow;
    private final VaultConfig modelConfig;
    
    public RouterWorkflow(EtlConfig config, 
                         PromptService promptService,
                         RAGSearchWorkflow searchWorkflow,
                         ModelRequestService modelRequestService) throws Exception {
        super(ModelClientFactory.fromConfig(
            config.getModelConfig(OpenAIModelClient.OPENAI_PREFIX).orElseThrow()),
            modelRequestService,
            promptService);
        this.searchWorkflow = searchWorkflow;
        this.modelConfig = config.getModelConfig(OpenAIModelClient.OPENAI_PREFIX).orElseThrow();
    }
    
    @Step(name = "start")
    @StepInfo(description = "Route message based on intent")
    public WorkflowEvent start(RouterStartEvent startEvent, 
                              WorkflowContext context) throws Exception {
        // Try with mini model first
        String model = modelConfig.getModelMini();
        
        Map<String, Object> variables = Map.of(
            "query", startEvent.getQuery(),
            "history", startEvent.getMessages(),
            "customRoutes", startEvent.getCustomRoutes()
        );
        
        ModelTextResponse response = sendTextToText(
            ModelRequestParams.create()
                .setPromptId("router")
                .setVariables(variables)
                .setModel(model),
            context
        );
        
        RouterResult routerResult = response.getResponseJson(RouterResult.class);
        
        // Validate result
        if (needsRetry(routerResult, startEvent)) {
            return DataEvent.of(startEvent, "retry");
        }
        
        return DataEvent.of(routerResult, "enhanceWithSearch");
    }
    
    @Step(name = "retry")
    @StepInfo(description = "Retry with smarter model")
    public DataEvent<RouterResult> retry(DataEvent<RouterStartEvent> input,
                                       WorkflowContext context) throws Exception {
        RouterStartEvent startEvent = input.getData();
        
        // Use full model for retry
        String model = modelConfig.getModel();
        
        Map<String, Object> variables = Map.of(
            "query", startEvent.getQuery(),
            "history", startEvent.getMessages(),
            "customRoutes", startEvent.getCustomRoutes()
        );
        
        ModelTextResponse response = sendTextToText(
            ModelRequestParams.create()
                .setPromptId("router")
                .setVariables(variables)
                .setModel(model),
            context
        );
        
        RouterResult routerResult = response.getResponseJson(RouterResult.class);
        return DataEvent.of(routerResult, "enhanceWithSearch");
    }
    
    @Step(name = "enhanceWithSearch")
    @StepInfo(description = "Search relevant documents if needed")
    public StopEvent<RouterResult> enhanceWithSearch(DataEvent<RouterResult> input,
                                                   WorkflowContext context) throws Exception {
        RouterResult routerResult = input.getData();
        
        // If RAG is needed, search for documents
        if (routerResult.isOutputType(RouterDefaultOutputTypes.RAG)) {
            List<DocumentsResult> relatedDocs = new ArrayList<>();
            
            for (String indexName : routerResult.getRecommendedIndexes()) {
                VectorStoreStartEvent searchInput = VectorStoreStartEvent.builder()
                    .query(context.get("query", String.class))
                    .indexName(indexName)
                    .limit(5)
                    .build();
                
                StopEvent<DocumentsResult> searchResult = 
                    searchWorkflow.execute(searchInput, context);
                
                relatedDocs.add(searchResult.getData());
            }
            
            routerResult.setRelatedDocs(relatedDocs);
        }
        
        return StopEvent.of(routerResult);
    }
    
    private boolean needsRetry(RouterResult result, RouterStartEvent event) {
        // Simple validation logic
        return result.getInputTypes().isEmpty() || 
               (result.isInputType(RouterDefaultInputTypes.CUSTOM) && 
                !result.matchesCustomRoutes(event.getCustomRoutes()));
    }
    
    @Data
    @Builder
    public static class RouterStartEvent implements StartEvent {
        private List<Message> messages;
        private List<Route> customRoutes;
        
        public String getQuery() {
            return messages.isEmpty() ? "" : messages.get(messages.size() - 1).getMessage();
        }
    }
    
    @Data
    public static class RouterResult {
        private Set<RouterDecision<RouterDefaultInputTypes>> inputTypes;
        private Set<RouterDecision<RouterDefaultOutputTypes>> routes;
        private List<String> recommendedIndexes;
        private List<DocumentsResult> relatedDocs;
        
        public boolean isInputType(RouterDefaultInputTypes type) {
            return inputTypes.stream().anyMatch(d -> d.getDecision() == type);
        }
        
        public boolean isOutputType(RouterDefaultOutputTypes type) {
            return routes.stream().anyMatch(d -> d.getDecision() == type);
        }
        
        public boolean hasRelatedDocuments() {
            return relatedDocs != null && !relatedDocs.isEmpty();
        }
        
        public boolean matchesCustomRoutes(List<Route> customRoutes) {
            // Validation logic
            return true;
        }
    }
    
    public enum RouterDefaultInputTypes {
        GREETING, INFORMATION_REQUEST, IMAGE_GENERATION, CUSTOM
    }
    
    public enum RouterDefaultOutputTypes {
        RAG, CHAT, SUPPORT_REQUEST
    }
}
```

### 3. RAG Search Workflow with Reranking

This example shows a three-step semantic search with reranking.

```java
@Component
public class RAGSearchWorkflow extends ModelWorkflow<VectorStoreStartEvent, DocumentsResult> {
    
    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;
    
    public RAGSearchWorkflow(EtlConfig config,
                           PromptService promptService,
                           ModelRequestService modelRequestService) throws Exception {
        super(ModelClientFactory.fromConfig(
            config.getModelConfig(OpenAIModelClient.OPENAI_PREFIX).orElseThrow()),
            modelRequestService,
            promptService);
        
        this.embeddingModel = EmbeddingFactory.fromName(
            config.getEmbedding().getName(),
            config.getEmbedding().getConfig()
        );
        this.vectorStore = VectorStoreFactory.fromConfig(config.getVectorStore());
    }
    
    @Step
    @StepInfo(description = "Search vector store")
    public WorkflowEvent start(VectorStoreStartEvent startEvent, 
                              WorkflowContext context) throws Exception {
        String query = startEvent.getQuery();
        
        // Embed query
        Response<Embedding> embedding = embeddingModel.embed(TextSegment.from(query));
        float[] queryVector = embedding.content().vector();
        
        // Search vector store
        DocumentsResult documents = vectorStore.findRelevant(
            startEvent.getIndexName(), 
            queryVector, 
            startEvent.getLimit()
        );
        
        context.put("query", query);
        context.put("retrievedDocuments", documents);
        
        if (documents.isEmpty()) {
            return StopEvent.of(documents);
        }
        
        return DataEvent.of(documents, "rerank");
    }
    
    @Step
    @StepInfo(description = "Rerank documents using LLM")
    public DataEvent<DocumentsResult> rerank(DataEvent<DocumentsResult> input,
                                           WorkflowContext context) throws Exception {
        DocumentsResult documents = input.getData();
        String query = context.get("query", String.class);
        
        // Prepare documents for reranking
        Map<String, Document> docMap = new HashMap<>();
        StringBuilder docsText = new StringBuilder();
        
        for (Document doc : documents.documents()) {
            docMap.put(doc.getId(), doc);
            docsText.append("ID: ").append(doc.getId())
                   .append("\nContent: ").append(doc.getPageContent())
                   .append("\n\n");
        }
        
        // Ask LLM to rerank
        Map<String, Object> variables = Map.of(
            "query", query,
            "documents", docsText.toString()
        );
        
        ModelTextResponse response = sendTextToText(
            ModelRequestParams.create()
                .setPromptId("rerank")
                .setVariables(variables),
            context
        );
        
        // Parse reranking scores
        Map<String, Float> scores = parseScores(response.getResponse());
        
        // Create reranked result
        DocumentsResult reranked = new DocumentsResult();
        scores.entrySet().stream()
            .sorted(Map.Entry.<String, Float>comparingByValue().reversed())
            .forEach(entry -> {
                Document doc = docMap.get(entry.getKey());
                if (doc != null) {
                    reranked.put(doc, entry.getValue());
                }
            });
        
        return DataEvent.of(reranked, "finalStep");
    }
    
    @Step
    @StepInfo(description = "Return final results")
    public StopEvent<DocumentsResult> finalStep(DataEvent<DocumentsResult> event,
                                              WorkflowContext context) {
        return StopEvent.of(event.getData());
    }
    
    private Map<String, Float> parseScores(String response) {
        // Parse JSON scores from LLM response
        try {
            return new ObjectMapper().readValue(response, 
                new TypeReference<Map<String, Float>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
    
    @Data
    @Builder
    public static class VectorStoreStartEvent implements StartEvent {
        private String indexName;
        private String query;
        private int limit;
    }
}
```

### 4. Document Processing Workflow

This example shows parallel processing with retry logic.

```java
@Component
public class RAGModifyWorkflow extends ModelWorkflow<DocumentInput, DocumentSaveResult> {
    
    private final DocumentParser documentParser;
    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;
    private final ExecutorService executorService;
    
    public RAGModifyWorkflow(EtlConfig config,
                           PromptService promptService,
                           ModelRequestService modelRequestService) throws Exception {
        super(ModelClientFactory.fromConfig(
            config.getModelConfig(OpenAIModelClient.OPENAI_PREFIX).orElseThrow()),
            modelRequestService,
            promptService);
        
        this.documentParser = new DocumentParser();
        this.vectorStore = VectorStoreFactory.fromConfig(config.getVectorStore());
        this.embeddingModel = EmbeddingFactory.fromName(
            config.getEmbedding().getName(),
            config.getEmbedding().getConfig()
        );
        this.executorService = Executors.newFixedThreadPool(5);
    }
    
    @Step(name = "parseDocument")
    @StepInfo(description = "Parse various document types")
    public DataEvent<ParsedContent> parseDocument(DocumentInput input,
                                                WorkflowContext context) {
        context.put("documentId", input.getDocumentId());
        context.put("indexName", input.getIndexName());
        
        ParsedContent parsed;
        switch (input.getType()) {
            case YOUTUBE:
                parsed = documentParser.parseYouTube(input.getSource());
                break;
            case URL:
                parsed = documentParser.parseUrl(input.getSource());
                break;
            case TEXT:
                parsed = ParsedContent.of(input.getSource());
                break;
            default:
                throw new IllegalArgumentException("Unknown type: " + input.getType());
        }
        
        return DataEvent.of(parsed, "ingestDocument");
    }
    
    @Step(name = "ingestDocument", invocationLimit = 3)
    @StepInfo(description = "Chunk and embed document")
    public StopEvent<DocumentSaveResult> ingestDocument(DataEvent<ParsedContent> input,
                                                      WorkflowContext context) {
        ParsedContent content = input.getData();
        
        try {
            // Split into chunks
            List<String> chunks = DocumentSplitter.builder()
                .maxChunkSize(1000)
                .overlapSize(200)
                .build()
                .split(content.getContent());
            
            // Parallel embedding
            List<Document> documents = new CopyOnWriteArrayList<>();
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            
            for (int i = 0; i < chunks.size(); i++) {
                final int index = i;
                final String chunk = chunks.get(i);
                
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        // Embed chunk
                        Response<Embedding> embedding = embeddingModel.embed(
                            TextSegment.from(chunk)
                        );
                        
                        // Create document
                        Document doc = Document.builder()
                            .id(context.get("documentId") + "_" + index)
                            .pageContent(chunk)
                            .embedding(embedding.content().vector())
                            .metadata(Map.of(
                                "documentId", context.get("documentId"),
                                "chunkIndex", index
                            ))
                            .build();
                        
                        documents.add(doc);
                    } catch (Exception e) {
                        throw new RuntimeException("Embedding failed", e);
                    }
                }, executorService);
                
                futures.add(future);
            }
            
            // Wait for completion
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(2, TimeUnit.MINUTES);
            
            // Store in vector database
            String indexName = context.get("indexName", String.class);
            List<String> storedIds = vectorStore.addDocuments(indexName, documents);
            
            DocumentSaveResult result = DocumentSaveResult.builder()
                .documentId(context.get("documentId", String.class))
                .chunksStored(storedIds.size())
                .indexName(indexName)
                .build();
            
            return StopEvent.of(result);
            
        } catch (Exception e) {
            context.addCounter("ingestDocument", 1);
            if (context.getCounter("ingestDocument") >= 3) {
                return StopEvent.of(DocumentSaveResult.failed(
                    context.get("documentId", String.class),
                    e.getMessage()
                ));
            }
            throw new RuntimeException("Ingestion failed, will retry", e);
        }
    }
    
    @Data
    @Builder
    public static class DocumentInput implements StartEvent {
        private String documentId;
        private String source;
        private DocumentType type;
        private String indexName;
    }
    
    public enum DocumentType {
        YOUTUBE, URL, TEXT, FILE
    }
    
    @Data
    @Builder
    public static class ParsedContent {
        private String content;
        private Map<String, Object> metadata;
        
        public static ParsedContent of(String content) {
            return ParsedContent.builder()
                .content(content)
                .metadata(new HashMap<>())
                .build();
        }
    }
    
    @Data
    @Builder
    public static class DocumentSaveResult {
        private String documentId;
        private int chunksStored;
        private String indexName;
        private String error;
        
        public static DocumentSaveResult failed(String documentId, String error) {
            return DocumentSaveResult.builder()
                .documentId(documentId)
                .error(error)
                .build();
        }
    }
}
```

### 5. Multi-Step Reasoning Workflow

This example shows iterative processing with invocation limits.

```java
@Component
public class ReasoningWorkflow extends ModelWorkflow<StartEvent, JsonNode> {
    
    public ReasoningWorkflow(EtlConfig config,
                           PromptService promptService,
                           ModelRequestService modelRequestService) throws Exception {
        super(ModelClientFactory.fromConfig(
            config.getModelConfig(OpenAIModelClient.OPENAI_PREFIX).orElseThrow()),
            modelRequestService,
            promptService);
    }
    
    @Step(name = "start")
    public DataEvent<JsonNode> start(StartEvent startEvent, WorkflowContext context) {
        String query;
        if (startEvent instanceof StartQueryEvent) {
            query = ((StartQueryEvent) startEvent).getQuery();
        } else if (startEvent instanceof LLMRequestEvent) {
            query = ((LLMRequestEvent) startEvent).getMessageTask().getMessage();
        } else {
            throw new IllegalArgumentException("Unsupported event type");
        }
        
        context.put("query", query);
        context.put("conversation", new ArrayList<Message>());
        
        return DataEvent.of(null, "reason");
    }
    
    @Step(name = "reason", invocationLimit = 10, 
          onInvocationsLimit = OnInvocationsLimit.STOP)
    @StepInfo(description = "Iterative reasoning step")
    public WorkflowEvent reason(DataEvent<JsonNode> input, WorkflowContext context) {
        List<Message> conversation = context.get("conversation", List.class);
        String query = context.get("query", String.class);
        
        // Build prompt with conversation history
        Map<String, Object> variables = Map.of(
            "query", query,
            "conversation", conversation,
            "step", context.getCounter("reason")
        );
        
        ModelTextResponse response = sendTextToText(
            ModelRequestParams.create()
                .setPromptId("reasoning_step")
                .setVariables(variables),
            context
        );
        
        JsonNode result = parseJson(response.getResponse());
        
        // Add to conversation
        conversation.add(Message.of("assistant", result.toString()));
        
        // Check if we should continue
        String action = result.get("action").asText();
        if ("continue".equals(action)) {
            return DataEvent.of(result, "reason");
        } else if ("verify".equals(action)) {
            return DataEvent.of(result, "selfCheck");
        } else {
            return StopEvent.of(result);
        }
    }
    
    @Step(name = "selfCheck")
    @StepInfo(description = "Verify reasoning result")
    public WorkflowEvent selfCheck(DataEvent<JsonNode> input, WorkflowContext context) {
        JsonNode reasoning = input.getData();
        
        Map<String, Object> variables = Map.of(
            "reasoning", reasoning,
            "query", context.get("query")
        );
        
        ModelTextResponse response = sendTextToText(
            ModelRequestParams.create()
                .setPromptId("verify_reasoning")
                .setVariables(variables),
            context
        );
        
        JsonNode verification = parseJson(response.getResponse());
        
        if (verification.get("valid").asBoolean()) {
            return StopEvent.of(reasoning);
        } else {
            // Retry reasoning
            context.put("verificationFeedback", verification.get("feedback"));
            return DataEvent.of(null, "reason");
        }
    }
    
    private JsonNode parseJson(String response) {
        try {
            return new ObjectMapper().readTree(response);
        } catch (Exception e) {
            return new ObjectMapper().createObjectNode()
                .put("error", "Failed to parse response")
                .put("raw", response);
        }
    }
}
```

These simplified examples focus on the core workflow patterns:

1. **Chat Workflow** - External workflow calls, conditional routing
2. **Router Workflow** - Retry logic, model switching, RAG integration
3. **RAG Search** - Three-step process, LLM reranking
4. **Document Processing** - Parallel processing, retry on failure
5. **Reasoning Workflow** - Iterative steps, invocation limits, self-checking

Key patterns demonstrated:
- Using `@Step` and `@StepInfo` annotations
- `ExternalEvent` for calling other workflows
- `DataEvent` for passing data between steps
- `StopEvent` for workflow completion
- `WorkflowContext` for state management
- Conditional routing based on data
- Retry mechanisms and invocation limits
- Integration with ModelWorkflow for LLM calls The module offers a powerful foundation for building sophisticated AI workflows with advanced features like conditional branching, retry mechanisms, and external workflow composition.
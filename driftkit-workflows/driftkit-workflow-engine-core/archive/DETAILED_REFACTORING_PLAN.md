# Detailed Workflow Engine Refactoring Plan

## 1. StepResult SDK Simplification - Static Factory Methods

### Current Complex Pattern:
```java
// Current - verbose and error-prone
return new StepResult.Suspend<>(promptData, nextInputClass, metadata);
return new StepResult.Async<>(taskId, 30000, args, immediateData);
return new StepResult.Finish<>(result);
return new StepResult.Fail<>(new Exception("error"));
```

### New Simplified Pattern (like StepEvent.of):
```java
// New - concise and intuitive
return StepResult.suspend(promptData, UserInput.class);
return StepResult.async("searchTask", 30_000, searchNotification);
return StepResult.finish(result);
return StepResult.fail("Error message");
return StepResult.continue(nextData);
return StepResult.branch(routingEvent);
```

### Implementation:
```java
public sealed interface StepResult<T> {
    
    // Static factory methods for better SDK experience
    
    static <T> Suspend<T> suspend(T promptToUser, Class<?> nextInputClass) {
        SchemaProvider provider = WorkflowEngineHolder.getSchemaProvider();
        AIFunctionSchema schema = provider.generateSchema(nextInputClass);
        return new Suspend<>(promptToUser, nextInputClass, schema, new HashMap<>());
    }
    
    static <T> Suspend<T> suspend(T promptToUser, Class<?> nextInputClass, Map<String, Object> metadata) {
        SchemaProvider provider = WorkflowEngineHolder.getSchemaProvider();
        AIFunctionSchema schema = provider.generateSchema(nextInputClass);
        return new Suspend<>(promptToUser, nextInputClass, schema, metadata);
    }
    
    static <T> Async<T> async(String taskId, long estimatedMs, T immediateData) {
        return new Async<>(taskId, estimatedMs, new HashMap<>(), immediateData);
    }
    
    static <T> Async<T> async(String taskId, long estimatedMs, Map<String, Object> taskArgs, T immediateData) {
        return new Async<>(taskId, estimatedMs, taskArgs, immediateData);
    }
    
    static <T> Finish<T> finish(T result) {
        return new Finish<>(result);
    }
    
    static <T> Fail<T> fail(String message) {
        return new Fail<>(new RuntimeException(message));
    }
    
    static <T> Fail<T> fail(Throwable error) {
        return new Fail<>(error);
    }
    
    static <T> Continue<T> continueWith(T data) {
        return new Continue<>(data);
    }
    
    static <T> Branch<T> branch(T event) {
        return new Branch<>(event);
    }
    
    // Records remain the same but constructors can be package-private
    record Suspend<T>(
        T promptToUser,
        Class<?> nextInputClass,
        AIFunctionSchema nextInputSchema,
        Map<String, Object> metadata
    ) implements StepResult<T> {}
    
    // Other records...
}
```

### Benefits:
- Cleaner API similar to familiar StepEvent.of
- Schema generation handled automatically
- Type inference works better
- Less boilerplate in workflow code

## 2. Delete TODELETE Classes - Concrete Actions

### Classes to Delete:
1. **AnnotationScanner.java** - DELETE ENTIRELY
   - All methods already in WorkflowAnalyzer.discoverSteps()
   
2. **GraphBuilder.java** - DELETE ENTIRELY
   - All methods already in WorkflowAnalyzer.buildEdges/buildNodes()
   
3. **TypeAnalyzer.java** - EXTRACT then DELETE
   ```java
   // Extract these 3 methods to TypeUtils.java:
   public static Class<?> extractStepResultType(Type type) { ... }
   public static boolean isTypeCompatible(Class<?> from, Class<?> to) { ... }
   public static boolean isFinishType(Type rawType) { ... }
   ```
   
4. **WorkflowValidator.java** - MOVE then DELETE
   ```java
   // Move validateAsyncStepMethod() to WorkflowAnalyzer as private method
   ```

### Fix Compilation Errors:
```java
// WorkflowAnalyzer.java line 221-223
// DELETE these lines - methods don't exist in @Step
- boolean requiresUserInput = step.requiresUserInput();
- boolean trackProgress = step.trackProgress();
```

## 3. Graph Model Simplification - Keep Branching, Remove Complexity

### Current Over-Complex Model:
```java
// Current - too many abstractions
WorkflowGraph -> StepNode -> Edge -> EdgeType -> Predicate -> TypeMatcher
```

### Simplified Model:
```java
// New - direct execution with branching support
public class SimpleWorkflowExecutor {
    
    // Direct step execution with type-based routing
    public StepResult<?> executeStep(String stepId, Object input, WorkflowContext context) {
        StepMethod step = steps.get(stepId);
        StepResult<?> result = step.invoke(input, context);
        
        // Handle routing based on result type
        return switch (result) {
            case Continue<?> cont -> {
                // Find next step by type matching
                String nextStep = findStepByInputType(cont.data().getClass());
                if (nextStep != null) {
                    yield executeStep(nextStep, cont.data(), context);
                }
                yield result;
            }
            case Branch<?> branch -> {
                // Type-based branching preserved!
                String nextStep = findStepByInputType(branch.event().getClass());
                if (nextStep != null) {
                    yield executeStep(nextStep, branch.event(), context);
                }
                yield result;
            }
            case Suspend<?>, Async<?>, Finish<?>, Fail<?> -> result;
        };
    }
    
    // Simple type matching for branching
    private String findStepByInputType(Class<?> outputType) {
        return steps.entrySet().stream()
            .filter(e -> e.getValue().acceptsInput(outputType))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);
    }
}
```

### What We Keep:
- ✅ Type-based branching (Branch<T>)
- ✅ Conditional routing (if needed)
- ✅ Parallel execution support (future)
- ✅ Step metadata and annotations

### What We Remove:
- ❌ Complex graph traversal algorithms
- ❌ Edge predicates and conditions
- ❌ Node wrappers around methods
- ❌ Abstract graph validation

### Migration Example:
```java
// Before - complex graph
@Step(id = "processInput", nextClasses = {ValidationResult.class, ErrorResult.class})
public StepResult<OutputEvent> process(InputEvent input) {
    if (isValid(input)) {
        return new StepResult.Branch<>(new ValidationResult(input));
    } else {
        return new StepResult.Branch<>(new ErrorResult("Invalid"));
    }
}

// After - same functionality, simpler execution
@Step("processInput")
public StepResult<OutputEvent> process(InputEvent input) {
    if (isValid(input)) {
        return StepResult.branch(new ValidationResult(input));
    } else {
        return StepResult.branch(new ErrorResult("Invalid"));
    }
}
// Engine automatically routes based on type - no explicit nextClasses needed!
```

## 4. Move WorkflowService to Core

### Current Structure:
```
driftkit-workflow-engine-spring-boot-starter/
  └── WorkflowService.java (❌ Wrong location)
  
driftkit-workflow-engine-core/
  └── (Missing service layer)
```

### New Structure:
```
driftkit-workflow-engine-core/
  ├── service/
  │   ├── WorkflowExecutionService.java (interface)
  │   ├── DefaultWorkflowExecutionService.java (implementation)
  │   └── ChatWorkflowService.java (chat-specific logic)
  
driftkit-workflow-engine-spring-boot-starter/
  └── WorkflowServiceAdapter.java (thin Spring wrapper)
```

### Implementation:
```java
// Core service interface
public interface WorkflowExecutionService {
    // Chat workflow execution
    ChatResponse executeChat(ChatRequest request);  // workflowId is in request
    ChatResponse resumeChat(String messageId, ChatRequest request);
    ChatResponse getAsyncStatus(String messageId);
    
    // Session management
    ChatSession getOrCreateSession(String chatId, String userId);
    Page<ChatMessage> getChatHistory(String chatId, Pageable pageable);
    
    // Workflow management
    List<WorkflowMetadata> listWorkflows();
    AIFunctionSchema getInitialSchema(String workflowId);
}

// Spring adapter (thin wrapper)
@Service
public class WorkflowServiceAdapter implements WorkflowService {
    private final WorkflowExecutionService core;
    
    // Delegates all calls to core service
    public ChatResponse processChatRequest(ChatRequest request) {
        return core.executeChat(request);  // workflowId already in request
    }
}
```

## 5. Fix Workflow Execution - Stop at Suspend/Async

### Current Problem:
```java
// Workflows run to completion ignoring suspend points
public Object execute(String workflowId, Object input) {
    while (!workflow.isComplete()) {
        result = step.execute();
        // WRONG: Continues past suspend/async
    }
    return result;
}
```

### Fixed Implementation:
```java
public class WorkflowEngine {
    
    public StepResult<?> executeForChat(ChatRequest request) {
        String workflowId = request.getWorkflowId();  // Get from request
        WorkflowInstance instance = createOrResumeInstance(workflowId, request);
        
        return executeUntilInteractionPoint(instance, request);
    }
    
    private StepResult<?> executeUntilInteractionPoint(WorkflowInstance instance, Object input) {
        while (true) {
            StepResult<?> result = executeCurrentStep(instance, input);
            
            switch (result) {
                case Suspend<?> suspend -> {
                    // STOP HERE - waiting for user input
                    instance.setStatus(SUSPENDED);
                    stateRepository.save(instance);
                    return suspend;
                }
                
                case Async<?> async -> {
                    // STOP HERE - async processing
                    AsyncStepState state = AsyncStepState.started(
                        async.taskId(), 
                        async.immediateData()
                    );
                    instance.addAsyncState(state);
                    instance.setStatus(ASYNC);
                    stateRepository.save(instance);
                    
                    // Schedule async execution
                    asyncExecutor.execute(() -> executeAsyncStep(instance, async));
                    
                    return async;
                }
                
                case Finish<?> finish -> {
                    // STOP HERE - workflow complete
                    instance.setStatus(COMPLETED);
                    stateRepository.save(instance);
                    return finish;
                }
                
                case Fail<?> fail -> {
                    // STOP HERE - workflow failed
                    instance.setStatus(FAILED);
                    stateRepository.save(instance);
                    return fail;
                }
                
                case Continue<?> cont -> {
                    // Continue to next step
                    input = cont.data();
                    String nextStepId = findNextStep(cont.data().getClass());
                    instance.setCurrentStepId(nextStepId);
                    // Loop continues
                }
                
                case Branch<?> branch -> {
                    // Branch to next step
                    input = branch.event();
                    String nextStepId = findNextStep(branch.event().getClass());
                    instance.setCurrentStepId(nextStepId);
                    // Loop continues
                }
            }
        }
    }
}
```

## 6. Controller Feature Comparison

### Missing Features in WorkflowController:

1. **WebSocket Support** (AssistantController has it)
   ```java
   @Controller
   public class WorkflowWebSocketController {
       @MessageMapping("/workflow.execute")
       @SendTo("/topic/workflow.updates")
       public ChatResponse executeWorkflow(ChatRequest request) {
           // Real-time updates
       }
   }
   ```

2. **Async Progress Streaming**
   ```java
   @GetMapping(value = "/chat/response/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
   public Flux<ServerSentEvent<ProgressUpdate>> streamProgress(@PathVariable String id) {
       return asyncTracker.getProgressStream(id)
           .map(progress -> ServerSentEvent.<ProgressUpdate>builder()
               .data(progress)
               .build());
   }
   ```

3. **Schema Composition** (multiple schemas per step)
   ```java
   @GetMapping("/schemas/composable")
   public List<AIFunctionSchema> getComposableSchemas(
       @RequestParam String workflowId,
       @RequestParam String stepId
   ) {
       return schemaProvider.generateComposableSchemas(stepClass);
   }
   ```

4. **Workflow Versioning**
   ```java
   @GetMapping("/{workflowId}/versions")
   public List<WorkflowVersion> getVersions(@PathVariable String workflowId) {
       return workflowService.getWorkflowVersions(workflowId);
   }
   ```

5. **Batch Operations**
   ```java
   @PostMapping("/batch/execute")
   public List<ChatResponse> batchExecute(@RequestBody List<ChatRequest> requests) {
       return requests.parallelStream()
           .map(req -> workflowService.processChatRequest(req))
           .toList();
   }
   ```

## 7. Schema Management Improvements

### Port SchemaUtils from chat-assistant-framework:
```java
public class SchemaUtils {
    // Direct port of proven implementation
    public static AIFunctionSchema getSchemaFromClass(Class<?> clazz) {
        SchemaName schemaName = clazz.getAnnotation(SchemaName.class);
        if (schemaName == null) return null;
        
        AIFunctionSchema schema = new AIFunctionSchema();
        schema.setSchemaName(schemaName.value());
        
        // Extract properties
        List<AIFunctionProperty> properties = Arrays.stream(clazz.getDeclaredFields())
            .filter(f -> f.isAnnotationPresent(SchemaProperty.class))
            .map(SchemaUtils::createProperty)
            .toList();
            
        schema.setProperties(properties);
        return schema;
    }
    
    public static Map<String, String> extractProperties(Object obj) {
        // Convert object to property map
        Map<String, String> props = new HashMap<>();
        
        for (Field field : obj.getClass().getDeclaredFields()) {
            SchemaProperty ann = field.getAnnotation(SchemaProperty.class);
            if (ann != null) {
                field.setAccessible(true);
                Object value = field.get(obj);
                if (value != null) {
                    props.put(field.getName(), value.toString());
                }
            }
        }
        
        return props;
    }
}
```

## 8. Async Handling Improvements

### Current Complex Async:
```java
// Too many steps
return new StepResult.Async<>(taskId, 30000, args, immediateData);
// Then AsyncStepState
// Then AsyncStepHandler
// Then ProgressTracker
```

### Simplified Async:
```java
@Step("search")
public StepResult<SearchResult> startSearch(SearchQuery query) {
    // Simple async with progress object
    SearchProgress progress = new SearchProgress("Searching for: " + query.getTerm());
    return StepResult.async("performSearch", 30_000, progress);
}

@AsyncStep("performSearch")
public StepResult<SearchResult> performSearch(
    SearchQuery query,
    AsyncProgressReporter reporter
) {
    // Direct progress updates
    reporter.updateProgress(25, new SearchProgress("Found 10 results"));
    reporter.updateProgress(50, new SearchProgress("Found 50 results"));
    reporter.updateProgress(100, new SearchProgress("Search complete"));
    
    return StepResult.finish(new SearchResult(results));
}
```

## 9. Memory Management from driftkit-common

### Use existing ChatMemoryStore from driftkit-common:

```java
// Already available in driftkit-common
public interface ChatMemoryStore {
    List<Message> getMessages(String id, int limit);
    void updateMessages(String id, List<Message> messages);
    void deleteMessages(String id);
}

// Token-based memory management already implemented
public class TokenWindowChatMemory implements ChatMemory {
    public static final int MESSAGES_LIMIT = 200;
    private final Integer maxTokens;
    private final Tokenizer tokenizer;
    private final ChatMemoryStore store;
    
    // Automatically evicts old messages when token limit exceeded
    private static void ensureCapacity(List<Message> messages, int maxTokens, Tokenizer tokenizer) {
        int currentTokenCount = tokenizer.estimateTokenCountInMessages(messages);
        while (currentTokenCount > maxTokens) {
            // Smart eviction - keeps system messages, removes oldest user/ai messages
            int messageToEvictIndex = messages.get(0).type() == ChatMessageType.SYSTEM ? 1 : 0;
            Message evicted = messages.remove(messageToEvictIndex);
            currentTokenCount -= tokenizer.estimateTokenCountInMessage(evicted);
        }
    }
}
```

### Integration with Workflow Engine:

```java
@Configuration
public class WorkflowMemoryConfiguration {
    
    @Bean
    public ChatMemoryStore workflowChatMemoryStore() {
        // Can use InMemory, Redis, MongoDB, etc.
        return new InMemoryChatMemoryStore();
    }
    
    @Bean
    public ChatMemory workflowChatMemory(
        @Value("${workflow.chat.maxTokens:4096}") int maxTokens,
        Tokenizer tokenizer,
        ChatMemoryStore store
    ) {
        return TokenWindowChatMemory.withMaxTokens(
            "workflow-default",
            maxTokens,
            tokenizer,
            store
        );
    }
}

// In WorkflowExecutionService
public class DefaultWorkflowExecutionService implements WorkflowExecutionService {
    private final ChatMemoryStore memoryStore;
    private final Tokenizer tokenizer;
    private final int maxTokensPerChat;
    
    public ChatResponse executeChat(ChatRequest request) {
        // Get chat memory for this session
        ChatMemory memory = TokenWindowChatMemory.withMaxTokens(
            request.getChatId(),
            maxTokensPerChat,
            tokenizer,
            memoryStore
        );
        
        // Add new message
        memory.add(convertToMessage(request));
        
        // Execute workflow with memory context
        WorkflowContext context = createContext(memory.messages());
        StepResult<?> result = engine.execute(request, context);  // workflowId from request
        
        // Add response to memory
        ChatResponse response = convertToResponse(result);
        memory.add(convertToMessage(response));
        
        return response;
    }
    
    private Message convertToMessage(ChatRequest request) {
        return Message.userMessage(request.getMessage());
    }
    
    private Message convertToMessage(ChatResponse response) {
        String content = response.getPropertiesMap().getOrDefault("message", "");
        return Message.aiMessage(content);
    }
}
```

### Advantages of using driftkit-common ChatMemoryStore:
1. **Token-based limits** - More intelligent than simple message count
2. **Pluggable storage** - InMemory, Redis, MongoDB implementations
3. **System message preservation** - Keeps important context
4. **Already tested and proven** - Used across DriftKit
5. **Tokenizer support** - Accurate token counting for LLM limits

## 10. Implementation Timeline

### Week 1 - Core Fixes:
- Day 1-2: Delete TODELETE classes, fix compilation
- Day 3-4: Implement StepResult static factories
- Day 5: Move WorkflowService to core

### Week 2 - Execution & Simplification:
- Day 1-2: Fix workflow execution (stop at suspend/async)
- Day 3-4: Simplify graph model while keeping branching
- Day 5: Implement proper ChatResponseFactory

### Week 3 - Feature Parity:
- Day 1-2: Port SchemaUtils from chat-assistant
- Day 3-4: Add missing controller endpoints
- Day 5: Implement memory management

### Week 4 - Polish:
- Day 1-2: Add WebSocket support
- Day 3-4: Comprehensive testing
- Day 5: Documentation and examples

## Summary

This refactoring will:
1. **Reduce code by ~40%** (delete 1300+ lines)
2. **Simplify API** with static factories
3. **Fix critical bugs** in execution flow
4. **Maintain all functionality** including branching
5. **Add missing features** from chat-assistant-framework
6. **Improve developer experience** significantly

The key insight: Keep what works (type-safe branching, async support) while removing unnecessary complexity (graph abstractions, multiple analyzers).
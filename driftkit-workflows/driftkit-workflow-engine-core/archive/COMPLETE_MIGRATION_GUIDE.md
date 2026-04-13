# Complete Migration Guide: Unifying DriftKit Workflow Engines

## Executive Summary

This document provides a comprehensive guide for enhancing the new `driftkit-workflow-engine-core` to support the complete functionality from both `driftkit-chat-assistant-framework` and `driftkit-workflows-core`. The new engine must combine:
- The type-safe, graph-based execution model of the new engine
- The sophisticated human-in-the-loop features from the chat framework
- The AI agent capabilities from the old workflows engine

## Table of Contents
1. [Existing Implementations Analysis](#existing-implementations-analysis)
2. [Critical Missing Features](#critical-missing-features)
3. [Features from Old Workflows Engine](#features-from-old-workflows-engine)
4. [Production Example Analysis](#production-example-analysis)
5. [Implementation Roadmap](#implementation-roadmap)
6. [Testing Requirements](#testing-requirements)
7. [Migration Guide](#migration-guide)
8. [Performance and Security](#performance-and-security)

---

## 1. Existing Implementations Analysis

### 1.1 driftkit-chat-assistant-framework

#### Core Workflow Classes:
- **`AnnotatedWorkflow`** (`/ai/driftkit/chat/framework/workflow/AnnotatedWorkflow.java`)
  - **Package**: `ai.driftkit.chat.framework.workflow`
  - **Key Methods**: 
    - `processChat(ChatRequest)` - Main entry point for processing chat requests
    - `discoverSteps()` - Automatically discovers @WorkflowStep annotated methods
    - `registerStepMethod()` - Registers individual workflow steps
    - `executeStep()` - Executes workflow steps with proper input/output handling
    - `handleAsyncTask()` - Manages asynchronous step execution
  - **Dependencies**: AiClient, AsyncResponseTracker, ChatHistoryService, WorkflowContextRepository
  - **Current Usage**: Base class for all conversational workflows

#### Controller Implementation:
- **`AssistantController`** (`/ai/driftkit/chat/framework/controller/AssistantController.java`)
  - **Package**: `ai.driftkit.chat.framework.controller`
  - **Key Methods**:
    - `chat()` - RESTful endpoint for chat interactions
    - `getChatResponse()` - Polling endpoint for async responses
    - `history()` - Chat history retrieval with pagination
    - `schemas()` - Returns all available workflow schemas
    - `getFirstStepSchema()` - Gets initial schema for workflow initialization
  - **Dependencies**: ChatWorkflowService, AsyncResponseTracker, ChatSessionService
  - **Current Usage**: Main REST API for chat interactions

#### Service Layer:
- **`ChatWorkflowService`** (`/ai/driftkit/chat/framework/service/ChatWorkflowService.java`)
  - **Package**: `ai.driftkit.chat.framework.service`
  - **Key Methods**:
    - `processChat()` - Orchestrates workflow execution
    - `findWorkflow()` - Determines appropriate workflow for requests
    - `resolveDataNameIdReferences()` - Handles cross-message data references
  - **Dependencies**: WorkflowContextRepository, AsyncResponseTracker, ChatHistoryService
  - **Current Usage**: Central service for workflow orchestration

#### Schema Generation and Conversion:
- **`SchemaUtils`** (`/ai/driftkit/chat/framework/util/SchemaUtils.java`)
  - **Package**: `ai.driftkit.chat.framework.util`
  - **Key Methods**:
    - `getSchemaFromClass()` - Converts Java classes to AIFunctionSchema
    - `getAllSchemasFromClass()` - Handles composable schemas
    - `createInstance()` - Creates instances from properties maps
    - `extractProperties()` - Converts objects to property maps
    - `combineComposableSchemaData()` - Merges partial data for composable schemas
  - **Dependencies**: AIFunctionSchema, SchemaClass annotations
  - **Current Usage**: Core utility for schema management and conversion

### 1.2 driftkit-workflows-core

#### LLM Agent System:
- **`LLMAgent`** (`/ai/driftkit/workflows/core/agent/LLMAgent.java`)
  - **Package**: `ai.driftkit.workflows.core.agent`
  - **Key Methods**:
    - `executeText()` - Simple text execution
    - `executeWithTools()` - Tool-enabled execution
    - `executeStructured()` - Structured output extraction
    - `executeWithPrompt()` - Template-based execution
    - `executeImageGeneration()` - Image generation
    - `registerTool()` - Tool registration methods
  - **Dependencies**: ModelClient, ChatMemory, PromptService, ToolRegistry
  - **Current Usage**: High-level AI agent abstraction

#### Tool System:
- **`Tool`** (`/ai/driftkit/workflows/core/agent/tool/Tool.java`)
  - **Package**: `ai.driftkit.workflows.core.agent.tool`
  - **Key Methods**: `getName()`, `getDescription()`, `getParametersSchema()`, `execute()`
  - **Current Usage**: Interface for agent tools

### 1.3 Production Example (Acena Backend)

#### Production Workflow:
- **`LearningWorkflow`** (`/cc/acena/backend/chat/assistant/framework/workflow/LearningWorkflow.java`)
  - **Package**: `cc.acena.backend.chat.assistant.framework.workflow`
  - **Key Methods**: Complex learning workflow with 9+ steps including async material generation
  - **Dependencies**: Extends AnnotatedWorkflow, uses extensive domain services
  - **Current Usage**: Real-world educational workflow implementation

---

## 2. Critical Missing Features

### 2.1 Schema Generation and Management System

**Current State**: The new engine lacks the dynamic schema generation system that enables frontend integration.

**Existing Implementation to Port**:
- **Source**: `ai.driftkit.chat.framework.util.SchemaUtils` (driftkit-chat-assistant-framework)
- **Key Methods**:
  - `getSchemaFromClass(Class<?> clazz)` - Converts Java classes to AIFunctionSchema
  - `getAllSchemasFromClass(Class<?> clazz)` - Generates composable schemas from fields
  - `createInstance(Class<?> clazz, Map<String, Object> properties)` - Creates typed instances from maps
  - `extractProperties(Object instance)` - Converts objects to property maps
  - `combineComposableSchemaData(Map<String, Object> data, Class<?> targetClass)` - Merges partial data

**Supporting Classes to Port**:
- `ai.driftkit.chat.framework.events.AIFunctionSchema` - Schema definition class
- `ai.driftkit.chat.framework.annotations.SchemaClass` - Marks classes for schema generation
- `ai.driftkit.chat.framework.annotations.SchemaDescription` - Field descriptions
- `ai.driftkit.chat.framework.annotations.SchemaSecurity` - Security annotations

**Required Implementation**:

```java
// Port SchemaUtils to new engine with enhanced interface
package ai.driftkit.workflow.engine.schema;

public interface SchemaProvider {
    // Direct port from SchemaUtils.getSchemaFromClass
    AIFunctionSchema generateSchema(Class<?> inputType);
    
    // Direct port from SchemaUtils.getAllSchemasFromClass
    Map<String, Object> generateComposableSchema(Class<?> inputType);
    
    // Direct port from SchemaUtils.createInstance
    Object convertFromMap(Map<String, Object> data, Class<?> targetType);
    
    // Direct port from SchemaUtils.extractProperties
    Map<String, Object> convertToMap(Object data);
}

// Schema metadata for steps - enhance existing annotations
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface SchemaField {
    String title() default "";
    String description() default "";
    boolean required() default true;
    String[] enumValues() default {};
}

// Dynamic schema provider for runtime values
public interface DynamicSchemaProvider {
    List<String> getValues(String fieldName, WorkflowContext context);
}
```

**Implementation Priority**: CRITICAL - Without this, human-in-the-loop scenarios cannot work with frontends.

### 2.2 Controller Layer for External Integration

**Current State**: The new engine has no HTTP/WebSocket controller layer.

**Existing Implementation to Port**:
- **Source**: `ai.driftkit.chat.framework.controller.AssistantController` 
- **Key Endpoints**:
  - `POST /chat` - Main chat endpoint (line 65-106)
  - `GET /response/{messageId}` - Async response polling (line 109-142)
  - `GET /chat/{chatId}/history` - History with pagination (line 177-191)
  - `GET /workflows/{workflowName}/schemas` - Schema discovery (line 195-234)

**Service Layer to Port**:
- **Source**: `ai.driftkit.chat.framework.service.ChatWorkflowService`
- **Key Methods**:
  - `processChat(ChatRequest request)` - Main orchestration method
  - `findWorkflow(String workflowName)` - Workflow resolution
  - `resolveDataNameIdReferences(ChatRequest request)` - Data reference handling

**Required Implementation**:

```java
package ai.driftkit.workflow.engine.controller;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {
    
    private final WorkflowEngine engine;
    private final SchemaProvider schemaProvider;
    private final ProgressTracker progressTracker;
    
    // Port from AssistantController.chat()
    @PostMapping("/{workflowId}/execute")
    public WorkflowResponse execute(
        @PathVariable String workflowId,
        @RequestBody Map<String, Object> input,
        @RequestHeader(value = "X-Session-Id", required = false) String sessionId
    ) {
        // Use ChatWorkflowService.processChat logic
        // Convert using SchemaUtils.createInstance
        // Return structured response like ChatResponse
    }
    
    // Port from AssistantController.resume()
    @PostMapping("/{runId}/resume")
    public WorkflowResponse resume(
        @PathVariable String runId,
        @RequestBody Map<String, Object> userInput
    ) {
        // Validate against expected schema
        // Resume workflow
        // Return next step or completion
    }
    
    // Port from AssistantController.getChatResponse()
    @GetMapping("/{runId}/status")
    public WorkflowStatus getStatus(@PathVariable String runId) {
        // Use AsyncResponseTracker pattern
        // Return current status with progress if async
    }
    
    // Port from AssistantController.schemas()
    @GetMapping("/{workflowId}/schemas")
    public List<AIFunctionSchema> getSchemas(@PathVariable String workflowId) {
        // Return all schemas for workflow steps
    }
}
```

### 2.3 Asynchronous Step Execution with Progress Tracking

**Current State**: The new engine has basic async support but lacks progress tracking.

**Existing Implementation to Port**:
- **Source**: `ai.driftkit.chat.framework.workflow.AnnotatedWorkflow.handleAsyncTask()` (lines 313-343)
- **Progress Tracking**: `ai.driftkit.chat.framework.service.AsyncResponseTracker` interface
- **Async Annotations**: `ai.driftkit.chat.framework.annotations.AsyncStep`

**Key Methods from AnnotatedWorkflow**:
- `handleAsyncTask(String asyncStepMethod, AsyncTaskEvent event)` - Async execution handler
- `updateAsyncTaskProgress(String messageId, int percentComplete, Object progressData)` - Progress updates

**Required Enhancement**:

```java
// Port AsyncResponseTracker interface
package ai.driftkit.workflow.engine.async;

public interface ProgressTracker {
    // Direct ports from AsyncResponseTracker
    void trackResponse(String id, String status, Object data);
    void updateResponseStatus(String id, String status, Object data);
    ChatResponse getResponse(String id);
    
    // Enhanced for new engine
    void updateProgress(int percentage, String message);
    Progress getProgress();
    void onComplete(Object result);
    void onError(Throwable error);
}

// Enhanced async step result
public record AsyncStepResult<T>(
    CompletableFuture<StepResult<T>> future,
    String taskId,
    ProgressTracker progressTracker
) implements StepResult<T> {}

// Port async execution pattern from AnnotatedWorkflow.handleAsyncTask
@Component
public class AsyncStepExecutor {
    public CompletableFuture<StepResult<?>> executeAsync(
        Method method, Object instance, Object[] args, ProgressTracker tracker
    ) {
        // Implementation based on AnnotatedWorkflow lines 313-343
        return CompletableFuture.supplyAsync(() -> {
            try {
                tracker.updateProgress(0, "Starting async task");
                Object result = method.invoke(instance, args);
                tracker.updateProgress(100, "Completed");
                return new StepResult.Continue<>(result);
            } catch (Exception e) {
                tracker.onError(e);
                return new StepResult.Fail<>(e);
            }
        });
    }
}

// Enhance existing annotation
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AsyncStep {
    String value() default "";
    String description() default "";
    boolean trackProgress() default true;
    long timeoutMs() default 300000; // 5 minutes
    String forStep() default ""; // Port from chat framework
}
```

### 2.4 Chat Context and Memory Management

**Current State**: The new engine's WorkflowContext is generic, not optimized for chat scenarios.

**Existing Implementations**:
- **Chat History**: `ai.driftkit.chat.framework.service.ChatHistoryService`
- **Session Management**: `ai.driftkit.chat.framework.service.ChatSessionService`
- **Context Repository**: `ai.driftkit.chat.framework.repository.WorkflowContextRepository`
- **Old Context**: `ai.driftkit.workflows.core.domain.WorkflowContext`

**Required Enhancement**:

```java
package ai.driftkit.workflow.engine.chat;

// Combine features from both context implementations
public interface ChatContext extends WorkflowContext {
    // From ChatHistoryService
    List<ChatMessage> getConversationHistory();
    void addUserMessage(String message);
    void addAssistantMessage(String message);
    void addSystemMessage(String message);
    
    // From ChatSessionService
    ChatSession getSession();
    Map<String, Object> getUserProfile();
    
    // From old WorkflowContext
    void onStepInvocation(String stepName);
    int getStepInvocationCount(String stepName);
    
    // New for chat workflows
    void setCurrentSchema(AIFunctionSchema schema);
    AIFunctionSchema getCurrentSchema();
}

// Chat-specific workflow base
public abstract class ChatWorkflow {
    protected ChatContext getChatContext(WorkflowContext context) {
        return ChatContextAdapter.from(context);
    }
    
    // Port utility methods from AnnotatedWorkflow
    protected void saveWorkflowContext() {
        // Implementation from AnnotatedWorkflow
    }
    
    protected void updateAsyncTaskProgress(String messageId, int percent, Object data) {
        // Implementation from AnnotatedWorkflow
    }
}
```

---

## 3. Features from Old Workflows Engine

### 3.1 LLM Agent Integration

**Existing Implementation**:
- **Source**: `ai.driftkit.workflows.core.agent.LLMAgent`
- **Package**: `ai.driftkit.workflows.core.agent`
- **Builder Pattern**: `LLMAgent.Builder` with extensive configuration options

**Key Methods to Port**:
- `executeText(String userMessage)` - Simple text execution
- `executeWithTools(String userMessage)` - Tool-enabled execution
- `executeStructured(String userMessage, Class<T> targetClass)` - Structured output
- `executeWithPrompt(String promptId, Map<String, Object> variables)` - Template execution
- `registerTool(Tool tool)` - Tool registration

**Required Implementation**:

```java
package ai.driftkit.workflow.engine.ai;

// Direct port of LLMAgent to new engine
@Component
public class LLMStepExecutor {
    private final LLMAgent agent; // Use existing LLMAgent class from workflows-core
    
    @Step
    public StepResult<String> processWithAI(String input, WorkflowContext context) {
        // Port from ExecutableWorkflow patterns
        String result = agent.executeText(input);
        return new StepResult.Continue<>(result);
    }
    
    @Step
    public StepResult<Map<String, Object>> executeWithTools(
        String input, 
        WorkflowContext context,
        List<Tool> tools
    ) {
        tools.forEach(agent::registerTool);
        ToolExecutionResult result = agent.executeWithTools(input);
        return new StepResult.Continue<>(result.getResult());
    }
    
    @AsyncStep(trackProgress = true)
    public CompletableFuture<StepResult<String>> generateContentAsync(
        String prompt,
        WorkflowContext context,
        ProgressTracker tracker
    ) {
        return CompletableFuture.supplyAsync(() -> {
            tracker.updateProgress(10, "Initializing AI model");
            String result = agent.executeText(prompt);
            tracker.updateProgress(100, "Generation complete");
            return new StepResult.Continue<>(result);
        });
    }
}
```

### 3.2 Tool System

**Existing Implementation**:
- **Tool Interface**: `ai.driftkit.workflows.core.agent.tool.Tool`
- **Tool Result**: `ai.driftkit.workflows.core.agent.ToolExecutionResult`
- **Tool Schema**: `ai.driftkit.workflows.core.agent.tool.ToolParameterSchema`
- **Built-in Tools**: Package `ai.driftkit.workflows.core.agent.tool.builtin`

**Classes to Port Directly**:
```java
// All these can be used as-is in the new engine
ai.driftkit.workflows.core.agent.tool.Tool
ai.driftkit.workflows.core.agent.ToolExecutionResult
ai.driftkit.workflows.core.agent.tool.ToolParameterSchema
ai.driftkit.workflows.core.agent.tool.builtin.RandomNumberTool
ai.driftkit.workflows.core.agent.tool.builtin.CurrentTimeTool
ai.driftkit.workflows.core.agent.tool.builtin.SearchTool
```

**Integration with New Engine**:
```java
package ai.driftkit.workflow.engine.tools;

@Component
public class ToolRegistry {
    private final Map<String, Tool> tools = new ConcurrentHashMap<>();
    
    public void register(Tool tool) {
        tools.put(tool.getName(), tool);
    }
    
    public Tool get(String name) {
        return tools.get(name);
    }
    
    public List<Tool> getAll() {
        return new ArrayList<>(tools.values());
    }
    
    // Integration with workflow steps
    @Step
    public StepResult<Object> executeTool(String toolName, Map<String, Object> params) {
        Tool tool = get(toolName);
        if (tool == null) {
            return new StepResult.Fail<>(new IllegalArgumentException("Tool not found: " + toolName));
        }
        
        ToolExecutionResult result = tool.execute(params);
        if (result.isSuccess()) {
            return new StepResult.Continue<>(result.getResult());
        } else {
            return new StepResult.Fail<>(new RuntimeException(result.getError()));
        }
    }
}
```

---

## 4. Production Example Analysis

Based on the Acena production workflow at `/Volumes/op/development/projects/acena/acena-backend/src/main/java/cc/acena/backend/chat/assistant/framework/workflow/LearningWorkflow`:

### 4.1 Complex Workflow Patterns

The production example demonstrates several advanced patterns that must be supported:

#### Multi-Step User Onboarding
```java
// From LearningWorkflow - Multi-step data collection
@WorkflowStep(index = 1, inputClasses = {UserInfoPart1.class})
public StepEvent collectUserInfoPart1(UserInfoPart1 info) {
    // Validate and store partial data
    context.put("userInfoPart1", info);
    return StepEvent.nextStep("collectUserInfoPart2");
}

@WorkflowStep(index = 2, inputClasses = {UserInfoPart2.class})
public StepEvent collectUserInfoPart2(UserInfoPart2 info) {
    // Combine with previous data
    UserInfoPart1 part1 = context.get("userInfoPart1");
    UserInfoComplete complete = combine(part1, info);
    return StepEvent.nextStep("processUserInfo", complete);
}
```

#### Async Material Generation with Progress
```java
@AsyncStep(forStep = "generateMaterial", description = "Generate learning material")
public void generateMaterialAsync(AsyncTaskEvent event) {
    String messageId = event.getMessageId();
    MaterialRequest request = event.getTypedData(MaterialRequest.class);
    
    // Update progress periodically
    updateAsyncTaskProgress(messageId, 0, "Analyzing requirements...");
    
    // Step 1: Analyze topic (25%)
    TopicAnalysis analysis = analyzeTask(request.getTopic());
    updateAsyncTaskProgress(messageId, 25, "Generating outline...");
    
    // Step 2: Generate outline (50%)
    Outline outline = generateOutline(analysis);
    updateAsyncTaskProgress(messageId, 50, "Creating content...");
    
    // Step 3: Generate content (75%)
    Content content = generateContent(outline);
    updateAsyncTaskProgress(messageId, 75, "Formatting material...");
    
    // Step 4: Format and finalize (100%)
    LearningMaterial material = formatMaterial(content);
    updateAsyncTaskProgress(messageId, 100, material);
}
```

#### Conditional Flow Control
```java
@WorkflowStep(
    index = 5,
    condition = "#hasPrerequisites",
    onTrue = "checkPrerequisites",
    onFalse = "startLearning"
)
public StepEvent evaluateReadiness(LearnerProfile profile) {
    boolean hasPrerequisites = profile.getLevel() > 1;
    context.put("hasPrerequisites", hasPrerequisites);
    
    if (hasPrerequisites) {
        return StepEvent.conditionalNext("checkPrerequisites", profile);
    } else {
        return StepEvent.conditionalNext("startLearning", profile);
    }
}
```

### 4.2 Document Processing Pipeline

```java
@WorkflowStep(index = 3, requiresUserInput = false)
public StepEvent processDocuments(List<Document> documents) {
    // Multi-stage processing
    List<ProcessedDocument> processed = documents.stream()
        .map(this::validateDocument)
        .map(this::extractContent)
        .map(this::analyzeContent)
        .map(this::enrichMetadata)
        .collect(Collectors.toList());
    
    return StepEvent.nextStep("reviewDocuments", processed);
}
```

### 4.3 Interactive Q&A System

```java
@WorkflowStep(index = 7, inputClasses = {Question.class})
public StepEvent handleQuestion(Question question) {
    // Context-aware response generation
    ConversationHistory history = chatHistoryService.getHistory(context.getSessionId());
    
    // Use LLM with context
    String response = llmAgent.Builder()
        .withMemory(history)
        .withTools(getRelevantTools(question))
        .build()
        .executeText(question.getText());
    
    // Track citations if needed
    List<Citation> citations = extractCitations(response);
    
    return StepEvent.completed(new Answer(response, citations));
}
```

---

## 5. Implementation Roadmap

### Phase 1: Core Infrastructure (Week 1-2)

#### 1.1 Schema System Implementation
```bash
# Create schema package structure
mkdir -p src/main/java/ai/driftkit/workflow/engine/schema
mkdir -p src/main/java/ai/driftkit/workflow/engine/schema/annotations

# Port files
cp ../../../driftkit-chat-assistant-framework/src/main/java/ai/driftkit/chat/framework/util/SchemaUtils.java \
   src/main/java/ai/driftkit/workflow/engine/schema/

cp ../../../driftkit-chat-assistant-framework/src/main/java/ai/driftkit/chat/framework/events/AIFunctionSchema.java \
   src/main/java/ai/driftkit/workflow/engine/schema/

# Port annotations
cp -r ../../../driftkit-chat-assistant-framework/src/main/java/ai/driftkit/chat/framework/annotations/Schema*.java \
   src/main/java/ai/driftkit/workflow/engine/schema/annotations/
```

**Tasks**:
- [ ] Create SchemaProvider interface
- [ ] Port SchemaUtils as DefaultSchemaProvider
- [ ] Port all schema annotations
- [ ] Add schema caching mechanism
- [ ] Create unit tests for schema generation

#### 1.2 Controller Layer
**Tasks**:
- [ ] Create WorkflowController with all endpoints
- [ ] Port request/response DTOs from chat framework
- [ ] Implement error handling middleware
- [ ] Add OpenAPI documentation
- [ ] Create integration tests

### Phase 2: Async and Progress (Week 3)

#### 2.1 Enhanced Async Support
**Tasks**:
- [ ] Port AsyncResponseTracker interface
- [ ] Implement InMemoryProgressTracker
- [ ] Create RedisProgressTracker for production
- [ ] Enhance AsyncStep annotation
- [ ] Update WorkflowEngine.executeAsyncStep()

#### 2.2 Testing
**Tasks**:
- [ ] Update NewRouterWorkflow with async examples
- [ ] Add progress tracking tests
- [ ] Test concurrent async steps
- [ ] Add timeout handling tests
- [ ] Performance benchmarks

### Phase 3: Chat Features (Week 4)

#### 3.1 Chat Context Implementation
**Tasks**:
- [ ] Create ChatContext interface
- [ ] Implement ChatContextAdapter
- [ ] Port ChatHistoryService
- [ ] Port ChatSessionService
- [ ] Create ChatWorkflow base class

#### 3.2 Memory Management
**Tasks**:
- [ ] Add conversation persistence
- [ ] Implement history pagination
- [ ] Add memory limits and cleanup
- [ ] Create memory usage tests

### Phase 4: AI Integration (Week 5)

#### 4.1 LLM Support
**Tasks**:
- [ ] Add workflows-core dependency
- [ ] Create LLMStepExecutor
- [ ] Add model configuration
- [ ] Implement retry logic
- [ ] Add token counting

#### 4.2 Tool System
**Tasks**:
- [ ] Create ToolRegistry component
- [ ] Port built-in tools
- [ ] Add tool discovery
- [ ] Create tool execution tests

---

## 6. Testing Requirements

### 6.1 Unit Tests

#### Schema Generation Tests
```java
@Test
void testSchemaGeneration() {
    // Port from SchemaUtilsTest
    AIFunctionSchema schema = schemaProvider.generateSchema(UserInput.class);
    assertNotNull(schema);
    assertEquals("UserInput", schema.getName());
    assertTrue(schema.getProperties().containsKey("name"));
}

@Test
void testComposableSchema() {
    // Test multi-part schema generation
    Map<String, Object> schemas = schemaProvider.generateComposableSchema(UserInfoComplete.class);
    assertEquals(2, schemas.size());
    assertTrue(schemas.containsKey("UserInfoPart1"));
    assertTrue(schemas.containsKey("UserInfoPart2"));
}
```

#### Async Execution Tests
```java
@Test
void testAsyncStepWithProgress() {
    // Test progress tracking
    AtomicInteger progressCount = new AtomicInteger();
    ProgressTracker tracker = new TestProgressTracker(progress -> {
        progressCount.incrementAndGet();
    });
    
    CompletableFuture<StepResult<?>> future = asyncExecutor.executeAsync(
        asyncMethod, instance, args, tracker
    );
    
    StepResult<?> result = future.get(5, TimeUnit.SECONDS);
    assertTrue(progressCount.get() > 0);
}
```

### 6.2 Integration Tests

#### End-to-End Workflow Tests
```java
@Test
void testCompleteWorkflowWithSuspension() {
    // Port from AnnotatedWorkflowTest
    WorkflowExecution<Result> execution = engine.execute("test-workflow", input);
    
    // Wait for suspension
    Thread.sleep(1000);
    assertFalse(execution.isDone());
    
    // Resume with user input
    execution = engine.resume(execution.getRunId(), userInput);
    Result result = execution.get(5, TimeUnit.SECONDS);
    
    assertNotNull(result);
}
```

### 6.3 Performance Tests

```java
@Test
void testConcurrentWorkflowExecutions() {
    int concurrentCount = 100;
    List<CompletableFuture<Result>> futures = new ArrayList<>();
    
    for (int i = 0; i < concurrentCount; i++) {
        futures.add(engine.execute("perf-workflow", input).getFuture());
    }
    
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
        .get(30, TimeUnit.SECONDS);
    
    // Verify all completed
    assertTrue(futures.stream().allMatch(CompletableFuture::isDone));
}
```

---

## 7. Migration Guide

### 7.1 For Chat Assistant Framework Users

#### Step 1: Update Dependencies
```xml
<!-- Remove old dependency -->
<dependency>
    <groupId>ai.driftkit</groupId>
    <artifactId>driftkit-chat-assistant-framework</artifactId>
</dependency>

<!-- Add new dependency -->
<dependency>
    <groupId>ai.driftkit</groupId>
    <artifactId>driftkit-workflow-engine-core</artifactId>
    <version>${driftkit.version}</version>
</dependency>
```

#### Step 2: Convert Workflow Classes
```java
// Old
public class MyWorkflow extends AnnotatedWorkflow {
    @WorkflowStep(index = 1)
    public StepEvent firstStep(Input input) {
        return StepEvent.nextStep("secondStep", output);
    }
}

// New
@Workflow(id = "my-workflow", version = "1.0")
public class MyWorkflow {
    @InitialStep
    public StepResult<Output> firstStep(Input input) {
        return new StepResult.Continue<>(output);
    }
}
```

#### Step 3: Update Async Steps
```java
// Old
@AsyncStep(forStep = "generate")
public void generateAsync(AsyncTaskEvent event) {
    updateAsyncTaskProgress(event.getMessageId(), 50, "Half done");
}

// New
@AsyncStep(value = "generate", trackProgress = true)
public CompletableFuture<StepResult<Output>> generateAsync(
    Input input, 
    ProgressTracker tracker
) {
    return CompletableFuture.supplyAsync(() -> {
        tracker.updateProgress(50, "Half done");
        return new StepResult.Continue<>(output);
    });
}
```

### 7.2 For Old Workflows Users

#### Step 1: Convert Workflow Definitions
```java
// Old
public class MyWorkflow extends ExecutableWorkflow<Input, Output> {
    @Override
    public Output execute(Input input) {
        return process(input);
    }
}

// New
@Workflow(id = "my-workflow", version = "1.0")
public class MyWorkflow {
    @InitialStep
    public StepResult<Output> execute(Input input) {
        return new StepResult.Finish<>(process(input));
    }
}
```

#### Step 2: Integrate LLM Agent
```java
// Old - direct usage
Output result = llmAgent.executeStructured(prompt, Output.class);

// New - within a step
@Step
public StepResult<Output> generateWithAI(Input input, WorkflowContext context) {
    Output result = llmAgent.executeStructured(input.getPrompt(), Output.class);
    return new StepResult.Continue<>(result);
}
```

### 7.3 Class-by-Class Migration Reference

| Old Class | New Location | Migration Notes |
|-----------|--------------|-----------------|
| `AnnotatedWorkflow` | Merge into `WorkflowEngine` | Extract step discovery, async handling |
| `SchemaUtils` | `ai.driftkit.workflow.engine.schema.DefaultSchemaProvider` | Port all methods directly |
| `AssistantController` | `ai.driftkit.workflow.engine.controller.WorkflowController` | Adapt endpoints to new engine |
| `ChatWorkflowService` | Enhance `WorkflowEngine` | Add orchestration logic |
| `AsyncResponseTracker` | `ai.driftkit.workflow.engine.async.ProgressTracker` | Port interface and implementations |
| `StepEvent` | Adapt to `StepResult` | Map event types to result types |
| `@WorkflowStep` | `@Step` + `@InitialStep` | Add missing properties |
| `@AsyncStep` | Enhance existing `@AsyncStep` | Add progress tracking |
| `LLMAgent` | Use directly from workflows-core | No changes needed |
| `Tool` interface | Use directly from workflows-core | Compatible as-is |

---

## 8. Performance and Security

### 8.1 Performance Considerations

#### Schema Caching
```java
// Port from SchemaUtils
private static final Map<Class<?>, AIFunctionSchema> schemaCache = new ConcurrentHashMap<>();

public AIFunctionSchema generateSchema(Class<?> clazz) {
    return schemaCache.computeIfAbsent(clazz, this::doGenerateSchema);
}
```

#### Thread Pool Configuration
```java
// From WorkflowEngineConfig
WorkflowEngineConfig config = WorkflowEngineConfig.builder()
    .coreThreads(10)
    .maxThreads(50)
    .queueCapacity(1000)
    .defaultStepTimeoutMs(300_000) // 5 minutes
    .build();
```

#### Memory Management
```java
// Limit conversation history
public class BoundedChatHistory implements ChatHistory {
    private final int maxMessages = 100;
    private final LinkedList<ChatMessage> messages = new LinkedList<>();
    
    public void addMessage(ChatMessage message) {
        messages.addLast(message);
        if (messages.size() > maxMessages) {
            messages.removeFirst();
        }
    }
}
```

### 8.2 Security Considerations

#### Input Validation
```java
// Validate all inputs against schema
public Object validateAndConvert(Map<String, Object> input, AIFunctionSchema schema) {
    // Validate required fields
    for (String required : schema.getRequired()) {
        if (!input.containsKey(required)) {
            throw new ValidationException("Missing required field: " + required);
        }
    }
    
    // Validate types and constraints
    // ... implementation
    
    return schemaProvider.convertFromMap(input, schema.getTargetClass());
}
```

#### Access Control
```java
@PreAuthorize("hasRole('WORKFLOW_USER')")
@PostMapping("/{workflowId}/execute")
public WorkflowResponse execute(...) {
    // Check workflow-specific permissions
    if (!permissionService.canExecute(workflowId, currentUser)) {
        throw new AccessDeniedException("Cannot execute workflow: " + workflowId);
    }
    // ... continue execution
}
```

#### Rate Limiting
```java
@Component
public class WorkflowRateLimiter {
    private final RateLimiter rateLimiter = RateLimiter.create(10.0); // 10 requests per second
    
    public void checkLimit(String userId) {
        if (!rateLimiter.tryAcquire()) {
            throw new RateLimitExceededException("Rate limit exceeded for user: " + userId);
        }
    }
}
```

---

## 9. Configuration Migration

### From application.yml

```yaml
# Old chat framework configuration
chat:
  async:
    response:
      timeout: 300000
      cache:
        enabled: true
        ttl: 3600
  history:
    max-messages: 100
    page-size: 20
  workflows:
    package: "com.example.workflows"

# New engine configuration  
driftkit:
  workflow:
    engine:
      core-threads: 10
      max-threads: 50
      queue-capacity: 1000
      default-step-timeout-ms: 300000
    schema:
      cache:
        enabled: true
        max-size: 1000
    controller:
      base-path: "/api/workflows"
      cors:
        allowed-origins: ["*"]
    async:
      progress-tracker:
        type: "redis" # or "in-memory"
        redis:
          host: "localhost"
          port: 6379
```

---

## 10. Complete File List for Migration

### Must Port (High Priority)
1. `ai/driftkit/chat/framework/util/SchemaUtils.java` → `ai/driftkit/workflow/engine/schema/DefaultSchemaProvider.java`
2. `ai/driftkit/chat/framework/controller/AssistantController.java` → `ai/driftkit/workflow/engine/controller/WorkflowController.java`
3. `ai/driftkit/chat/framework/service/ChatWorkflowService.java` → Enhance `WorkflowEngine`
4. `ai/driftkit/chat/framework/service/AsyncResponseTracker.java` → `ai/driftkit/workflow/engine/async/ProgressTracker.java`
5. `ai/driftkit/chat/framework/events/StepEvent.java` → Adapt to `StepResult`
6. `ai/driftkit/chat/framework/events/AIFunctionSchema.java` → `ai/driftkit/workflow/engine/schema/AIFunctionSchema.java`
7. `ai/driftkit/chat/framework/workflow/AnnotatedWorkflow.java` → Extract patterns to `WorkflowEngine`

### Can Reuse As-Is
1. `ai/driftkit/workflows/core/agent/LLMAgent.java`
2. `ai/driftkit/workflows/core/agent/tool/*` (entire package)
3. `ai/driftkit/chat/framework/annotations/Schema*.java` (all schema annotations)
4. `ai/driftkit/chat/framework/events/AsyncTaskEvent.java`

### Reference for Patterns
1. `cc/acena/backend/chat/assistant/framework/workflow/LearningWorkflow.java` - Complex production workflow
2. `ai/driftkit/chat/framework/workflow/AnnotatedWorkflowTest.java` - Comprehensive test patterns
3. `ai/driftkit/workflows/core/service/WorkflowAnalyzer.java` - Workflow analysis patterns

---

## 11. Documentation Requirements

### 11.1 API Documentation

```java
@Operation(summary = "Execute a workflow", 
          description = "Starts a new workflow execution with the provided input")
@ApiResponses({
    @ApiResponse(responseCode = "200", description = "Workflow started successfully"),
    @ApiResponse(responseCode = "400", description = "Invalid input"),
    @ApiResponse(responseCode = "404", description = "Workflow not found")
})
@PostMapping("/{workflowId}/execute")
public WorkflowResponse execute(...) {
    // Implementation
}
```

### 11.2 Migration Guide Structure

1. **Quick Start** - 5-minute migration for simple workflows
2. **Detailed Guide** - Step-by-step for complex workflows
3. **API Comparison** - Old vs new API reference
4. **Common Patterns** - Cookbook of migration patterns
5. **Troubleshooting** - Common issues and solutions

### 11.3 Example Workflows

Create example workflows demonstrating:
- Simple sequential flow
- Human-in-the-loop with suspension
- Async processing with progress
- Tool-enabled AI workflows
- Multi-step data collection
- Conditional branching
- Error handling and recovery

---

## 12. Conclusion

This comprehensive migration guide provides all the necessary information to enhance the new `driftkit-workflow-engine-core` with features from both the chat assistant framework and the old workflows engine. By following this guide and leveraging the existing implementations, the new engine will support:

1. **Type-safe workflow execution** with the new graph-based model
2. **Rich human-in-the-loop interactions** with schema generation and validation
3. **Advanced AI capabilities** with LLM agents and tools
4. **Production-ready features** including async processing, progress tracking, and chat context
5. **Backward compatibility** through adapters and migration tools

The key to success is to reuse as much existing, battle-tested code as possible while adapting it to the new engine's architecture. This approach minimizes risk and accelerates development while ensuring feature parity and improved functionality.
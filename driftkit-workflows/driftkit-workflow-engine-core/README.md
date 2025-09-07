# DriftKit Workflow Engine Core

**Advanced workflow orchestration engine for AI applications with comprehensive support for conversational AI, chatbots, and human-in-the-loop workflows**

## 🌟 Overview

DriftKit Workflow Engine is a powerful framework that unifies multiple workflow paradigms:
- **Traditional Workflows** - Step-by-step business process automation
- **Conversational Workflows** - Multi-turn chat interactions with automatic message tracking
- **Human-in-the-Loop** - Seamless suspension and resumption for human input
- **Async Processing** - Long-running operations with progress tracking
- **Multi-Agent Systems** - Orchestration of multiple AI agents

## 🎯 Key Features

### 1. Chat & Conversational AI Support
- **Automatic Message Tracking** - All suspend/finish messages are automatically saved to ChatStore
- **Session Management** - Conversations linked to chatId for continuity
- **Multi-turn Dialogues** - Complex conversational flows with branching
- **Context Preservation** - Full state maintained across suspensions
- **No Manual Saving** - Framework handles all chat persistence

### 2. Human-in-the-Loop Capabilities
- **Workflow Suspension** - Use `StepResult.suspend()` to pause for human input
- **Type-Safe Resumption** - Specify expected input class for type safety
- **Context Preservation** - Full workflow state maintained during suspension
- **Multiple Suspension Points** - Workflows can suspend multiple times
- **Flexible Data Collection** - Support for any custom data types

### 3. Core Workflow Features
- **Annotation-Based** - Define workflows with `@Workflow`, `@InitialStep`, `@Step`
- **Fluent API** - Programmatic workflow construction with `WorkflowBuilder`
- **Async Processing** - `@AsyncStep` for long-running operations
- **Conditional Routing** - Branch based on data or conditions
- **Error Handling** - Comprehensive exception management with retry policies

## 📚 Core Concepts

### Workflow Definition

Workflows can be defined using annotations or the fluent API:

#### Annotation-Based Approach

```java
@Workflow(
    id = "customer-onboarding",
    version = "1.0",
    description = "Customer onboarding process"
)
public class CustomerOnboardingWorkflow {
    
    @InitialStep
    public StepResult<WelcomeMessage> startOnboarding(StartEvent event, WorkflowContext context) {
        WelcomeMessage welcome = new WelcomeMessage();
        welcome.setText("Welcome! Let's get you started.");
        welcome.setNextSteps(Arrays.asList("Personal Info", "Preferences", "Verification"));
        
        // Suspend workflow and wait for customer info
        return StepResult.suspend(welcome, CustomerInfo.class);
    }
    
    @Step
    public StepResult<?> processCustomerInfo(CustomerInfo info, WorkflowContext context) {
        // Validate customer data
        if (!isValid(info)) {
            ValidationError error = new ValidationError();
            error.setMessage("Please correct the following issues");
            error.setFields(getInvalidFields(info));
            
            // Suspend again for corrections
            return StepResult.suspend(error, CustomerInfo.class);
        }
        
        // Continue to next step
        context.put("customerInfo", info);
        return StepResult.continueWith(new InfoVerified(info));
    }
}
```

#### Fluent API Approach

```java
var workflow = WorkflowBuilder
    .define("customer-onboarding", StartRequest.class, OnboardingResult.class)
    .then("welcome", (StartRequest req) -> {
        WelcomeMessage welcome = new WelcomeMessage("Welcome " + req.getName());
        return StepResult.suspend(welcome, CustomerInfo.class);
    })
    .then("validate", (CustomerInfo info) -> {
        if (isValid(info)) {
            return StepResult.continueWith(new ValidatedInfo(info));
        } else {
            return StepResult.suspend(new ValidationError(), CustomerInfo.class);
        }
    })
    .then("complete", (ValidatedInfo info) -> {
        return StepResult.finish(new OnboardingResult(info));
    })
    .build();
```

### Step Result Types

The framework provides several result types for controlling workflow execution:

#### 1. Continue - Move to Next Step
```java
return StepResult.continueWith(data);
```

#### 2. Suspend - Wait for Human Input
```java
// Suspend and specify expected input type
return StepResult.suspend(promptData, ExpectedInputClass.class);

// With metadata
Map<String, String> metadata = Map.of("priority", "high");
return StepResult.suspend(promptData, ExpectedInputClass.class, metadata);
```

#### 3. Branch - Conditional Routing
```java
return StepResult.branch(new SpecificEvent(data));
```

#### 4. Async - Long-Running Operations
```java
return StepResult.async(
    "taskId",           // Unique task identifier
    30000L,            // Timeout in milliseconds
    taskArgs,          // Arguments for async handler
    immediateData      // Data to return immediately
);
```

#### 5. Finish - Complete Workflow
```java
return StepResult.finish(finalResult);
```

#### 6. Fail - Handle Errors
```java
return StepResult.fail(exception);
// or
return StepResult.fail("Error message");
```

## 🤝 Human-in-the-Loop Patterns

### Basic Suspension Pattern

```java
@Workflow(id = "feedback-collection", version = "1.0")
public class FeedbackWorkflow {
    
    @InitialStep
    public StepResult<FeedbackRequest> requestFeedback(StartEvent event, WorkflowContext context) {
        FeedbackRequest request = new FeedbackRequest();
        request.setQuestion("How was your experience?");
        request.setOptions(Arrays.asList("Excellent", "Good", "Fair", "Poor"));
        
        // Workflow suspends here until user provides feedback
        return StepResult.suspend(request, UserFeedback.class);
    }
    
    @Step
    public StepResult<?> processFeedback(UserFeedback feedback, WorkflowContext context) {
        if ("Poor".equals(feedback.getRating())) {
            // Ask for more details
            DetailRequest detailRequest = new DetailRequest();
            detailRequest.setMessage("We're sorry to hear that. Can you tell us more?");
            
            return StepResult.suspend(detailRequest, DetailedFeedback.class);
        }
        
        // Complete workflow
        return StepResult.finish(new FeedbackResult(feedback));
    }
}
```

### Multi-Step Data Collection

```java
@Workflow(id = "multi-step-form", version = "1.0")
public class MultiStepFormWorkflow {
    
    @InitialStep
    public StepResult<PersonalInfoForm> collectPersonalInfo(StartEvent event, WorkflowContext context) {
        PersonalInfoForm form = new PersonalInfoForm();
        form.setTitle("Step 1: Personal Information");
        form.setFields(Arrays.asList("name", "email", "phone"));
        
        return StepResult.suspend(form, PersonalInfo.class);
    }
    
    @Step
    public StepResult<AddressForm> collectAddress(PersonalInfo info, WorkflowContext context) {
        context.put("personalInfo", info);
        
        AddressForm form = new AddressForm();
        form.setTitle("Step 2: Address Information");
        
        return StepResult.suspend(form, AddressInfo.class);
    }
    
    @Step
    public StepResult<PreferencesForm> collectPreferences(AddressInfo address, WorkflowContext context) {
        context.put("addressInfo", address);
        
        PreferencesForm form = new PreferencesForm();
        form.setTitle("Step 3: Your Preferences");
        
        return StepResult.suspend(form, Preferences.class);
    }
    
    @Step
    public StepResult<RegistrationComplete> completeRegistration(Preferences prefs, WorkflowContext context) {
        PersonalInfo personal = context.get("personalInfo", PersonalInfo.class);
        AddressInfo address = context.get("addressInfo", AddressInfo.class);
        
        // Process complete registration
        RegistrationComplete result = new RegistrationComplete();
        result.setUserId(generateUserId());
        result.setWelcomeMessage("Registration complete! Welcome " + personal.getName());
        
        return StepResult.finish(result);
    }
}
```

## 💬 Chat Workflow Features

### Automatic Message Tracking

When using ChatStore with WorkflowEngine, all messages are automatically tracked:

```java
// Configure engine with ChatStore
ChatStore chatStore = new InMemoryChatStore(new SimpleTextTokenizer());
WorkflowEngineConfig config = WorkflowEngineConfig.builder()
    .chatStore(chatStore)
    .build();
    
WorkflowEngine engine = new WorkflowEngine(config);

// Define workflow - NO manual chat saving needed!
var chatWorkflow = WorkflowBuilder
    .define("support-chat", ChatInput.class, ChatResult.class)
    .then("greet", (ChatInput input) -> {
        // This message is automatically saved to ChatStore
        Greeting greeting = new Greeting("Hello! How can I help you today?");
        return StepResult.suspend(greeting, UserQuery.class);
    })
    .then("respond", (UserQuery query) -> {
        // User input is automatically saved
        // Generate response
        Response response = generateResponse(query);
        
        // This is also automatically saved
        return StepResult.finish(response);
    })
    .build();

// Execute with chatId - all messages are auto-tracked!
engine.execute("support-chat", new ChatInput("Start"), "chat-123");
```

### Conversation Flow Example

```java
@Workflow(id = "support-assistant", version = "1.0")
public class SupportAssistantWorkflow {
    
    @InitialStep
    public StepResult<MenuOptions> presentMenu(StartEvent event, WorkflowContext context) {
        MenuOptions menu = new MenuOptions();
        menu.setGreeting("Welcome to support! What can I help you with?");
        menu.setOptions(Arrays.asList(
            "Check Order Status",
            "Technical Support",
            "Billing Question",
            "Other"
        ));
        
        return StepResult.suspend(menu, UserChoice.class);
    }
    
    @Step(nextClasses = {OrderQuery.class, TechIssue.class, BillingQuery.class, GeneralQuery.class})
    public StepResult<?> routeByChoice(UserChoice choice, WorkflowContext context) {
        return switch (choice.getSelection()) {
            case "Check Order Status" -> StepResult.branch(new OrderQuery());
            case "Technical Support" -> StepResult.branch(new TechIssue());
            case "Billing Question" -> StepResult.branch(new BillingQuery());
            default -> StepResult.branch(new GeneralQuery());
        };
    }
    
    @Step
    public StepResult<OrderStatusPrompt> handleOrderQuery(OrderQuery query, WorkflowContext context) {
        OrderStatusPrompt prompt = new OrderStatusPrompt();
        prompt.setMessage("I can help you check your order status.");
        prompt.setInstruction("Please provide your order number:");
        
        return StepResult.suspend(prompt, OrderNumber.class);
    }
    
    @Step
    public StepResult<OrderStatus> checkOrderStatus(OrderNumber orderNum, WorkflowContext context) {
        // Look up order (could be async)
        OrderStatus status = orderService.getStatus(orderNum.getValue());
        
        // If not found, ask again
        if (status == null) {
            OrderNotFound notFound = new OrderNotFound();
            notFound.setMessage("I couldn't find order " + orderNum.getValue());
            notFound.setSuggestion("Please double-check the order number");
            
            return StepResult.suspend(notFound, OrderNumber.class);
        }
        
        // Complete with order status
        return StepResult.finish(status);
    }
}
```

## 🔄 Async Processing

### Basic Async Pattern

```java
@Workflow(id = "document-processing", version = "1.0")
public class DocumentProcessingWorkflow {
    
    @InitialStep
    public StepResult<ProcessingStatus> startProcessing(DocumentRequest request, WorkflowContext context) {
        context.put("documentId", request.getDocumentId());
        
        // Return async result with immediate status
        Map<String, Object> taskArgs = Map.of(
            "documentUrl", request.getDocumentUrl(),
            "documentType", request.getType()
        );
        
        ProcessingStatus immediateStatus = new ProcessingStatus();
        immediateStatus.setStatus("PROCESSING_STARTED");
        immediateStatus.setMessage("Document processing has begun");
        immediateStatus.setEstimatedTime(30);
        
        return StepResult.async(
            "process-doc-" + request.getDocumentId(),
            30000L,  // 30 second timeout
            taskArgs,
            immediateStatus
        );
    }
    
    @AsyncStep("process-doc-*")
    public StepResult<ProcessingResult> processDocumentAsync(
            Map<String, Object> taskArgs,
            WorkflowContext context,
            TaskProgressReporter progress) {
        
        String documentUrl = (String) taskArgs.get("documentUrl");
        
        try {
            progress.updateProgress(10, "Downloading document...");
            byte[] content = downloadDocument(documentUrl);
            
            progress.updateProgress(40, "Extracting text...");
            String text = extractText(content);
            
            progress.updateProgress(70, "Analyzing content...");
            Analysis analysis = analyzeDocument(text);
            
            progress.updateProgress(100, "Processing complete");
            
            ProcessingResult result = new ProcessingResult();
            result.setDocumentId(context.get("documentId", String.class));
            result.setText(text);
            result.setAnalysis(analysis);
            
            return StepResult.finish(result);
            
        } catch (Exception e) {
            return StepResult.fail(e);
        }
    }
}
```

### Async with Progress Updates

```java
@AsyncStep("complex-analysis")
public StepResult<AnalysisResult> performComplexAnalysis(
        Map<String, Object> args,
        WorkflowContext context,
        TaskProgressReporter progress) {
    
    List<String> items = (List<String>) args.get("items");
    List<ItemResult> results = new ArrayList<>();
    
    for (int i = 0; i < items.size(); i++) {
        // Check if cancelled
        if (progress.isCancelled()) {
            return StepResult.fail("Analysis cancelled by user");
        }
        
        // Update progress
        int percentComplete = (i * 100) / items.size();
        progress.updateProgress(percentComplete, "Processing item " + (i + 1) + " of " + items.size());
        
        // Process item
        ItemResult itemResult = processItem(items.get(i));
        results.add(itemResult);
    }
    
    progress.updateProgress(100, "Analysis complete");
    
    return StepResult.finish(new AnalysisResult(results));
}
```

## 📋 Schema Annotations for Data Classes

Use schema annotations to provide metadata for UI generation:

```java
@Data
@NoArgsConstructor
@SchemaName("customerInfo")
@SchemaDescription("Customer information form")
public class CustomerInfo {
    
    @SchemaProperty(
        description = "Full name",
        required = true,
        nameId = "customer.name"
    )
    private String name;
    
    @SchemaProperty(
        description = "Email address",
        required = true,
        pattern = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    )
    private String email;
    
    @SchemaProperty(
        description = "Account type",
        values = {"personal", "business", "enterprise"}
    )
    private String accountType;
    
    @SchemaProperty(
        description = "Years of experience",
        min = 0,
        max = 50
    )
    private Integer experience;
}
```

## 🚀 Quick Start Guide

### 1. Define Your Workflow

```java
@Workflow(id = "simple-chat", version = "1.0")
public class SimpleChatWorkflow {
    
    @InitialStep
    public StepResult<WelcomeMessage> start(StartEvent event, WorkflowContext context) {
        WelcomeMessage welcome = new WelcomeMessage();
        welcome.setText("Hello! What's your name?");
        
        return StepResult.suspend(welcome, UserName.class);
    }
    
    @Step
    public StepResult<PersonalizedGreeting> greetUser(UserName name, WorkflowContext context) {
        PersonalizedGreeting greeting = new PersonalizedGreeting();
        greeting.setMessage("Nice to meet you, " + name.getValue() + "!");
        greeting.setNextPrompt("How can I help you today?");
        
        return StepResult.suspend(greeting, UserRequest.class);
    }
    
    @Step
    public StepResult<HelpResponse> provideHelp(UserRequest request, WorkflowContext context) {
        HelpResponse response = processRequest(request);
        return StepResult.finish(response);
    }
}
```

### 2. Configure and Register

```java
// Create configuration
WorkflowEngineConfig config = WorkflowEngineConfig.builder()
    .chatStore(new InMemoryChatStore(new SimpleTextTokenizer()))
    .build();

// Create engine
WorkflowEngine engine = new WorkflowEngine(config);

// Register workflow
engine.register(new SimpleChatWorkflow());
```

### 3. Execute Workflow

```java
// Start workflow
String chatId = "chat-123";
WorkflowExecution<HelpResponse> execution = engine.execute("simple-chat", null, chatId);

// Check if suspended
if (execution.isSuspended()) {
    // Get suspension data (e.g., WelcomeMessage)
    Object suspensionData = execution.getCurrentResult();
    
    // Later, resume with user input
    UserName userName = new UserName("John");
    execution = engine.resume(execution.getRunId(), userName);
}

// Continue resuming until complete
while (execution.isSuspended()) {
    // Get user input based on suspension data
    Object userInput = getUserInput(execution.getCurrentResult());
    execution = engine.resume(execution.getRunId(), userInput);
}

// Get final result
HelpResponse result = execution.getResult();
```

## 🛠️ Advanced Features

### Retry Policies

```java
@Step(
    name = "processPayment",
    retryPolicy = @RetryPolicy(
        maxAttempts = 3,
        initialDelay = 1000,
        maxDelay = 10000,
        multiplier = 2.0
    )
)
public StepResult<PaymentResult> processPayment(PaymentRequest request, WorkflowContext context) {
    // Payment processing with automatic retry
}
```

### Cross-Workflow Calls

```java
@Step
public StepResult<?> callOtherWorkflow(DataEvent data, WorkflowContext context) {
    // Prepare input for other workflow
    OtherWorkflowInput input = new OtherWorkflowInput(data.getValue());
    
    // Call external workflow
    return StepResult.external(
        "other-workflow-id",
        input,
        "processExternalResult"  // Next step after external workflow completes
    );
}
```

### Conditional Routing

```java
@Step
public StepResult<?> routeBasedOnCondition(ProcessedData data, WorkflowContext context) {
    if (data.getScore() > 0.8) {
        return StepResult.branch(new HighScoreEvent(data));
    } else if (data.getScore() > 0.5) {
        return StepResult.branch(new MediumScoreEvent(data));
    } else {
        return StepResult.branch(new LowScoreEvent(data));
    }
}
```

## 📊 Monitoring and Testing

### Workflow State Access

```java
// Get workflow instance
Optional<WorkflowInstance> instance = engine.getWorkflowInstance(runId);

// Check status
WorkflowStatus status = instance.get().getStatus();
// CREATED, RUNNING, SUSPENDED, COMPLETED, FAILED

// Get current step
String currentStep = instance.get().getCurrentStepId();

// Get execution history
List<String> executedSteps = instance.get().getExecutedSteps();
```

### Testing Workflows

```java
@Test
void testChatWorkflow() {
    // Setup
    WorkflowEngine engine = new WorkflowEngine();
    engine.register(new SimpleChatWorkflow());
    
    // Execute
    var execution = engine.execute("simple-chat", null);
    
    // Verify suspension
    assertTrue(execution.isSuspended());
    assertEquals(WelcomeMessage.class, execution.getCurrentResult().getClass());
    
    // Resume with input
    execution = engine.resume(execution.getRunId(), new UserName("Test User"));
    
    // Continue until complete
    while (execution.isSuspended()) {
        execution = engine.resume(execution.getRunId(), createTestInput());
    }
    
    // Verify result
    assertNotNull(execution.getResult());
}
```

## 🎯 Best Practices

### 1. Workflow Design
- Keep steps focused and single-purpose
- Use meaningful step names and descriptions
- Implement proper error handling
- Design for resumability

### 2. Data Modeling
- Create clear, typed data classes for inputs/outputs
- Use schema annotations for better documentation
- Keep suspension data lightweight
- Avoid storing sensitive data in workflow context

### 3. Chat Workflows
- Let the framework handle message persistence
- Use meaningful chat IDs for conversation tracking
- Design conversational flows to be natural
- Provide clear prompts and options

### 4. Async Operations
- Use progress reporting for long-running tasks
- Implement cancellation checks
- Set appropriate timeouts
- Handle failures gracefully

## 📚 Examples

Complete working examples can be found in the test directory:
- `SimplifiedChatWorkflow` - Basic chat with auto-tracking
- `OnboardingWorkflow` - Multi-step onboarding process
- `ChatWorkflowExample` - Advanced chat patterns
- `AsyncWorkflowExample` - Async processing patterns

---

**DriftKit Workflow Engine** - Building sophisticated AI workflows with human-in-the-loop capabilities has never been easier.
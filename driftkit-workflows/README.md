# DriftKit Workflows Module

**Comprehensive workflow orchestration for AI applications with native support for conversational AI, chatbots, and human-in-the-loop workflows**

## 🌟 Overview

The DriftKit Workflows module provides a complete solution for building sophisticated AI-powered applications:

- **🤖 Conversational AI** - Build chatbots and voice assistants with automatic message tracking
- **👥 Human-in-the-Loop** - Seamlessly pause workflows for human input and approval
- **⚡ Async Processing** - Handle long-running AI operations with progress tracking
- **🔄 Multi-Agent Orchestration** - Coordinate multiple AI agents in complex workflows
- **📊 Business Process Automation** - Traditional workflow patterns with AI enhancement

## 📦 Module Structure

```
driftkit-workflows/
├── driftkit-workflow-engine-core/          # Core workflow engine
│   ├── Annotation-based workflows          # @Workflow, @Step, @AsyncStep
│   ├── Fluent API builder                  # Programmatic workflow construction
│   ├── Chat context management             # Automatic conversation tracking
│   └── Human-in-the-loop support          # Suspension and resumption
├── driftkit-workflow-engine-agents/        # Multi-agent patterns
│   ├── LoopAgent                          # Iterative refinement
│   ├── SequentialAgent                    # Pipeline processing
│   └── HierarchicalAgent                  # Agent-as-tool pattern
├── driftkit-workflow-test-framework/       # Comprehensive testing support
│   ├── Mock builders                      # Type-safe mock creation
│   ├── Execution tracking                 # Step-by-step verification
│   ├── Fluent assertions                  # Expressive test assertions
│   └── JUnit 5 integration                # @WorkflowTest annotation
└── driftkit-workflow-engine-spring-boot-starter/  # Spring Boot integration
    ├── Auto-configuration                  # Zero-config setup
    ├── REST endpoints                      # Workflow execution APIs
    └── Service layer                       # Business logic integration
```

## 📖 Module Documentation

### driftkit-workflow-engine-core
The core workflow engine providing the foundation for all workflow types:
- **Workflow Definition**: Define workflows using annotations (`@Workflow`, `@Step`) or fluent API
- **Execution Engine**: Robust execution with retry policies, circuit breakers, and error handling
- **State Management**: Automatic state persistence and recovery
- **Context Management**: Thread-safe workflow context for data sharing between steps
- **Suspension/Resumption**: Built-in support for human-in-the-loop workflows
- **Async Processing**: Handle long-running operations with progress tracking

### driftkit-workflow-engine-agents  
Pre-built multi-agent orchestration patterns for complex AI workflows:
- **LoopAgent**: Iterative refinement until quality criteria are met
- **SequentialAgent**: Pipeline processing with data transformation
- **HierarchicalAgent**: Agent-as-tool pattern for nested workflows
- **Custom Patterns**: Extensible framework for building your own patterns

### driftkit-workflow-test-framework
Comprehensive testing toolkit for workflow validation:
- **Mock Builders**: Type-safe mock creation for workflow steps
- **Execution Tracking**: Detailed step-by-step execution verification
- **Fluent Assertions**: Expressive API for result validation
- **JUnit 5 Integration**: Seamless integration with modern test frameworks
- **Async Testing**: Support for testing async and suspended workflows

### driftkit-workflow-engine-spring-boot-starter
Zero-configuration Spring Boot integration:
- **Auto-Configuration**: Automatic setup of workflow engine and dependencies
- **REST Endpoints**: Built-in APIs for workflow execution and management
- **Service Layer**: Ready-to-use services for chat and workflow operations
- **Monitoring**: Health checks and metrics integration
- **Configuration**: Externalized configuration via `application.yml`

## 🚀 Quick Start

### 1. Add Dependencies

```xml
<dependency>
    <groupId>ai.driftkit</groupId>
    <artifactId>driftkit-workflow-engine-spring-boot-starter</artifactId>
    <version>0.8.5</version>
</dependency>
```

### 2. Create a Simple Chatbot

```java
@Workflow(id = "customer-service-bot", version = "1.0")
public class CustomerServiceBot {
    
    @InitialStep
    public StepResult<Greeting> greetCustomer(StartEvent event, WorkflowContext context) {
        Greeting greeting = new Greeting();
        greeting.setMessage("Hello! I'm here to help. What can I do for you today?");
        greeting.setOptions(Arrays.asList(
            "Check order status",
            "Technical support",
            "General inquiry"
        ));
        
        // Suspend workflow and wait for customer choice
        return StepResult.suspend(greeting, CustomerChoice.class);
    }
    
    @Step
    public StepResult<?> handleChoice(CustomerChoice choice, WorkflowContext context) {
        switch (choice.getSelection()) {
            case "Check order status":
                return handleOrderInquiry();
            case "Technical support":
                return handleTechnicalSupport();
            default:
                return handleGeneralInquiry();
        }
    }
    
    private StepResult<OrderPrompt> handleOrderInquiry() {
        OrderPrompt prompt = new OrderPrompt();
        prompt.setMessage("I'd be happy to check your order status.");
        prompt.setInstruction("Please provide your order number:");
        
        // Wait for order number
        return StepResult.suspend(prompt, OrderNumber.class);
    }
}
```

### 3. Configure and Run

```java
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}

@RestController
public class ChatController {
    private final WorkflowEngine engine;
    
    @PostMapping("/chat/start")
    public ChatResponse startChat(@RequestBody ChatStartRequest request) {
        String chatId = UUID.randomUUID().toString();
        
        // Start workflow - messages are automatically tracked!
        WorkflowExecution execution = engine.execute(
            "customer-service-bot", 
            request, 
            chatId
        );
        
        return ChatResponse.from(execution.getCurrentResult());
    }
    
    @PostMapping("/chat/{chatId}/continue")
    public ChatResponse continueChat(
            @PathVariable String chatId,
            @RequestBody UserInput input) {
        
        // Resume workflow with user input
        WorkflowExecution execution = engine.resume(chatId, input);
        
        return ChatResponse.from(execution.getCurrentResult());
    }
}
```

## 💬 Chat Workflow Features

### Automatic Message Tracking

All chat messages are automatically saved when you configure a ChatStore:

```java
@Configuration
public class WorkflowConfig {
    
    @Bean
    public WorkflowEngineConfig engineConfig(ChatStore chatStore) {
        return WorkflowEngineConfig.builder()
            .chatStore(chatStore)  // Enable automatic message tracking
            .build();
    }
    
    @Bean
    public ChatStore chatStore() {
        return new InMemoryChatStore(new SimpleTextTokenizer());
    }
}
```

With this configuration:
- ✅ All suspend messages are automatically saved
- ✅ All finish messages are automatically saved
- ✅ User inputs are tracked when resuming
- ✅ No manual chat.save() calls needed!

### Conversation Context

The framework provides built-in context management:

```java
@Workflow(id = "context-aware-bot", version = "1.0")
public class ContextAwareBot {
    
    @InitialStep
    public StepResult<Question> askName(StartEvent event, WorkflowContext context) {
        Question q = new Question("What's your name?");
        return StepResult.suspend(q, UserName.class);
    }
    
    @Step
    public StepResult<PersonalizedMessage> greetByName(UserName name, WorkflowContext context) {
        // Store in context
        context.put("userName", name.getValue());
        
        PersonalizedMessage msg = new PersonalizedMessage();
        msg.setText("Nice to meet you, " + name.getValue() + "!");
        msg.setQuestion("How can I help you today?");
        
        return StepResult.suspend(msg, HelpRequest.class);
    }
    
    @Step
    public StepResult<Solution> provideHelp(HelpRequest request, WorkflowContext context) {
        // Retrieve from context
        String userName = context.get("userName", String.class);
        
        Solution solution = generateSolution(request, userName);
        return StepResult.finish(solution);
    }
}
```

### Multi-Turn Conversations

Build complex conversational flows with branching:

```java
@Workflow(id = "product-recommendation", version = "1.0")
public class ProductRecommendationBot {
    
    @InitialStep
    public StepResult<CategoryQuestion> askCategory(StartEvent event, WorkflowContext context) {
        CategoryQuestion question = new CategoryQuestion();
        question.setMessage("What type of product are you looking for?");
        question.setCategories(Arrays.asList("Electronics", "Clothing", "Home & Garden"));
        
        return StepResult.suspend(question, CategoryChoice.class);
    }
    
    @Step(nextClasses = {ElectronicsFlow.class, ClothingFlow.class, HomeGardenFlow.class})
    public StepResult<?> routeByCategory(CategoryChoice choice, WorkflowContext context) {
        context.put("category", choice.getSelected());
        
        return switch (choice.getSelected()) {
            case "Electronics" -> StepResult.branch(new ElectronicsFlow());
            case "Clothing" -> StepResult.branch(new ClothingFlow());
            case "Home & Garden" -> StepResult.branch(new HomeGardenFlow());
            default -> StepResult.fail("Unknown category");
        };
    }
    
    @Step
    public StepResult<BudgetQuestion> askBudget(ElectronicsFlow flow, WorkflowContext context) {
        BudgetQuestion question = new BudgetQuestion();
        question.setMessage("What's your budget range?");
        question.setRanges(Arrays.asList("Under $100", "$100-$500", "$500-$1000", "Over $1000"));
        
        return StepResult.suspend(question, BudgetChoice.class);
    }
    
    @Step
    public StepResult<ProductList> recommendProducts(BudgetChoice budget, WorkflowContext context) {
        String category = context.get("category", String.class);
        
        List<Product> recommendations = productService.recommend(category, budget.getRange());
        
        ProductList list = new ProductList();
        list.setProducts(recommendations);
        list.setMessage("Here are my top recommendations for you:");
        
        return StepResult.finish(list);
    }
}
```

## 👥 Human-in-the-Loop Patterns

### Approval Workflows

```java
@Workflow(id = "expense-approval", version = "1.0")
public class ExpenseApprovalWorkflow {
    
    @InitialStep
    public StepResult<?> routeByAmount(ExpenseRequest expense, WorkflowContext context) {
        context.put("expense", expense);
        
        if (expense.getAmount() < 100) {
            // Auto-approve small expenses
            return StepResult.finish(new ApprovalResult("AUTO_APPROVED"));
        } else if (expense.getAmount() < 1000) {
            // Manager approval needed
            return requestManagerApproval(expense);
        } else {
            // Director approval needed
            return requestDirectorApproval(expense);
        }
    }
    
    private StepResult<ApprovalRequest> requestManagerApproval(ExpenseRequest expense) {
        ApprovalRequest request = new ApprovalRequest();
        request.setTitle("Expense Approval Required");
        request.setDescription(expense.getDescription());
        request.setAmount(expense.getAmount());
        request.setApproverRole("MANAGER");
        
        // Suspend until manager responds
        return StepResult.suspend(request, ApprovalDecision.class);
    }
    
    @Step
    public StepResult<ApprovalResult> processApproval(ApprovalDecision decision, WorkflowContext context) {
        ExpenseRequest expense = context.get("expense", ExpenseRequest.class);
        
        if (decision.isApproved()) {
            // Process the expense
            processExpense(expense);
            return StepResult.finish(new ApprovalResult("APPROVED", decision.getComments()));
        } else {
            return StepResult.finish(new ApprovalResult("REJECTED", decision.getReason()));
        }
    }
}
```

### Form-Based Data Collection

```java
@Workflow(id = "user-onboarding", version = "1.0")
public class UserOnboardingWorkflow {
    
    @InitialStep
    public StepResult<WelcomeScreen> welcome(StartEvent event, WorkflowContext context) {
        WelcomeScreen welcome = new WelcomeScreen();
        welcome.setTitle("Welcome to Our Platform!");
        welcome.setMessage("Let's get you set up in just a few steps.");
        welcome.setNextButtonText("Get Started");
        
        return StepResult.suspend(welcome, GetStarted.class);
    }
    
    @Step
    public StepResult<PersonalInfoForm> collectPersonalInfo(GetStarted start, WorkflowContext context) {
        PersonalInfoForm form = new PersonalInfoForm();
        form.setTitle("Tell us about yourself");
        form.setFields(Arrays.asList("firstName", "lastName", "email", "phone"));
        
        return StepResult.suspend(form, PersonalInfo.class);
    }
    
    @Step
    public StepResult<CompanyInfoForm> collectCompanyInfo(PersonalInfo info, WorkflowContext context) {
        // Validate personal info
        ValidationResult validation = validate(info);
        if (!validation.isValid()) {
            PersonalInfoForm form = new PersonalInfoForm();
            form.setErrors(validation.getErrors());
            return StepResult.suspend(form, PersonalInfo.class);
        }
        
        context.put("personalInfo", info);
        
        CompanyInfoForm form = new CompanyInfoForm();
        form.setTitle("Tell us about your company");
        
        return StepResult.suspend(form, CompanyInfo.class);
    }
    
    @Step
    public StepResult<OnboardingComplete> completeOnboarding(CompanyInfo company, WorkflowContext context) {
        PersonalInfo personal = context.get("personalInfo", PersonalInfo.class);
        
        // Create user account
        User user = createUser(personal, company);
        
        OnboardingComplete complete = new OnboardingComplete();
        complete.setUserId(user.getId());
        complete.setWelcomeMessage("Welcome aboard, " + personal.getFirstName() + "!");
        complete.setNextSteps(Arrays.asList(
            "Complete your profile",
            "Invite team members",
            "Explore features"
        ));
        
        return StepResult.finish(complete);
    }
}
```

## ⚡ Async Processing

### Long-Running Operations

```java
@Workflow(id = "document-analysis", version = "1.0")
public class DocumentAnalysisWorkflow {
    
    @InitialStep
    public StepResult<ProcessingStarted> startAnalysis(DocumentSubmission submission, WorkflowContext context) {
        context.put("documentId", submission.getDocumentId());
        
        // Prepare async task
        Map<String, Object> taskArgs = Map.of(
            "documentUrl", submission.getUrl(),
            "analysisType", submission.getAnalysisType()
        );
        
        // Return immediate response
        ProcessingStarted started = new ProcessingStarted();
        started.setMessage("Document analysis started");
        started.setEstimatedTime(120); // seconds
        
        return StepResult.async(
            "analyze-" + submission.getDocumentId(),
            120000L, // 2 minute timeout
            taskArgs,
            started
        );
    }
    
    @AsyncStep("analyze-*")
    public StepResult<AnalysisComplete> analyzeDocument(
            Map<String, Object> args,
            WorkflowContext context,
            TaskProgressReporter progress) {
        
        String documentUrl = (String) args.get("documentUrl");
        
        try {
            // Download document
            progress.updateProgress(10, "Downloading document...");
            Document doc = downloadDocument(documentUrl);
            
            // Extract text
            progress.updateProgress(30, "Extracting text...");
            String text = extractText(doc);
            
            // Perform analysis
            progress.updateProgress(60, "Analyzing content...");
            Analysis analysis = performAnalysis(text, args.get("analysisType"));
            
            // Generate report
            progress.updateProgress(90, "Generating report...");
            Report report = generateReport(analysis);
            
            progress.updateProgress(100, "Analysis complete");
            
            AnalysisComplete complete = new AnalysisComplete();
            complete.setDocumentId(context.get("documentId", String.class));
            complete.setReport(report);
            
            return StepResult.finish(complete);
            
        } catch (Exception e) {
            return StepResult.fail("Analysis failed: " + e.getMessage());
        }
    }
}
```

### Progress Monitoring

```java
// Start async workflow
WorkflowExecution execution = engine.execute("document-analysis", submission);

// Check progress periodically
while (!execution.isCompleted()) {
    WorkflowStatus status = engine.getStatus(execution.getRunId());
    
    if (status.isAsync()) {
        int progress = status.getProgress();
        String message = status.getProgressMessage();
        
        System.out.println("Progress: " + progress + "% - " + message);
    }
    
    Thread.sleep(1000);
}
```

## 🤖 Multi-Agent Patterns

### Sequential Agent Pipeline

```java
// Create a research pipeline
SequentialAgent researchPipeline = SequentialAgent.builder()
    .agent(dataCollector)     // Collect raw data
    .agent(dataAnalyzer)      // Analyze patterns
    .agent(reportGenerator)   // Generate report
    .agent(qualityChecker)    // Verify quality
    .build();

// Execute pipeline
String report = researchPipeline.execute("Research quantum computing trends");
```

### Loop Agent for Refinement

```java
// Create content generator with quality loop
Agent writer = LLMAgent.builder()
    .systemMessage("You are a technical writer")
    .modelClient(modelClient)
    .build();

Agent reviewer = LLMAgent.builder()
    .systemMessage("You are a technical editor. Review content for accuracy and clarity.")
    .modelClient(modelClient)
    .build();

LoopAgent contentCreator = LoopAgent.builder()
    .worker(writer)           // Creates content
    .evaluator(reviewer)      // Reviews and provides feedback
    .stopCondition(LoopStatus.COMPLETE)
    .maxIterations(3)
    .build();

// Generate high-quality content
String article = contentCreator.execute("Write about microservices architecture");
```

### Hierarchical Agent Teams

```java
// Create specialized agents
Agent flightSearcher = createFlightAgent();
Agent hotelFinder = createHotelAgent();
Agent carRental = createCarRentalAgent();

// Create coordinator
LLMAgent travelPlanner = LLMAgent.builder()
    .systemMessage("You are a travel planning coordinator")
    .addTool(AgentAsTool.create("searchFlights", flightSearcher))
    .addTool(AgentAsTool.create("findHotels", hotelFinder))
    .addTool(AgentAsTool.create("rentCar", carRental))
    .modelClient(modelClient)
    .build();

// Plan complete trip
String itinerary = travelPlanner.execute("Plan a 5-day trip to Paris for 2 people");
```

## 🛠️ Advanced Features

### Workflow Composition

```java
@Workflow(id = "master-workflow", version = "1.0")
public class MasterWorkflow {
    
    @Step
    public StepResult<?> delegateToSubWorkflow(MasterData data, WorkflowContext context) {
        // Prepare input for sub-workflow
        SubWorkflowInput input = new SubWorkflowInput(data);
        
        // Call another workflow
        return StepResult.external(
            "detail-processing-workflow",
            input,
            "processSubResult"  // Next step when sub-workflow completes
        );
    }
    
    @Step
    public StepResult<FinalResult> processSubResult(SubWorkflowResult result, WorkflowContext context) {
        // Process result from sub-workflow
        return StepResult.finish(new FinalResult(result));
    }
}
```

### Conditional Routing

```java
@Step
public StepResult<?> intelligentRouting(UserInput input, WorkflowContext context) {
    // Analyze input with AI
    Intent intent = analyzeIntent(input.getMessage());
    context.put("intent", intent);
    
    // Route based on confidence and type
    if (intent.getConfidence() < 0.7) {
        // Low confidence - ask for clarification
        return StepResult.suspend(new ClarificationRequest(), UserClarification.class);
    }
    
    // High confidence - route to appropriate handler
    return switch (intent.getType()) {
        case QUESTION -> StepResult.branch(new QuestionFlow(intent));
        case COMPLAINT -> StepResult.branch(new ComplaintFlow(intent));
        case PURCHASE -> StepResult.branch(new PurchaseFlow(intent));
        default -> StepResult.branch(new GeneralFlow(intent));
    };
}
```

### Error Handling and Recovery

```java
@Step(
    retryPolicy = @RetryPolicy(
        maxAttempts = 3,
        initialDelay = 1000,
        multiplier = 2.0
    )
)
public StepResult<PaymentResult> processPayment(PaymentRequest request, WorkflowContext context) {
    try {
        PaymentResult result = paymentService.process(request);
        return StepResult.finish(result);
    } catch (PaymentException e) {
        if (e.isRecoverable()) {
            // Will be retried automatically
            throw e;
        } else {
            // Non-recoverable - handle gracefully
            return StepResult.fail(new PaymentError(e.getMessage()));
        }
    }
}
```

## 📊 Monitoring and Management

### Workflow Execution Service

```java
@Service
public class WorkflowService {
    private final WorkflowExecutionService executionService;
    
    public ChatSession startChat(String userId, String workflowId) {
        // Create new chat session
        ChatSession session = executionService.createChatSession(userId, "New Chat");
        
        // Start workflow
        ChatRequest request = new ChatRequest();
        request.setChatId(session.getChatId());
        request.setUserId(userId);
        request.setWorkflowId(workflowId);
        
        ChatResponse response = executionService.executeChat(request);
        
        return session;
    }
    
    public ChatResponse continueChat(String chatId, Map<String, Object> userInput) {
        // Resume suspended workflow
        ChatRequest request = new ChatRequest();
        request.setChatId(chatId);
        request.setPropertiesMap(userInput);
        
        return executionService.resumeChat(chatId, request);
    }
    
    public List<ChatMessage> getChatHistory(String chatId) {
        return executionService.getChatHistory(chatId, PageRequest.of(0, 100), false)
            .getContent();
    }
}
```

### REST API Endpoints

The Spring Boot starter provides built-in endpoints:

```bash
# List all workflows
GET /workflows

# Execute workflow
POST /workflows/{workflowId}/execute
{
  "input": { ... }
}

# Resume suspended workflow
POST /workflows/instances/{instanceId}/resume
{
  "data": { ... }
}

# Get workflow status
GET /workflows/instances/{instanceId}/status

# Get chat history
GET /chat/{chatId}/messages
```

## 🧪 Testing Workflows

The `driftkit-workflow-test-framework` provides comprehensive testing capabilities for your workflows.

### Add Test Dependency

```xml
<dependency>
    <groupId>ai.driftkit</groupId>
    <artifactId>driftkit-workflow-test-framework</artifactId>
    <version>0.8.5</version>
    <scope>test</scope>
</dependency>
```

### Writing Tests

#### Using JUnit 5 Extension

```java
@WorkflowTest
class CustomerServiceBotTest {
    
    @RegisterWorkflow
    CustomerServiceBot bot = new CustomerServiceBot();
    
    @Test
    void testGreetingFlow(WorkflowTestContext context) {
        // Mock AI responses
        context.mockStep("greetCustomer")
            .willReturn(new Greeting("Hello! How can I help?"));
        
        // Execute workflow
        var result = context.execute("customer-service-bot", new StartEvent());
        
        // Verify execution
        context.assertThat()
            .hasExecutedSteps("greetCustomer")
            .hasStatus(WorkflowStatus.SUSPENDED)
            .hasSuspendedWith(Greeting.class);
        
        // Resume with user input
        var finalResult = context.resume(new CustomerChoice("Check order status"));
        
        context.assertThat()
            .hasExecutedSteps("greetCustomer", "handleChoice", "handleOrderInquiry")
            .hasCompletedSuccessfully();
    }
}
```

#### Testing with Fluent API Workflows

```java
class OrderProcessingWorkflowTest extends FluentWorkflowTest {
    
    @Override
    protected void registerWorkflows() {
        registerWorkflow(
            WorkflowBuilder.define("order-workflow", Order.class, OrderResult.class)
                .then("validate", this::validateOrder)
                .then("process", this::processOrder)
                .then("notify", this::notifyCustomer)
                .build()
        );
    }
    
    @Test
    void testOrderProcessing() {
        // Mock specific steps
        mockStep("process")
            .willReturn(new ProcessedOrder("ORD-123", OrderStatus.COMPLETED));
        
        // Execute workflow
        var result = executeWorkflow("order-workflow", new Order("item-1", 2));
        
        // Verify results
        assertThat(result)
            .isSuccess()
            .hasResult(OrderResult.class)
            .extracting(OrderResult::getOrderId)
            .isEqualTo("ORD-123");
        
        // Verify execution order
        assertExecutionOrder("validate", "process", "notify");
    }
}
```

#### Advanced Testing Features

```java
@Test
void testRetryBehavior() {
    // Mock failure then success
    mockStep("unreliableService")
        .willFailTimes(2, new ServiceException("Temporary error"))
        .thenReturn(new ServiceResult("Success"));
    
    var result = executeWorkflow("retry-workflow", input);
    
    // Verify retries happened
    assertStepExecutionCount("unreliableService", 3);
    assertThat(result).isSuccess();
}

@Test
void testAsyncWorkflow() {
    // Mock async step
    mockAsyncStep("longRunningTask")
        .willCompleteAfter(Duration.ofSeconds(2))
        .withProgressUpdates(25, 50, 75, 100)
        .returning(new TaskResult("Completed"));
    
    var execution = executeWorkflowAsync("async-workflow", input);
    
    // Wait for completion with timeout
    var result = execution.await(5, TimeUnit.SECONDS);
    
    // Verify progress updates
    assertProgressUpdates("longRunningTask", 25, 50, 75, 100);
}
```

## 🎯 Best Practices

### 1. Workflow Design
- **Single Responsibility**: Each step should do one thing well
- **Clear Naming**: Use descriptive names for workflows and steps
- **Error Handling**: Always handle potential failures
- **Idempotency**: Design steps to be safely retryable

### 2. Chat Workflows
- **Natural Flow**: Design conversations to feel natural
- **Context Awareness**: Use WorkflowContext to maintain state
- **Clear Prompts**: Provide clear instructions to users
- **Graceful Fallbacks**: Handle unexpected inputs gracefully

### 3. Human-in-the-Loop
- **Clear Instructions**: Tell users exactly what's needed
- **Validation**: Validate input before proceeding
- **Timeouts**: Set appropriate timeouts for human tasks
- **Progress Indication**: Show progress in multi-step processes

### 4. Performance
- **Async Operations**: Use async steps for long-running tasks
- **Caching**: Cache frequently accessed data in context
- **Batch Processing**: Process multiple items efficiently
- **Resource Cleanup**: Clean up resources in finally blocks

## 🤖 Multi-Agent Patterns

The `driftkit-workflow-engine-agents` module provides ready-to-use multi-agent patterns:

### Loop Agent Example
Iteratively refine content until it meets quality criteria:

```java
@Component
public class ContentOptimizer {
    private final ModelClient aiClient;
    
    public String optimizeContent(String content) {
        LoopAgent agent = LoopAgent.builder()
            .maxIterations(5)
            .taskDescription("Improve the following content for clarity and engagement")
            .completionCriteria("The content should be clear, engaging, and error-free")
            .modelClient(aiClient)
            .build();
            
        return agent.execute(content);
    }
}
```

### Sequential Agent Example
Process data through a pipeline of specialized agents:

```java
@Component
public class DataPipeline {
    private final ModelClient aiClient;
    
    public AnalysisResult analyzeDocument(Document doc) {
        SequentialAgent pipeline = SequentialAgent.builder()
            .addStep("extract", "Extract key information from document")
            .addStep("validate", "Validate extracted data for completeness")
            .addStep("enrich", "Enrich data with external sources")
            .addStep("summarize", "Create executive summary")
            .modelClient(aiClient)
            .build();
            
        return pipeline.execute(doc, AnalysisResult.class);
    }
}
```

### Hierarchical Agent Example
Use specialized agents as tools within a master agent:

```java
@Component
public class ResearchAssistant {
    private final ModelClient aiClient;
    
    public ResearchReport conductResearch(String topic) {
        // Create specialized agents
        Agent webSearcher = new WebSearchAgent(aiClient);
        Agent factChecker = new FactCheckAgent(aiClient);
        Agent writer = new WriterAgent(aiClient);
        
        // Create master coordinator
        HierarchicalAgent coordinator = HierarchicalAgent.builder()
            .name("Research Coordinator")
            .description("Coordinate research activities")
            .addAgent("search", webSearcher)
            .addAgent("verify", factChecker)
            .addAgent("write", writer)
            .modelClient(aiClient)
            .build();
            
        return coordinator.execute(topic, ResearchReport.class);
    }
}
```

## 📚 Examples and Tutorials

### Complete Examples
- [Customer Service Bot](examples/customer-service-bot.md)
- [Expense Approval System](examples/expense-approval.md)
- [Document Processing Pipeline](examples/document-pipeline.md)
- [Multi-Agent Research System](examples/research-system.md)

### Integration Examples
- [Spring Boot Integration](examples/spring-boot-integration.md)
- [MongoDB Persistence](examples/mongodb-setup.md)
- [REST API Usage](examples/rest-api.md)
- [WebSocket Support](examples/websocket-chat.md)

## 🤝 Contributing

We welcome contributions! See our [Contributing Guide](CONTRIBUTING.md) for details.

## 📄 License

Apache License 2.0

---

**DriftKit Workflows** - Build sophisticated AI applications with conversation support and human collaboration built-in.
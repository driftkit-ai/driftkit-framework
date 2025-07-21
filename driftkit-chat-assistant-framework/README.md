# DriftKit Chat Assistant Framework

[![Java Version](https://img.shields.io/badge/Java-21-blue.svg)](https://adoptium.net/temurin/releases/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.1-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Maven Central](https://img.shields.io/badge/Maven%20Central-1.0--SNAPSHOT-red.svg)](https://search.maven.org/)

**DriftKit Chat Assistant Framework** is a powerful and flexible framework for building AI-powered conversational workflows using annotation-based step definitions. It provides a robust foundation for creating complex, multi-step chat interactions with automatic schema generation, asynchronous processing, and composable user interfaces.

## 🎯 Key Features

### Core Framework Features

- **📝 Annotation-based Workflow Definition** - Define complex workflows using simple Java annotations
- **🔄 Automatic Schema Generation** - Convert Java classes to AI function schemas automatically
- **⚡ Asynchronous Step Execution** - Support for long-running operations with progress tracking
- **🧩 Composable User Interfaces** - Break complex forms into step-by-step user interactions
- **💾 Persistent Session Management** - Maintain workflow state across user interactions
- **🔀 Conditional Flow Control** - Dynamic workflow routing based on user input and conditions
- **🛠️ Spring Boot Integration** - Seamless integration with Spring Boot applications
- **📊 Execution History Tracking** - Complete audit trail of workflow execution
- **🔧 Extensible Architecture** - Easy to extend with custom components and integrations

### Advanced Features

- **🎨 Dynamic Schema Composition** - Create complex forms by combining multiple schema classes
- **🔗 Multi-step Workflows** - Chain multiple steps with automatic state management
- **📱 Multi-modal Support** - Handle text, structured data, and custom input types
- **🌐 Internationalization Ready** - Built-in support for multiple languages
- **🔍 Expression Language Support** - Use SpEL for dynamic conditions and validations
- **📈 Progress Tracking** - Real-time progress updates for long-running operations
- **🛡️ Error Handling** - Comprehensive error handling and recovery mechanisms

## 📦 Installation

### Maven Dependency

```xml
<dependency>
    <groupId>ai.driftkit</groupId>
    <artifactId>driftkit-chat-assistant-framework</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

### Gradle Dependency

```gradle
implementation 'ai.driftkit:driftkit-chat-assistant-framework:1.0-SNAPSHOT'
```

## Spring Boot Initialization

To use the chat assistant framework in your Spring Boot application:

```java
@SpringBootApplication
@Import(FeignConfig.class) // Import Feign configuration for AI client
@ComponentScan(basePackages = {"ai.driftkit.chat.framework"}) // Scan framework components
public class YourApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourApplication.class, args);
    }
}
```

Configuration in `application.yml`:

```yaml
ai-props:
  host: "https://your-ai-service-host"
  username: "${AI_USERNAME}"
  password: "${AI_PASSWORD}"
```

The module provides:
- **Feign Configuration**: `FeignConfig` with basic authentication interceptor
- **AI Client**: `AiClient` interface for external AI service communication
- **Workflow Base Classes**: `AnnotatedWorkflow` for step-based conversational flows
- **Annotations**: `@WorkflowStep`, `@AsyncStep` for defining workflow steps

## 🚀 Quick Start

### 1. Implement Required Services

The framework requires implementations of several key interfaces:

```java
@Component
public class MyWorkflowContextRepository implements WorkflowContextRepository {
    private final Map<String, WorkflowContext> contexts = new ConcurrentHashMap<>();
    
    @Override
    public Optional<WorkflowContext> findById(String id) {
        return Optional.ofNullable(contexts.get(id));
    }
    
    @Override
    public WorkflowContext saveOrUpdate(WorkflowContext context) {
        contexts.put(context.getContextId(), context);
        return context;
    }
    
    @Override
    public void deleteById(String id) {
        contexts.remove(id);
    }
}

@Component
public class MyAsyncResponseTracker implements AsyncResponseTracker {
    @Override
    public String generateResponseId() {
        return UUID.randomUUID().toString();
    }
    
    @Override
    public void trackResponse(String responseId, ChatResponse response) {
        // Implementation for tracking responses
    }
    
    @Override
    public void updateResponseStatus(String responseId, ChatResponse response) {
        // Implementation for updating response status
    }
}

@Component
public class MyChatHistoryService implements ChatHistoryService {
    @Override
    public void addRequest(ChatRequest request) {
        // Implementation for storing chat requests
    }
    
    @Override
    public void updateResponse(ChatResponse response) {
        // Implementation for updating responses
    }
}

@Component
public class MyChatMessageService implements ChatMessageService {
    @Override
    public void addMessage(String chatId, String message, MessageType type) {
        // Implementation for adding messages
    }
}
```

### 2. Define Schema Classes

Create data classes that represent the structure of your workflow inputs:

```java
@SchemaClass(
    id = "userRegistration",
    description = "User registration information",
    composable = true
)
public class UserRegistrationInput {
    @SchemaProperty(description = "User's full name", required = true)
    private String fullName;
    
    @SchemaProperty(description = "User's email address", required = true)
    private String email;
    
    @SchemaProperty(
        description = "User's age", 
        required = true,
        minValue = 18,
        maxValue = 120
    )
    private Integer age;
    
    @SchemaProperty(
        description = "User's role",
        values = {"USER", "ADMIN", "MODERATOR"}
    )
    private String role;
    
    // Getters and setters
}

@SchemaClass(id = "confirmationRequest", description = "Confirmation request")
public class ConfirmationInput {
    @SchemaProperty(description = "Confirmation decision", required = true)
    private Boolean confirmed;
    
    @SchemaProperty(description = "Additional comments")
    private String comments;
    
    // Getters and setters
}
```

### 3. Create a Workflow

Extend `AnnotatedWorkflow` to define your workflow logic:

```java
@Component
public class UserRegistrationWorkflow extends AnnotatedWorkflow {
    
    @Override
    public String getWorkflowId() {
        return "user-registration";
    }
    
    @Override
    public boolean canHandle(String message, Map<String, String> properties) {
        return message != null && message.toLowerCase().contains("register");
    }
    
    @WorkflowStep(
        index = 1,
        inputClass = UserRegistrationInput.class,
        description = "Collect user registration information"
    )
    public StepEvent collectUserInfo(UserRegistrationInput input, WorkflowContext context) {
        // Validate input
        if (input.getAge() < 18) {
            return StepEvent.withError("User must be at least 18 years old");
        }
        
        // Store user data in context
        context.setContextValue("userData", input);
        
        // Return event with next step
        return StepEvent.of(input, ConfirmationInput.class);
    }
    
    @WorkflowStep(
        index = 2,
        inputClass = ConfirmationInput.class,
        description = "Confirm user registration"
    )
    public StepEvent confirmRegistration(ConfirmationInput input, WorkflowContext context) {
        UserRegistrationInput userData = context.getContextValue("userData", UserRegistrationInput.class);
        
        if (input.getConfirmed()) {
            // Process registration
            return StepEvent.withMessage("Registration completed successfully for " + userData.getFullName());
        } else {
            return StepEvent.withMessage("Registration cancelled");
        }
    }
}
```

### 4. Configuration

Configure the AI client in your `application.yml`:

```yaml
ai-props:
  host: https://api.openai.com
  username: your-api-key
  password: your-api-secret

spring:
  application:
    name: my-chat-assistant
```

### 5. Create a REST Controller

```java
@RestController
@RequestMapping("/api/chat")
public class ChatController {
    
    @Autowired
    private WorkflowRegistry workflowRegistry;
    
    @PostMapping("/message")
    public ResponseEntity<ChatResponse> processMessage(@RequestBody ChatRequest request) {
        // Find appropriate workflow
        ChatWorkflow workflow = workflowRegistry.findWorkflow(request.getMessage(), request.getPropertiesMap());
        
        if (workflow != null) {
            ChatResponse response = workflow.processChat(request);
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().build();
        }
    }
}
```

## 🎨 Advanced Features

### Asynchronous Step Execution

For long-running operations, use async steps:

```java
@WorkflowStep(
    index = 1,
    inputClass = DocumentProcessingInput.class,
    async = true,
    description = "Process document asynchronously"
)
public AsyncTaskEvent processDocument(DocumentProcessingInput input, WorkflowContext context) {
    // Return immediately with task information
    return AsyncTaskEvent.builder()
        .taskName("documentProcessing")
        .taskArgs(Map.of("documentId", input.getDocumentId()))
        .messageId("processing_document")
        .nextInputSchema(getSchemaFromClass(ProcessingResultInput.class))
        .build();
}

@AsyncStep(forStep = "documentProcessing")
public StepEvent executeDocumentProcessing(Map<String, Object> taskArgs, WorkflowContext context) {
    String documentId = (String) taskArgs.get("documentId");
    
    // Simulate long-running processing
    try {
        Thread.sleep(5000); // Simulate processing time
        
        // Update progress
        return StepEvent.builder()
            .completed(true)
            .percentComplete(100)
            .properties(Map.of("result", "Document processed successfully"))
            .build();
    } catch (InterruptedException e) {
        return StepEvent.withError("Processing interrupted");
    }
}
```

### Conditional Flow Control

Use Spring Expression Language for dynamic workflow routing:

```java
@WorkflowStep(
    index = 1,
    inputClass = AgeVerificationInput.class,
    condition = "#input.age >= 18",
    onTrue = "adultStep",
    onFalse = "minorStep"
)
public StepEvent checkAge(AgeVerificationInput input, WorkflowContext context) {
    return StepEvent.withProperty("age", input.getAge().toString());
}

@WorkflowStep(
    index = 2,
    id = "adultStep",
    inputClass = AdultServicesInput.class
)
public StepEvent handleAdultUser(AdultServicesInput input, WorkflowContext context) {
    return StepEvent.withMessage("Welcome to adult services");
}

@WorkflowStep(
    index = 3,
    id = "minorStep",
    inputClass = MinorServicesInput.class
)
public StepEvent handleMinorUser(MinorServicesInput input, WorkflowContext context) {
    return StepEvent.withMessage("Welcome to youth services");
}
```

### Composable Schemas

Break complex forms into multiple steps:

```java
@SchemaClass(
    id = "customerInfo",
    description = "Complete customer information",
    composable = true
)
public class CustomerInfo {
    @SchemaProperty(description = "Customer's full name", required = true)
    private String fullName;
    
    @SchemaProperty(description = "Customer's email", required = true)
    private String email;
    
    @SchemaProperty(description = "Customer's phone number")
    private String phoneNumber;
    
    @SchemaProperty(description = "Customer's address")
    private String address;
    
    @SchemaProperty(description = "Customer's preferences")
    private String preferences;
    
    // Getters and setters
}
```

When `composable = true`, the framework automatically creates separate interaction steps for each field, making the user experience more conversational.

### Multi-Schema Steps

Handle multiple input types in a single step:

```java
@WorkflowStep(
    index = 1,
    inputClasses = {EmailInput.class, PhoneInput.class, SocialMediaInput.class},
    description = "Choose contact method"
)
public StepEvent selectContactMethod(Object input, WorkflowContext context) {
    if (input instanceof EmailInput) {
        return handleEmailContact((EmailInput) input, context);
    } else if (input instanceof PhoneInput) {
        return handlePhoneContact((PhoneInput) input, context);
    } else if (input instanceof SocialMediaInput) {
        return handleSocialMediaContact((SocialMediaInput) input, context);
    }
    
    return StepEvent.withError("Invalid contact method");
}
```

### Progress Tracking

Track progress for long-running operations:

```java
@AsyncStep(forStep = "dataAnalysis")
public StepEvent performDataAnalysis(Map<String, Object> taskArgs, WorkflowContext context) {
    String dataSetId = (String) taskArgs.get("dataSetId");
    
    // Initial progress
    updateProgress(0, "Starting analysis...");
    
    // Simulate analysis phases
    for (int i = 1; i <= 10; i++) {
        try {
            Thread.sleep(1000); // Simulate work
            int progress = i * 10;
            updateProgress(progress, "Processing phase " + i + "/10");
        } catch (InterruptedException e) {
            return StepEvent.withError("Analysis interrupted");
        }
    }
    
    return StepEvent.builder()
        .completed(true)
        .percentComplete(100)
        .properties(Map.of("result", "Analysis completed successfully"))
        .build();
}

private void updateProgress(int percent, String message) {
    // Update progress in tracking system
    // This would typically update a database or cache
}
```

### Error Handling and Recovery

Implement comprehensive error handling:

```java
@WorkflowStep(
    index = 1,
    inputClass = PaymentInput.class,
    description = "Process payment"
)
public StepEvent processPayment(PaymentInput input, WorkflowContext context) {
    try {
        // Validate payment information
        if (!isValidPaymentInfo(input)) {
            return StepEvent.withError("Invalid payment information. Please check your details.");
        }
        
        // Process payment
        PaymentResult result = paymentService.processPayment(input);
        
        if (result.isSuccess()) {
            context.setContextValue("paymentId", result.getPaymentId());
            return StepEvent.of(result, ConfirmationInput.class);
        } else {
            return StepEvent.withError("Payment failed: " + result.getErrorMessage());
        }
        
    } catch (PaymentException e) {
        log.error("Payment processing failed", e);
        return StepEvent.withError("Payment processing temporarily unavailable. Please try again later.");
    } catch (Exception e) {
        log.error("Unexpected error during payment processing", e);
        return StepEvent.withError("An unexpected error occurred. Please contact support.");
    }
}
```

## 🛠️ Configuration Options

### AI Client Configuration

```yaml
ai-props:
  host: https://api.openai.com
  username: ${OPENAI_API_KEY}
  password: ${OPENAI_API_SECRET}
  timeout: 30000
  max-retries: 3
```

### Workflow Configuration

```yaml
driftkit:
  workflows:
    default-timeout: 300000  # 5 minutes
    max-steps: 50
    enable-debug: true
    session-timeout: 1800000  # 30 minutes
```

### Logging Configuration

```yaml
logging:
  level:
    ai.driftkit.chat.framework: DEBUG
    ai.driftkit.chat.framework.workflow: INFO
    ai.driftkit.chat.framework.service: WARN
```

## 📊 Monitoring and Observability

### Health Checks

```java
@Component
public class WorkflowHealthIndicator implements HealthIndicator {
    
    @Autowired
    private WorkflowRegistry workflowRegistry;
    
    @Override
    public Health health() {
        try {
            int workflowCount = workflowRegistry.getRegisteredWorkflows().size();
            
            return Health.up()
                .withDetail("registered-workflows", workflowCount)
                .withDetail("framework-version", "1.0-SNAPSHOT")
                .build();
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
```

### Metrics

```java
@Component
public class WorkflowMetrics {
    
    private final MeterRegistry meterRegistry;
    private final Counter workflowExecutions;
    private final Timer workflowDuration;
    
    public WorkflowMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.workflowExecutions = Counter.builder("workflow.executions")
            .description("Number of workflow executions")
            .register(meterRegistry);
        this.workflowDuration = Timer.builder("workflow.duration")
            .description("Workflow execution duration")
            .register(meterRegistry);
    }
    
    public void recordExecution(String workflowId, Duration duration) {
        workflowExecutions.increment(Tags.of("workflow", workflowId));
        workflowDuration.record(duration);
    }
}
```

## 🏗️ Architecture Overview

### Core Components

```
┌─────────────────────────────────────────────────────────────────┐
│                        Framework Architecture                    │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │   Annotations   │  │     Events      │  │     Models      │ │
│  │                 │  │                 │  │                 │ │
│  │ @WorkflowStep   │  │   StepEvent     │  │WorkflowContext  │ │
│  │ @AsyncStep      │  │AsyncTaskEvent   │  │StepDefinition   │ │
│  │ @SchemaClass    │  │                 │  │                 │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │   Workflows     │  │    Services     │  │  Repositories   │ │
│  │                 │  │                 │  │                 │ │
│  │AnnotatedWorkflow│  │ChatHistoryService│  │WorkflowContext  │ │
│  │ WorkflowRegistry│  │ChatMessageService│  │   Repository    │ │
│  │                 │  │AsyncResponseTracker│  │              │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │   AI Client     │  │    Utilities    │  │    Framework    │ │
│  │                 │  │                 │  │                 │ │
│  │    AiClient     │  │  SchemaUtils    │  │ ApplicationContext│ │
│  │ AIFunctionSchema│  │    AIUtils      │  │   Provider      │ │
│  │                 │  │                 │  │                 │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

### Workflow Execution Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                     Workflow Execution Flow                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. Chat Request  →  2. Workflow Selection  →  3. Session Mgmt │
│                                                                 │
│          ↓                       ↓                      ↓      │
│                                                                 │
│  4. Step Discovery  →  5. Schema Generation  →  6. Validation  │
│                                                                 │
│          ↓                       ↓                      ↓      │
│                                                                 │
│  7. Step Execution  →  8. Event Processing  →  9. Response     │
│                                                                 │
│          ↓                       ↓                      ↓      │
│                                                                 │
│  10. State Update  →  11. History Tracking  →  12. Completion  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## 🔌 Integration Examples

### Spring Security Integration

```java
@Component
public class SecureWorkflow extends AnnotatedWorkflow {
    
    @Autowired
    private SecurityContextHolder securityContextHolder;
    
    @Override
    public boolean canHandle(String message, Map<String, String> properties) {
        // Check user permissions
        Authentication auth = securityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_USER"));
    }
    
    @WorkflowStep(index = 1, inputClass = SecureInput.class)
    public StepEvent handleSecureOperation(SecureInput input, WorkflowContext context) {
        // Access user information
        String username = securityContextHolder.getContext().getAuthentication().getName();
        context.setContextValue("username", username);
        
        return StepEvent.withMessage("Secure operation completed for " + username);
    }
}
```

### Database Integration

```java
@Component
public class DatabaseIntegratedWorkflow extends AnnotatedWorkflow {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private TransactionTemplate transactionTemplate;
    
    @WorkflowStep(index = 1, inputClass = UserLookupInput.class)
    public StepEvent findUser(UserLookupInput input, WorkflowContext context) {
        return transactionTemplate.execute(status -> {
            Optional<User> user = userRepository.findByEmail(input.getEmail());
            
            if (user.isPresent()) {
                context.setContextValue("user", user.get());
                return StepEvent.of(user.get(), UserDetailsInput.class);
            } else {
                return StepEvent.withError("User not found");
            }
        });
    }
}
```

### External API Integration

```java
@Component
public class ExternalAPIWorkflow extends AnnotatedWorkflow {
    
    @Autowired
    private RestTemplate restTemplate;
    
    @WorkflowStep(index = 1, inputClass = WeatherInput.class, async = true)
    public AsyncTaskEvent getWeather(WeatherInput input, WorkflowContext context) {
        return AsyncTaskEvent.builder()
            .taskName("fetchWeather")
            .taskArgs(Map.of("location", input.getLocation()))
            .messageId("fetching_weather")
            .build();
    }
    
    @AsyncStep(forStep = "fetchWeather")
    public StepEvent fetchWeatherData(Map<String, Object> taskArgs, WorkflowContext context) {
        String location = (String) taskArgs.get("location");
        
        try {
            String url = "https://api.weather.com/v1/current?location=" + location;
            WeatherResponse response = restTemplate.getForObject(url, WeatherResponse.class);
            
            return StepEvent.builder()
                .completed(true)
                .percentComplete(100)
                .properties(Map.of(
                    "temperature", response.getTemperature().toString(),
                    "condition", response.getCondition()
                ))
                .build();
        } catch (Exception e) {
            return StepEvent.withError("Failed to fetch weather data: " + e.getMessage());
        }
    }
}
```

## 🔧 Annotation Reference

### @WorkflowStep

```java
@WorkflowStep(
    index = 1,                              // Step order (required)
    id = "customStepId",                    // Custom step ID (optional)
    description = "Step description",        // Human-readable description
    inputClass = InputClass.class,          // Single input class
    inputClasses = {Input1.class, Input2.class}, // Multiple input classes
    outputClasses = {Output1.class},        // Output classes
    nextClasses = {NextInput.class},        // Next step input classes
    nextSteps = {"stepId1", "stepId2"},     // Possible next steps
    requiresUserInput = true,               // Requires user input
    async = false,                          // Asynchronous execution
    condition = "#input.age >= 18",         // Condition expression
    onTrue = "adultStep",                   // Step if condition is true
    onFalse = "minorStep",                  // Step if condition is false
    inputSchemaId = "customSchema",         // Input schema ID
    outputSchemaId = "outputSchema"         // Output schema ID
)
```

### @AsyncStep

```java
@AsyncStep(
    forStep = "stepId",                     // Associated step ID (required)
    inputClass = InputClass.class,          // Input class
    inputClasses = {Input1.class},          // Multiple input classes
    outputClass = OutputClass.class,        // Output class
    nextClasses = {NextInput.class}         // Next step input classes
)
```

### @SchemaClass

```java
@SchemaClass(
    id = "schemaId",                        // Schema identifier
    description = "Schema description",      // Schema description
    composable = false                      // Composable schema flag
)
```

### @SchemaProperty

```java
@SchemaProperty(
    nameId = "propertyId",                  // Property identifier
    description = "Property description",    // Property description
    required = true,                        // Required flag
    defaultValue = "default",               // Default value
    minValue = 0,                          // Minimum value
    maxValue = 100,                        // Maximum value
    minLength = 1,                         // Minimum length
    maxLength = 255,                       // Maximum length
    values = {"option1", "option2"},       // Enum values
    array = false,                         // Array flag
    multiSelect = false,                   // Multi-select flag
    valueAsNameId = false,                 // Value as name ID flag
    type = String.class                    // Property type
)
```

## 🐛 Troubleshooting

### Common Issues

#### 1. Workflow Not Found

**Problem**: Workflow not being selected for user input.

**Solution**:
```java
@Override
public boolean canHandle(String message, Map<String, String> properties) {
    // Make sure this method returns true for expected inputs
    return message != null && message.toLowerCase().contains("expected keyword");
}
```

#### 2. Schema Generation Errors

**Problem**: Schema not being generated correctly.

**Solution**:
```java
// Ensure proper annotations
@SchemaClass(id = "uniqueId", description = "Clear description")
public class MySchema {
    @SchemaProperty(description = "Field description", required = true)
    private String field;
    
    // Public constructor required
    public MySchema() {}
    
    // Getters and setters required
}
```

#### 3. Async Step Not Executing

**Problem**: Async step not being called.

**Solution**:
```java
// Ensure task name matches
@WorkflowStep(async = true)
public AsyncTaskEvent startTask() {
    return AsyncTaskEvent.builder()
        .taskName("exactTaskName")  // Must match @AsyncStep forStep
        .build();
}

@AsyncStep(forStep = "exactTaskName")  // Must match taskName
public StepEvent executeTask() {
    // Implementation
}
```

#### 4. Session State Issues

**Problem**: Session state not persisting.

**Solution**:
```java
@Component
public class MyWorkflowContextRepository implements WorkflowContextRepository {
    // Ensure proper implementation with actual persistence
    // Don't use in-memory maps in production
}
```

### Debug Configuration

```yaml
logging:
  level:
    ai.driftkit.chat.framework: DEBUG
    ai.driftkit.chat.framework.workflow.AnnotatedWorkflow: TRACE
    ai.driftkit.chat.framework.util.SchemaUtils: DEBUG
```

### Performance Optimization

```java
// Cache schema generation
@Configuration
public class SchemaConfiguration {
    
    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("schemas", "workflows");
    }
    
    @Cacheable("schemas")
    public AIFunctionSchema getSchema(Class<?> clazz) {
        return SchemaUtils.getSchemaFromClass(clazz);
    }
}
```

## 📈 Performance Considerations

### Best Practices

1. **Schema Caching**: Schemas are automatically cached, but consider external caching for high-load scenarios
2. **Async Processing**: Use async steps for operations taking more than 1-2 seconds
3. **Database Optimization**: Implement efficient WorkflowContextRepository with proper indexing
4. **Memory Management**: Clean up old sessions periodically
5. **Connection Pooling**: Configure appropriate connection pools for AI client

### Monitoring

```java
@Component
public class WorkflowPerformanceMonitor {
    
    @EventListener
    public void handleStepExecution(StepExecutionEvent event) {
        long duration = event.getExecutionTime();
        if (duration > 1000) { // Log slow operations
            log.warn("Slow step execution: {} took {}ms", 
                event.getStepId(), duration);
        }
    }
}
```

## 🤝 Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is part of the DriftKit framework and is licensed under the terms specified in the main DriftKit project.

## 🆘 Support

- **Documentation**: [DriftKit Documentation](https://driftkit.ai/docs)
- **Issues**: [GitHub Issues](https://github.com/driftkit/framework/issues)
- **Discussions**: [GitHub Discussions](https://github.com/driftkit/framework/discussions)
- **Email**: support@driftkit.ai

## 🔗 Related Projects

- [DriftKit Common](../driftkit-common) - Core utilities and shared components
- [DriftKit Workflows](../driftkit-workflows) - Workflow execution engine
- [DriftKit Context Engineering](../driftkit-context-engineering) - Prompt management system
- [DriftKit Vector](../driftkit-vector) - Vector storage and retrieval

## 🌟 Real-World Demo Examples

### 1. E-commerce Order Assistant

This example demonstrates a complete e-commerce order workflow with product search, cart management, and checkout.

```java
@Component
public class EcommerceOrderWorkflow extends AnnotatedWorkflow {
    
    @Autowired
    private ProductService productService;
    
    @Autowired
    private InventoryService inventoryService;
    
    @Autowired
    private PaymentService paymentService;
    
    @Autowired
    private OrderService orderService;
    
    @Override
    public String getWorkflowId() {
        return "ecommerce-order";
    }
    
    @Override
    public boolean canHandle(String message, Map<String, String> properties) {
        return message.toLowerCase().contains("order") || 
               message.toLowerCase().contains("buy") ||
               message.toLowerCase().contains("purchase");
    }
    
    @WorkflowStep(
        index = 1,
        inputClass = ProductSearchInput.class,
        description = "Search for products"
    )
    public StepEvent searchProducts(ProductSearchInput input, WorkflowContext context) {
        List<Product> products = productService.searchProducts(input.getQuery(), input.getCategory());
        
        if (products.isEmpty()) {
            return StepEvent.withMessage("No products found. Try a different search term.");
        }
        
        context.setContextValue("searchResults", products);
        
        // Generate product selection options
        ProductSelectionInput selectionInput = new ProductSelectionInput();
        selectionInput.setProducts(products.stream()
            .map(p -> new ProductOption(p.getId(), p.getName(), p.getPrice()))
            .collect(Collectors.toList()));
        
        return StepEvent.of(selectionInput, ProductSelectionInput.class);
    }
    
    @WorkflowStep(
        index = 2,
        inputClass = ProductSelectionInput.class,
        description = "Select products and quantities"
    )
    public StepEvent addToCart(ProductSelectionInput input, WorkflowContext context) {
        Cart cart = context.getContextValue("cart", Cart.class);
        if (cart == null) {
            cart = new Cart();
        }
        
        // Add selected products to cart
        for (SelectedProduct selected : input.getSelectedProducts()) {
            Product product = productService.getProduct(selected.getProductId());
            
            // Check inventory
            if (!inventoryService.isAvailable(product.getId(), selected.getQuantity())) {
                return StepEvent.withError(
                    String.format("%s is out of stock. Only %d available.",
                        product.getName(),
                        inventoryService.getAvailableQuantity(product.getId()))
                );
            }
            
            cart.addItem(product, selected.getQuantity());
        }
        
        context.setContextValue("cart", cart);
        
        // Show cart summary and ask for next action
        CartSummary summary = new CartSummary();
        summary.setItems(cart.getItems());
        summary.setSubtotal(cart.getSubtotal());
        summary.setTax(cart.getTax());
        summary.setTotal(cart.getTotal());
        
        return StepEvent.of(summary, CartActionInput.class);
    }
    
    @WorkflowStep(
        index = 3,
        inputClass = CartActionInput.class,
        description = "Handle cart actions",
        condition = "#input.action == 'CHECKOUT'",
        onTrue = "collectShipping",
        onFalse = "continueShopping"
    )
    public StepEvent handleCartAction(CartActionInput input, WorkflowContext context) {
        return StepEvent.withProperty("action", input.getAction());
    }
    
    @WorkflowStep(
        index = 4,
        id = "collectShipping",
        inputClass = ShippingInput.class,
        description = "Collect shipping information"
    )
    public StepEvent collectShippingInfo(ShippingInput input, WorkflowContext context) {
        // Validate address
        if (!addressService.validateAddress(input)) {
            return StepEvent.withError("Invalid address. Please check and try again.");
        }
        
        // Calculate shipping options
        Cart cart = context.getContextValue("cart", Cart.class);
        List<ShippingOption> options = shippingService.calculateOptions(cart, input.getAddress());
        
        context.setContextValue("shippingAddress", input);
        
        return StepEvent.of(
            new ShippingSelectionInput(options),
            ShippingSelectionInput.class
        );
    }
    
    @WorkflowStep(
        index = 5,
        inputClass = ShippingSelectionInput.class,
        description = "Select shipping method"
    )
    public StepEvent selectShipping(ShippingSelectionInput input, WorkflowContext context) {
        context.setContextValue("shippingMethod", input.getSelectedOption());
        
        // Update cart with shipping cost
        Cart cart = context.getContextValue("cart", Cart.class);
        cart.setShippingCost(input.getSelectedOption().getCost());
        
        return StepEvent.of(new PaymentInput(), PaymentInput.class);
    }
    
    @WorkflowStep(
        index = 6,
        inputClass = PaymentInput.class,
        description = "Process payment",
        async = true
    )
    public AsyncTaskEvent processPayment(PaymentInput input, WorkflowContext context) {
        Cart cart = context.getContextValue("cart", Cart.class);
        
        return AsyncTaskEvent.builder()
            .taskName("paymentProcessing")
            .taskArgs(Map.of(
                "paymentMethod", input.getPaymentMethod(),
                "amount", cart.getTotal(),
                "cartId", cart.getId()
            ))
            .messageId("processing_payment")
            .nextInputSchema(getSchemaFromClass(OrderConfirmationInput.class))
            .build();
    }
    
    @AsyncStep(forStep = "paymentProcessing")
    public StepEvent executePayment(Map<String, Object> taskArgs, WorkflowContext context) {
        try {
            PaymentResult result = paymentService.processPayment(
                (String) taskArgs.get("paymentMethod"),
                (BigDecimal) taskArgs.get("amount")
            );
            
            if (result.isSuccess()) {
                // Create order
                Cart cart = context.getContextValue("cart", Cart.class);
                ShippingInput shipping = context.getContextValue("shippingAddress", ShippingInput.class);
                
                Order order = orderService.createOrder(cart, shipping, result.getTransactionId());
                context.setContextValue("orderId", order.getId());
                
                // Reserve inventory
                inventoryService.reserveItems(order.getItems());
                
                return StepEvent.builder()
                    .completed(true)
                    .percentComplete(100)
                    .properties(Map.of(
                        "orderId", order.getId(),
                        "orderNumber", order.getOrderNumber(),
                        "estimatedDelivery", order.getEstimatedDelivery()
                    ))
                    .build();
            } else {
                return StepEvent.withError("Payment failed: " + result.getErrorMessage());
            }
        } catch (Exception e) {
            return StepEvent.withError("Payment processing error: " + e.getMessage());
        }
    }
    
    @WorkflowStep(
        index = 7,
        id = "continueShopping",
        inputClass = ProductSearchInput.class,
        description = "Continue shopping"
    )
    public StepEvent continueShopping(ProductSearchInput input, WorkflowContext context) {
        // Loop back to product search
        return searchProducts(input, context);
    }
}
```

### 2. Healthcare Appointment Booking

This example shows a healthcare appointment booking system with doctor selection, availability checking, and confirmation.

```java
@Component
public class HealthcareAppointmentWorkflow extends AnnotatedWorkflow {
    
    @Autowired
    private DoctorService doctorService;
    
    @Autowired
    private AppointmentService appointmentService;
    
    @Autowired
    private NotificationService notificationService;
    
    @Override
    public String getWorkflowId() {
        return "healthcare-appointment";
    }
    
    @Override
    public boolean canHandle(String message, Map<String, String> properties) {
        return message.toLowerCase().contains("appointment") || 
               message.toLowerCase().contains("doctor") ||
               message.toLowerCase().contains("schedule");
    }
    
    @WorkflowStep(
        index = 1,
        inputClass = SymptomInput.class,
        description = "Describe your symptoms or reason for visit"
    )
    public StepEvent collectSymptoms(SymptomInput input, WorkflowContext context) {
        // Analyze symptoms to suggest appropriate specialists
        List<String> specialties = symptomAnalyzer.suggestSpecialties(input.getSymptoms());
        
        context.setContextValue("symptoms", input.getSymptoms());
        context.setContextValue("urgency", input.getUrgencyLevel());
        
        // For urgent cases, suggest immediate care
        if (input.getUrgencyLevel() == UrgencyLevel.EMERGENCY) {
            return StepEvent.withMessage(
                "This seems urgent. Please visit the emergency room or call 911."
            );
        }
        
        // Find available doctors
        List<Doctor> doctors = doctorService.findBySpecialties(specialties);
        
        DoctorSelectionInput selection = new DoctorSelectionInput();
        selection.setDoctors(doctors.stream()
            .map(d -> new DoctorOption(
                d.getId(),
                d.getName(),
                d.getSpecialty(),
                d.getRating(),
                d.getNextAvailable()
            ))
            .collect(Collectors.toList()));
        
        return StepEvent.of(selection, DoctorSelectionInput.class);
    }
    
    @WorkflowStep(
        index = 2,
        inputClass = DoctorSelectionInput.class,
        description = "Select a doctor"
    )
    public StepEvent selectDoctor(DoctorSelectionInput input, WorkflowContext context) {
        Doctor doctor = doctorService.getDoctor(input.getSelectedDoctorId());
        context.setContextValue("selectedDoctor", doctor);
        
        // Get available time slots for next 2 weeks
        LocalDate startDate = LocalDate.now().plusDays(1);
        LocalDate endDate = startDate.plusWeeks(2);
        
        List<TimeSlot> availableSlots = appointmentService.getAvailableSlots(
            doctor.getId(),
            startDate,
            endDate
        );
        
        if (availableSlots.isEmpty()) {
            return StepEvent.withMessage(
                "No appointments available in the next 2 weeks. Would you like to see other doctors?"
            );
        }
        
        TimeSlotSelectionInput slotSelection = new TimeSlotSelectionInput();
        slotSelection.setTimeSlots(availableSlots);
        slotSelection.setDoctorName(doctor.getName());
        
        return StepEvent.of(slotSelection, TimeSlotSelectionInput.class);
    }
    
    @WorkflowStep(
        index = 3,
        inputClass = TimeSlotSelectionInput.class,
        description = "Select appointment time"
    )
    public StepEvent selectTimeSlot(TimeSlotSelectionInput input, WorkflowContext context) {
        TimeSlot selectedSlot = input.getSelectedSlot();
        Doctor doctor = context.getContextValue("selectedDoctor", Doctor.class);
        
        // Check if slot is still available (prevent race conditions)
        if (!appointmentService.isSlotAvailable(doctor.getId(), selectedSlot)) {
            return StepEvent.withError(
                "Sorry, that time slot was just booked. Please select another time."
            );
        }
        
        context.setContextValue("selectedTimeSlot", selectedSlot);
        
        // Collect patient information
        return StepEvent.of(new PatientInfoInput(), PatientInfoInput.class);
    }
    
    @WorkflowStep(
        index = 4,
        inputClass = PatientInfoInput.class,
        description = "Provide patient information"
    )
    public StepEvent collectPatientInfo(PatientInfoInput input, WorkflowContext context) {
        // Validate insurance if provided
        if (input.hasInsurance()) {
            InsuranceValidation validation = insuranceService.validate(
                input.getInsuranceProvider(),
                input.getInsuranceNumber()
            );
            
            if (!validation.isValid()) {
                return StepEvent.withError(
                    "Insurance validation failed: " + validation.getErrorMessage()
                );
            }
            
            context.setContextValue("insuranceCoverage", validation.getCoverageInfo());
        }
        
        context.setContextValue("patientInfo", input);
        
        // Show appointment summary for confirmation
        AppointmentSummary summary = buildAppointmentSummary(context);
        
        return StepEvent.of(summary, AppointmentConfirmationInput.class);
    }
    
    @WorkflowStep(
        index = 5,
        inputClass = AppointmentConfirmationInput.class,
        description = "Confirm appointment booking",
        async = true
    )
    public AsyncTaskEvent confirmAppointment(AppointmentConfirmationInput input, WorkflowContext context) {
        if (!input.isConfirmed()) {
            return AsyncTaskEvent.builder()
                .taskName("cancelBooking")
                .messageId("booking_cancelled")
                .build();
        }
        
        return AsyncTaskEvent.builder()
            .taskName("bookAppointment")
            .taskArgs(Map.of(
                "doctorId", context.getContextValue("selectedDoctor", Doctor.class).getId(),
                "timeSlot", context.getContextValue("selectedTimeSlot"),
                "patientInfo", context.getContextValue("patientInfo"),
                "symptoms", context.getContextValue("symptoms")
            ))
            .messageId("booking_appointment")
            .build();
    }
    
    @AsyncStep(forStep = "bookAppointment")
    public StepEvent executeBooking(Map<String, Object> taskArgs, WorkflowContext context) {
        try {
            // Create appointment
            Appointment appointment = appointmentService.createAppointment(
                (String) taskArgs.get("doctorId"),
                (TimeSlot) taskArgs.get("timeSlot"),
                (PatientInfo) taskArgs.get("patientInfo"),
                (String) taskArgs.get("symptoms")
            );
            
            // Send confirmation notifications
            notificationService.sendAppointmentConfirmation(
                appointment,
                NotificationChannel.EMAIL,
                NotificationChannel.SMS
            );
            
            // Add to calendar
            String calendarLink = calendarService.createCalendarEvent(appointment);
            
            return StepEvent.builder()
                .completed(true)
                .percentComplete(100)
                .properties(Map.of(
                    "appointmentId", appointment.getId(),
                    "confirmationNumber", appointment.getConfirmationNumber(),
                    "calendarLink", calendarLink,
                    "message", "Appointment booked successfully! Confirmation sent to your email and phone."
                ))
                .build();
                
        } catch (Exception e) {
            log.error("Failed to book appointment", e);
            return StepEvent.withError("Failed to book appointment: " + e.getMessage());
        }
    }
    
    private AppointmentSummary buildAppointmentSummary(WorkflowContext context) {
        Doctor doctor = context.getContextValue("selectedDoctor", Doctor.class);
        TimeSlot timeSlot = context.getContextValue("selectedTimeSlot", TimeSlot.class);
        PatientInfo patient = context.getContextValue("patientInfo", PatientInfo.class);
        InsuranceCoverage coverage = context.getContextValue("insuranceCoverage", InsuranceCoverage.class);
        
        AppointmentSummary summary = new AppointmentSummary();
        summary.setDoctorName(doctor.getName());
        summary.setDoctorSpecialty(doctor.getSpecialty());
        summary.setDateTime(timeSlot.getDateTime());
        summary.setDuration(timeSlot.getDuration());
        summary.setLocation(doctor.getOfficeAddress());
        summary.setPatientName(patient.getFullName());
        summary.setEstimatedCost(calculateEstimatedCost(doctor, coverage));
        summary.setInsuranceCovered(coverage != null);
        
        return summary;
    }
}
```

### 3. Banking Virtual Assistant

This example demonstrates a banking assistant that handles account inquiries, transfers, and bill payments.

```java
@Component
public class BankingAssistantWorkflow extends AnnotatedWorkflow {
    
    @Autowired
    private AccountService accountService;
    
    @Autowired
    private TransactionService transactionService;
    
    @Autowired
    private SecurityService securityService;
    
    @Autowired
    private BillPayService billPayService;
    
    @Override
    public String getWorkflowId() {
        return "banking-assistant";
    }
    
    @Override
    public boolean canHandle(String message, Map<String, String> properties) {
        // Require authenticated user
        return properties.containsKey("userId") && 
               (message.toLowerCase().contains("account") ||
                message.toLowerCase().contains("transfer") ||
                message.toLowerCase().contains("balance") ||
                message.toLowerCase().contains("pay"));
    }
    
    @WorkflowStep(
        index = 1,
        inputClass = BankingActionInput.class,
        description = "What would you like to do?"
    )
    public StepEvent selectAction(BankingActionInput input, WorkflowContext context) {
        String userId = context.getProperty("userId");
        
        // Verify user session
        if (!securityService.isSessionValid(userId)) {
            return StepEvent.withError("Session expired. Please log in again.");
        }
        
        switch (input.getAction()) {
            case CHECK_BALANCE:
                return checkBalance(userId, context);
            case TRANSFER_MONEY:
                return StepEvent.of(new TransferInput(), TransferInput.class);
            case PAY_BILL:
                return StepEvent.of(new BillSelectionInput(), BillSelectionInput.class);
            case VIEW_TRANSACTIONS:
                return viewTransactions(userId, context);
            default:
                return StepEvent.withError("Unknown action");
        }
    }
    
    private StepEvent checkBalance(String userId, WorkflowContext context) {
        List<Account> accounts = accountService.getUserAccounts(userId);
        
        BalanceSummary summary = new BalanceSummary();
        summary.setAccounts(accounts.stream()
            .map(a -> new AccountBalance(
                a.getAccountNumber(),
                a.getType(),
                a.getBalance(),
                a.getAvailableBalance()
            ))
            .collect(Collectors.toList()));
        summary.setTotalBalance(accounts.stream()
            .map(Account::getBalance)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
        
        return StepEvent.withMessage(
            "Your account balances:\n" + formatBalances(summary)
        );
    }
    
    @WorkflowStep(
        index = 2,
        inputClass = TransferInput.class,
        description = "Enter transfer details"
    )
    public StepEvent setupTransfer(TransferInput input, WorkflowContext context) {
        String userId = context.getProperty("userId");
        
        // Validate source account
        Account sourceAccount = accountService.getAccount(userId, input.getFromAccountId());
        if (sourceAccount == null) {
            return StepEvent.withError("Invalid source account");
        }
        
        // Check balance
        if (sourceAccount.getAvailableBalance().compareTo(input.getAmount()) < 0) {
            return StepEvent.withError(
                String.format("Insufficient funds. Available: $%.2f", 
                    sourceAccount.getAvailableBalance())
            );
        }
        
        // Validate destination
        TransferValidation validation = transactionService.validateTransfer(
            sourceAccount,
            input.getToAccount(),
            input.getAmount()
        );
        
        if (!validation.isValid()) {
            return StepEvent.withError(validation.getErrorMessage());
        }
        
        context.setContextValue("transferDetails", input);
        context.setContextValue("sourceAccount", sourceAccount);
        
        // Require 2FA for transfers
        return StepEvent.of(new TwoFactorInput(), TwoFactorInput.class);
    }
    
    @WorkflowStep(
        index = 3,
        inputClass = TwoFactorInput.class,
        description = "Enter verification code",
        async = true
    )
    public AsyncTaskEvent verifyAndTransfer(TwoFactorInput input, WorkflowContext context) {
        String userId = context.getProperty("userId");
        
        // Verify 2FA code
        if (!securityService.verify2FA(userId, input.getCode())) {
            return AsyncTaskEvent.builder()
                .taskName("transferFailed")
                .messageId("invalid_2fa")
                .build();
        }
        
        TransferInput transfer = context.getContextValue("transferDetails", TransferInput.class);
        
        return AsyncTaskEvent.builder()
            .taskName("executeTransfer")
            .taskArgs(Map.of(
                "userId", userId,
                "transfer", transfer
            ))
            .messageId("processing_transfer")
            .build();
    }
    
    @AsyncStep(forStep = "executeTransfer")
    public StepEvent performTransfer(Map<String, Object> taskArgs, WorkflowContext context) {
        try {
            String userId = (String) taskArgs.get("userId");
            TransferInput transfer = (TransferInput) taskArgs.get("transfer");
            
            // Execute transfer
            TransactionResult result = transactionService.executeTransfer(
                userId,
                transfer.getFromAccountId(),
                transfer.getToAccount(),
                transfer.getAmount(),
                transfer.getMemo()
            );
            
            if (result.isSuccess()) {
                // Send confirmation
                notificationService.sendTransferConfirmation(userId, result);
                
                return StepEvent.builder()
                    .completed(true)
                    .percentComplete(100)
                    .properties(Map.of(
                        "transactionId", result.getTransactionId(),
                        "confirmationNumber", result.getConfirmationNumber(),
                        "message", String.format(
                            "Transfer of $%.2f completed successfully. Confirmation: %s",
                            transfer.getAmount(),
                            result.getConfirmationNumber()
                        )
                    ))
                    .build();
            } else {
                return StepEvent.withError("Transfer failed: " + result.getErrorMessage());
            }
            
        } catch (Exception e) {
            log.error("Transfer processing error", e);
            return StepEvent.withError("Transfer processing error. Please try again.");
        }
    }
    
    @WorkflowStep(
        index = 2,
        inputClass = BillSelectionInput.class,
        description = "Select bill to pay"
    )
    public StepEvent selectBill(BillSelectionInput input, WorkflowContext context) {
        String userId = context.getProperty("userId");
        
        if (input.getAction() == BillAction.VIEW_BILLS) {
            List<Bill> bills = billPayService.getUpcomingBills(userId);
            
            BillListDisplay display = new BillListDisplay();
            display.setBills(bills);
            display.setTotalDue(bills.stream()
                .map(Bill::getAmountDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
            
            return StepEvent.of(display, BillPaymentInput.class);
        } else {
            // Add new payee
            return StepEvent.of(new PayeeInput(), PayeeInput.class);
        }
    }
}
```

### 4. Travel Planning Assistant

This example shows a comprehensive travel planning workflow with flight search, hotel booking, and itinerary creation.

```java
@Component
public class TravelPlanningWorkflow extends AnnotatedWorkflow {
    
    @Autowired
    private FlightSearchService flightService;
    
    @Autowired
    private HotelSearchService hotelService;
    
    @Autowired
    private ActivityService activityService;
    
    @Autowired
    private ItineraryService itineraryService;
    
    @Override
    public String getWorkflowId() {
        return "travel-planning";
    }
    
    @WorkflowStep(
        index = 1,
        inputClass = TravelBasicsInput.class,
        description = "Tell me about your travel plans"
    )
    public StepEvent collectTravelBasics(TravelBasicsInput input, WorkflowContext context) {
        // Validate travel dates
        if (input.getDepartureDate().isBefore(LocalDate.now())) {
            return StepEvent.withError("Departure date must be in the future");
        }
        
        if (input.getReturnDate().isBefore(input.getDepartureDate())) {
            return StepEvent.withError("Return date must be after departure date");
        }
        
        context.setContextValue("travelBasics", input);
        
        // Check if it's a popular destination with package deals
        List<TravelPackage> packages = packageService.findPackages(
            input.getOrigin(),
            input.getDestination(),
            input.getDepartureDate(),
            input.getReturnDate(),
            input.getTravelerCount()
        );
        
        if (!packages.isEmpty()) {
            PackageSelectionInput packageInput = new PackageSelectionInput();
            packageInput.setPackages(packages);
            packageInput.setAllowCustom(true);
            
            return StepEvent.of(packageInput, PackageSelectionInput.class);
        }
        
        // No packages, proceed with custom planning
        return StepEvent.of(new FlightPreferenceInput(), FlightPreferenceInput.class);
    }
    
    @WorkflowStep(
        index = 2,
        inputClass = FlightPreferenceInput.class,
        description = "Flight preferences",
        async = true
    )
    public AsyncTaskEvent searchFlights(FlightPreferenceInput input, WorkflowContext context) {
        TravelBasicsInput basics = context.getContextValue("travelBasics", TravelBasicsInput.class);
        
        return AsyncTaskEvent.builder()
            .taskName("flightSearch")
            .taskArgs(Map.of(
                "origin", basics.getOrigin(),
                "destination", basics.getDestination(),
                "departureDate", basics.getDepartureDate(),
                "returnDate", basics.getReturnDate(),
                "travelers", basics.getTravelerCount(),
                "preferences", input
            ))
            .messageId("searching_flights")
            .nextInputSchema(getSchemaFromClass(FlightSelectionInput.class))
            .build();
    }
    
    @AsyncStep(forStep = "flightSearch")
    public StepEvent performFlightSearch(Map<String, Object> taskArgs, WorkflowContext context) {
        try {
            // Search flights with progress updates
            updateProgress(0, "Searching flights...");
            
            FlightSearchCriteria criteria = buildSearchCriteria(taskArgs);
            List<FlightOption> flights = flightService.searchFlights(criteria);
            
            updateProgress(50, "Found " + flights.size() + " flights");
            
            // Sort by price and duration
            flights.sort(Comparator
                .comparing(FlightOption::getTotalPrice)
                .thenComparing(FlightOption::getTotalDuration));
            
            updateProgress(75, "Analyzing best options...");
            
            // Get top 5 options
            List<FlightOption> topFlights = flights.stream()
                .limit(5)
                .collect(Collectors.toList());
            
            context.setContextValue("flightOptions", topFlights);
            
            FlightSelectionInput selection = new FlightSelectionInput();
            selection.setFlights(topFlights);
            
            return StepEvent.builder()
                .completed(true)
                .percentComplete(100)
                .data(selection)
                .build();
                
        } catch (Exception e) {
            return StepEvent.withError("Flight search failed: " + e.getMessage());
        }
    }
    
    @WorkflowStep(
        index = 3,
        inputClass = FlightSelectionInput.class,
        description = "Select your flights"
    )
    public StepEvent selectFlights(FlightSelectionInput input, WorkflowContext context) {
        FlightOption selected = input.getSelectedFlight();
        context.setContextValue("selectedFlight", selected);
        
        // Calculate hotel search parameters based on flight times
        LocalDateTime arrivalTime = selected.getOutboundArrival();
        LocalDateTime departureTime = selected.getReturnDeparture();
        
        HotelSearchInput hotelSearch = new HotelSearchInput();
        hotelSearch.setCheckIn(arrivalTime.toLocalDate());
        hotelSearch.setCheckOut(departureTime.toLocalDate());
        hotelSearch.setLocation(selected.getDestinationCity());
        
        return StepEvent.of(hotelSearch, HotelPreferenceInput.class);
    }
    
    @WorkflowStep(
        index = 4,
        inputClass = HotelPreferenceInput.class,
        description = "Hotel preferences"
    )
    public StepEvent searchHotels(HotelPreferenceInput input, WorkflowContext context) {
        HotelSearchInput searchParams = context.getContextValue("hotelSearch", HotelSearchInput.class);
        
        List<Hotel> hotels = hotelService.searchHotels(
            searchParams.getLocation(),
            searchParams.getCheckIn(),
            searchParams.getCheckOut(),
            input
        );
        
        // Filter by preferences
        hotels = hotels.stream()
            .filter(h -> h.getStarRating() >= input.getMinStarRating())
            .filter(h -> h.getPricePerNight().compareTo(input.getMaxPricePerNight()) <= 0)
            .sorted(Comparator.comparing(Hotel::getGuestRating).reversed())
            .limit(5)
            .collect(Collectors.toList());
        
        HotelSelectionInput selection = new HotelSelectionInput();
        selection.setHotels(hotels);
        
        return StepEvent.of(selection, HotelSelectionInput.class);
    }
    
    @WorkflowStep(
        index = 5,
        inputClass = ActivityPreferenceInput.class,
        description = "What activities interest you?"
    )
    public StepEvent suggestActivities(ActivityPreferenceInput input, WorkflowContext context) {
        TravelBasicsInput basics = context.getContextValue("travelBasics", TravelBasicsInput.class);
        
        List<Activity> activities = activityService.findActivities(
            basics.getDestination(),
            input.getInterests(),
            input.getActivityLevel(),
            input.getBudgetPerDay()
        );
        
        // Group by day
        Map<LocalDate, List<Activity>> dailyActivities = groupActivitiesByDay(
            activities,
            basics.getDepartureDate(),
            basics.getReturnDate()
        );
        
        ItineraryDraft draft = new ItineraryDraft();
        draft.setDailyActivities(dailyActivities);
        draft.setFlight(context.getContextValue("selectedFlight", FlightOption.class));
        draft.setHotel(context.getContextValue("selectedHotel", Hotel.class));
        
        return StepEvent.of(draft, ItineraryConfirmationInput.class);
    }
    
    @WorkflowStep(
        index = 6,
        inputClass = ItineraryConfirmationInput.class,
        description = "Review and confirm your itinerary"
    )
    public StepEvent confirmItinerary(ItineraryConfirmationInput input, WorkflowContext context) {
        if (!input.isConfirmed()) {
            return StepEvent.withMessage("No problem! Let me know if you'd like to plan another trip.");
        }
        
        // Create final itinerary
        Itinerary itinerary = itineraryService.createItinerary(context);
        
        // Generate booking links
        BookingLinks links = bookingService.generateBookingLinks(itinerary);
        
        // Create calendar events
        String calendarFile = calendarService.exportItinerary(itinerary);
        
        return StepEvent.builder()
            .completed(true)
            .properties(Map.of(
                "itineraryId", itinerary.getId(),
                "totalCost", itinerary.getTotalCost(),
                "bookingLinks", links,
                "calendarFile", calendarFile,
                "message", "Your travel itinerary is ready! Check your email for booking links and calendar invites."
            ))
            .build();
    }
}
```

### 5. HR Onboarding Assistant

This example demonstrates an employee onboarding workflow with document collection, training scheduling, and equipment setup.

```java
@Component  
public class HROnboardingWorkflow extends AnnotatedWorkflow {
    
    @Autowired
    private EmployeeService employeeService;
    
    @Autowired
    private DocumentService documentService;
    
    @Autowired
    private TrainingService trainingService;
    
    @Autowired
    private ITService itService;
    
    @Autowired
    private FacilitiesService facilitiesService;
    
    @Override
    public String getWorkflowId() {
        return "hr-onboarding";
    }
    
    @WorkflowStep(
        index = 1,
        inputClass = NewEmployeeInput.class,
        description = "Welcome! Let's get your information"
    )
    public StepEvent collectEmployeeInfo(NewEmployeeInput input, WorkflowContext context) {
        // Validate employee ID
        Employee employee = employeeService.findByEmail(input.getEmail());
        if (employee == null) {
            return StepEvent.withError("Employee record not found. Please contact HR.");
        }
        
        context.setContextValue("employee", employee);
        context.setContextValue("startDate", input.getStartDate());
        
        // Get required documents for role
        List<DocumentRequirement> requiredDocs = documentService.getRequiredDocuments(
            employee.getRole(),
            employee.getDepartment(),
            employee.getLocation()
        );
        
        DocumentCollectionInput docInput = new DocumentCollectionInput();
        docInput.setRequiredDocuments(requiredDocs);
        docInput.setEmployeeName(employee.getFullName());
        
        return StepEvent.of(docInput, DocumentCollectionInput.class);
    }
    
    @WorkflowStep(
        index = 2,
        inputClass = DocumentCollectionInput.class,
        description = "Upload required documents",
        async = true
    )
    public AsyncTaskEvent processDocuments(DocumentCollectionInput input, WorkflowContext context) {
        return AsyncTaskEvent.builder()
            .taskName("documentProcessing")
            .taskArgs(Map.of(
                "documents", input.getUploadedDocuments(),
                "employeeId", context.getContextValue("employee", Employee.class).getId()
            ))
            .messageId("processing_documents")
            .nextInputSchema(getSchemaFromClass(EmergencyContactInput.class))
            .build();
    }
    
    @AsyncStep(forStep = "documentProcessing")
    public StepEvent verifyDocuments(Map<String, Object> taskArgs, WorkflowContext context) {
        List<UploadedDocument> documents = (List<UploadedDocument>) taskArgs.get("documents");
        String employeeId = (String) taskArgs.get("employeeId");
        
        updateProgress(0, "Verifying documents...");
        
        List<DocumentVerificationResult> results = new ArrayList<>();
        int processed = 0;
        
        for (UploadedDocument doc : documents) {
            DocumentVerificationResult result = documentService.verifyDocument(doc);
            results.add(result);
            
            processed++;
            int progress = (processed * 100) / documents.size();
            updateProgress(progress, "Verified " + doc.getType());
            
            if (!result.isValid()) {
                return StepEvent.withError(
                    "Document verification failed for " + doc.getType() + ": " + result.getErrorMessage()
                );
            }
        }
        
        // Store verified documents
        documentService.storeEmployeeDocuments(employeeId, documents);
        context.setContextValue("documentsVerified", true);
        
        return StepEvent.builder()
            .completed(true)
            .percentComplete(100)
            .build();
    }
    
    @WorkflowStep(
        index = 3,
        inputClass = EmergencyContactInput.class,
        description = "Emergency contact information"
    )
    public StepEvent collectEmergencyContacts(EmergencyContactInput input, WorkflowContext context) {
        Employee employee = context.getContextValue("employee", Employee.class);
        
        // Save emergency contacts
        employeeService.updateEmergencyContacts(employee.getId(), input.getContacts());
        
        // Determine required training based on role
        List<TrainingModule> requiredTraining = trainingService.getRequiredTraining(
            employee.getRole(),
            employee.getDepartment(),
            employee.getLevel()
        );
        
        TrainingScheduleInput trainingInput = new TrainingScheduleInput();
        trainingInput.setModules(requiredTraining);
        trainingInput.setStartDate(context.getContextValue("startDate", LocalDate.class));
        trainingInput.setPreferredTimes(employee.getPreferredTrainingTimes());
        
        return StepEvent.of(trainingInput, TrainingScheduleInput.class);
    }
    
    @WorkflowStep(
        index = 4,
        inputClass = TrainingScheduleInput.class,
        description = "Schedule your training"
    )
    public StepEvent scheduleTraining(TrainingScheduleInput input, WorkflowContext context) {
        Employee employee = context.getContextValue("employee", Employee.class);
        
        // Create training schedule
        TrainingSchedule schedule = trainingService.createSchedule(
            employee.getId(),
            input.getSelectedModules(),
            input.getSchedulePreference()
        );
        
        context.setContextValue("trainingSchedule", schedule);
        
        // Prepare equipment request
        EquipmentRequestInput equipmentInput = new EquipmentRequestInput();
        equipmentInput.setRole(employee.getRole());
        equipmentInput.setDepartment(employee.getDepartment());
        equipmentInput.setStandardPackages(itService.getStandardPackages(employee.getRole()));
        
        return StepEvent.of(equipmentInput, EquipmentRequestInput.class);
    }
    
    @WorkflowStep(
        index = 5,
        inputClass = EquipmentRequestInput.class,
        description = "Select equipment and workspace"
    )
    public StepEvent setupWorkspace(EquipmentRequestInput input, WorkflowContext context) {
        Employee employee = context.getContextValue("employee", Employee.class);
        LocalDate startDate = context.getContextValue("startDate", LocalDate.class);
        
        // Create IT ticket for equipment
        ITTicket equipmentTicket = itService.createEquipmentRequest(
            employee,
            input.getSelectedPackage(),
            input.getAdditionalItems(),
            startDate
        );
        
        // Assign workspace
        WorkspaceAssignment workspace = facilitiesService.assignWorkspace(
            employee,
            input.getWorkspacePreference()
        );
        
        context.setContextValue("equipmentTicket", equipmentTicket);
        context.setContextValue("workspace", workspace);
        
        // Create onboarding summary
        OnboardingSummary summary = buildOnboardingSummary(context);
        
        return StepEvent.of(summary, OnboardingConfirmationInput.class);
    }
    
    @WorkflowStep(
        index = 6,
        inputClass = OnboardingConfirmationInput.class,
        description = "Review onboarding checklist",
        async = true
    )
    public AsyncTaskEvent finalizeOnboarding(OnboardingConfirmationInput input, WorkflowContext context) {
        if (!input.isConfirmed()) {
            return AsyncTaskEvent.builder()
                .taskName("onboardingIncomplete")
                .messageId("onboarding_incomplete")
                .build();
        }
        
        return AsyncTaskEvent.builder()
            .taskName("completeOnboarding")
            .taskArgs(Map.of("context", context.getAllValues()))
            .messageId("completing_onboarding")
            .build();
    }
    
    @AsyncStep(forStep = "completeOnboarding")
    public StepEvent completeOnboardingProcess(Map<String, Object> taskArgs, WorkflowContext context) {
        try {
            Employee employee = context.getContextValue("employee", Employee.class);
            
            // Send notifications to relevant parties
            notificationService.notifyManager(employee.getManagerId(), employee);
            notificationService.notifyIT(context.getContextValue("equipmentTicket", ITTicket.class));
            notificationService.notifyFacilities(context.getContextValue("workspace", WorkspaceAssignment.class));
            notificationService.notifyTraining(context.getContextValue("trainingSchedule", TrainingSchedule.class));
            
            // Create first day agenda
            FirstDayAgenda agenda = onboardingService.createFirstDayAgenda(employee, context);
            
            // Send welcome package
            emailService.sendWelcomePackage(employee, agenda);
            
            return StepEvent.builder()
                .completed(true)
                .percentComplete(100)
                .properties(Map.of(
                    "employeeId", employee.getId(),
                    "message", "Onboarding completed successfully! Welcome package sent to " + employee.getEmail(),
                    "firstDayAgenda", agenda
                ))
                .build();
                
        } catch (Exception e) {
            log.error("Failed to complete onboarding", e);
            return StepEvent.withError("Failed to complete onboarding: " + e.getMessage());
        }
    }
    
    private OnboardingSummary buildOnboardingSummary(WorkflowContext context) {
        OnboardingSummary summary = new OnboardingSummary();
        
        Employee employee = context.getContextValue("employee", Employee.class);
        summary.setEmployeeName(employee.getFullName());
        summary.setStartDate(context.getContextValue("startDate", LocalDate.class));
        summary.setDocumentsVerified(context.getContextValue("documentsVerified", Boolean.class));
        summary.setTrainingSchedule(context.getContextValue("trainingSchedule", TrainingSchedule.class));
        summary.setEquipmentTicket(context.getContextValue("equipmentTicket", ITTicket.class));
        summary.setWorkspace(context.getContextValue("workspace", WorkspaceAssignment.class));
        
        // Generate checklist
        summary.setChecklist(onboardingService.generateChecklist(employee));
        
        return summary;
    }
}
```

---

**Built with ❤️ by the DriftKit team**
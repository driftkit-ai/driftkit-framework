# LLMAgent Documentation

## Overview

LLMAgent is a simplified, type-safe SDK for AI interactions in the DriftKit framework. It provides a high-level interface over the ModelClient API with built-in features for tool calling, structured output, memory management, and more.

## Key Features

- **Unified Interface**: Simple `execute*()` methods for all operations
- **Type Safety**: `AgentResponse<T>` wrapper for typed results
- **Tool Calling**: Both manual and automatic execution modes
- **Structured Output**: JSON schema-based extraction with type safety
- **Multi-modal Support**: Text + images in single interface
- **Memory Management**: Conversation history with `ChatMemory`
- **Prompt Templates**: Integration with `PromptService`
- **Builder Pattern**: Easy configuration and setup
- **Request Tracing**: Built-in monitoring and debugging

## Basic Usage

### Creating an LLMAgent

```java
LLMAgent agent = LLMAgent.builder()
    .modelClient(modelClient)
    .systemMessage("You are a helpful assistant")
    .temperature(0.7)
    .maxTokens(1000)
    .chatMemory(chatMemory)
    .build();
```

### Simple Text Chat

```java
AgentResponse<String> response = agent.executeText("What is the capital of France?");
String answer = response.getText();
```

### Multi-modal Chat (Text + Images)

```java
byte[] imageData = Files.readAllBytes(Paths.get("image.jpg"));
AgentResponse<String> response = agent.executeWithImages("What's in this image?", imageData);
```

### Tool Calling

#### Manual Tool Execution

```java
// Get tool calls for manual execution
AgentResponse<List<ToolCall>> response = agent.executeForToolCalls("Get weather in Paris");

for (ToolCall toolCall : response.getToolCalls()) {
    ToolExecutionResult result = agent.executeToolCall(toolCall);
    WeatherInfo weather = result.getTypedResult(); // Type-safe result
    System.out.println("Temperature: " + weather.getTemperature());
}
```

#### Automatic Tool Execution

```java
// Register tools
agent.registerTool("getCurrentWeather", weatherService, "Get current weather")
     .registerTool("searchDatabase", dbService, "Search database");

// Execute with automatic tool calling
AgentResponse<List<ToolExecutionResult>> response = agent.executeWithTools(
    "What's the weather in cities where our customers are located?"
);

// Access typed results
for (ToolExecutionResult result : response.getToolResults()) {
    if (result.isSuccess()) {
        Object typedResult = result.getTypedResult();
    }
}
```

### Structured Output Extraction

```java
// Define your data model
public record Person(String name, Integer age, String occupation) {}

// Extract structured data
AgentResponse<Person> response = agent.executeStructured(
    "John is 30 years old and works as a software engineer",
    Person.class
);

Person person = response.getStructuredData();
```

### Using Prompt Templates

```java
// Use predefined prompt template
Map<String, Object> variables = Map.of(
    "topic", "AI Ethics",
    "length", "500 words"
);

AgentResponse<String> response = agent.executeWithPrompt("blog-post-template", variables);
```

## Advanced Patterns

### Loop Pattern - Iterative Refinement

The LoopAgent pattern enables iterative refinement where a worker agent generates content and an evaluator agent determines if it meets criteria.

```java
// Worker agent - generates content
Agent writer = LLMAgent.builder()
    .modelClient(modelClient)
    .systemMessage("Write engaging blog posts about technology")
    .build();

// Evaluator agent - checks quality
Agent editor = LLMAgent.builder()
    .modelClient(modelClient)
    .systemMessage("Evaluate blog posts. Return JSON: " +
                  "{\"status\": \"COMPLETE\"} if excellent, " +
                  "{\"status\": \"REVISE\", \"feedback\": \"specific improvements\"}")
    .build();

// Create loop agent
LoopAgent blogWriter = LoopAgent.builder()
    .worker(writer)
    .evaluator(editor)
    .stopCondition(LoopStatus.COMPLETE)
    .maxIterations(5)
    .build();

String finalPost = blogWriter.execute("Write about quantum computing");
```

#### Loop Status Options

- `COMPLETE` - Task completed successfully
- `REVISE` - Output needs revision based on feedback
- `RETRY` - Retry with same input
- `FAILED` - Task failed
- `CONTINUE` - Continue to next iteration

### Sequential Pattern - Multi-Stage Processing

The SequentialAgent chains multiple agents where each output becomes the next input.

```java
// Create a multi-stage pipeline
SequentialAgent pipeline = SequentialAgent.builder()
    .agent(LLMAgent.builder()
        .modelClient(modelClient)
        .systemMessage("Extract key facts from the text")
        .build())
    .agent(LLMAgent.builder()
        .modelClient(modelClient)
        .systemMessage("Organize facts into categories")
        .build())
    .agent(LLMAgent.builder()
        .modelClient(modelClient)
        .systemMessage("Create a summary report")
        .build())
    .build();

String report = pipeline.execute(longDocument);
```

### Combined Patterns

Combine Loop and Sequential for complex workflows:

```java
// Sequential processing pipeline
SequentialAgent processor = SequentialAgent.builder()
    .agent(dataExtractor)
    .agent(dataTransformer)
    .agent(reportGenerator)
    .build();

// Add quality control loop
LoopAgent qualityControlled = LoopAgent.builder()
    .worker(processor)
    .evaluator(qualityChecker)
    .stopCondition(LoopStatus.COMPLETE)
    .build();
```

### Agent as Tool

Agents can be used as tools by other agents:

```java
// Specialized agents
Agent researcher = LLMAgent.builder()
    .modelClient(modelClient)
    .systemMessage("Research topics thoroughly")
    .name("Researcher")
    .build();

Agent factChecker = LLMAgent.builder()
    .modelClient(modelClient)
    .systemMessage("Verify facts and claims")
    .name("FactChecker")
    .build();

// Convert to tools
ToolInfo researchTool = AgentAsTool.create("research", "Research topics", researcher);
ToolInfo factCheckTool = AgentAsTool.create("factCheck", "Verify facts", factChecker);

// Master agent with sub-agents as tools
Agent masterAgent = LLMAgent.builder()
    .modelClient(modelClient)
    .systemMessage("Write accurate, well-researched articles")
    .addTool(researchTool)
    .addTool(factCheckTool)
    .build();
```

## Real-World Use Cases

### 1. Customer Support System

```java
// Intent classifier
Agent classifier = LLMAgent.builder()
    .modelClient(modelClient)
    .systemMessage("Classify customer inquiries: billing, technical, general")
    .build();

// Response generators for each category
Agent billingAgent = LLMAgent.builder()
    .modelClient(modelClient)
    .systemMessage("Handle billing inquiries professionally")
    .build();

Agent technicalAgent = LLMAgent.builder()
    .modelClient(modelClient)
    .systemMessage("Provide technical support")
    .build();

// Router agent with specialized agents as tools
Agent supportRouter = LLMAgent.builder()
    .modelClient(modelClient)
    .systemMessage("Route and handle customer inquiries")
    .addTool(AgentAsTool.create("billing", "Handle billing", billingAgent))
    .addTool(AgentAsTool.create("technical", "Handle technical", technicalAgent))
    .build();
```

### 2. Document Processing Pipeline

```java
// OCR and extraction
Agent ocrAgent = LLMAgent.builder()
    .modelClient(modelClient)
    .systemMessage("Extract text from document images")
    .build();

// Data structuring
Agent structuringAgent = LLMAgent.builder()
    .modelClient(modelClient)
    .systemMessage("Convert extracted text to structured data")
    .build();

// Validation
Agent validator = LLMAgent.builder()
    .modelClient(modelClient)
    .systemMessage("Validate data completeness and accuracy")
    .build();

// Complete pipeline with validation loop
SequentialAgent extraction = SequentialAgent.builder()
    .agent(ocrAgent)
    .agent(structuringAgent)
    .build();

LoopAgent validatedPipeline = LoopAgent.builder()
    .worker(extraction)
    .evaluator(validator)
    .stopCondition(LoopStatus.COMPLETE)
    .build();
```

### 3. Code Generation and Review

```java
// Code generator
Agent codeGenerator = LLMAgent.builder()
    .modelClient(modelClient)
    .systemMessage("Generate clean, efficient code following best practices")
    .build();

// Code reviewer
Agent codeReviewer = LLMAgent.builder()
    .modelClient(modelClient)
    .systemMessage("Review code for bugs, security issues, and improvements. " +
                  "Return {\"status\": \"COMPLETE\"} if good, " +
                  "{\"status\": \"REVISE\", \"feedback\": \"issues found\"}")
    .build();

// Iterative code improvement
LoopAgent codeWriter = LoopAgent.builder()
    .worker(codeGenerator)
    .evaluator(codeReviewer)
    .stopCondition(LoopStatus.COMPLETE)
    .maxIterations(3)
    .build();

String code = codeWriter.execute("Create a REST API endpoint for user management");
```

### 4. Multi-Language Content Creation

```java
// Content creation pipeline
SequentialAgent contentPipeline = SequentialAgent.builder()
    .agent(LLMAgent.builder()
        .modelClient(modelClient)
        .systemMessage("Write content in English")
        .build())
    .agent(LLMAgent.builder()
        .modelClient(modelClient)
        .systemMessage("Translate to Spanish preserving tone")
        .build())
    .agent(LLMAgent.builder()
        .modelClient(modelClient)
        .systemMessage("Adapt for Latin American audience")
        .build())
    .build();

// Quality control
Agent qualityChecker = LLMAgent.builder()
    .modelClient(modelClient)
    .systemMessage("Check translation quality and cultural appropriateness")
    .build();

LoopAgent multilingualContent = LoopAgent.builder()
    .worker(contentPipeline)
    .evaluator(qualityChecker)
    .stopCondition(LoopStatus.COMPLETE)
    .build();
```

### 5. Data Analysis Workflow

```java
// Analysis pipeline
SequentialAgent analysisWorkflow = SequentialAgent.builder()
    .agent(LLMAgent.builder()
        .modelClient(modelClient)
        .systemMessage("Clean and preprocess data")
        .build())
    .agent(LLMAgent.builder()
        .modelClient(modelClient)
        .systemMessage("Perform statistical analysis")
        .build())
    .agent(LLMAgent.builder()
        .modelClient(modelClient)
        .systemMessage("Generate insights and recommendations")
        .build())
    .agent(LLMAgent.builder()
        .modelClient(modelClient)
        .systemMessage("Create executive summary")
        .build())
    .build();

String analysis = analysisWorkflow.execute(rawData);
```

## Best Practices

1. **Clear System Messages**: Provide specific, clear instructions in system messages
2. **Type Safety**: Use structured output for type-safe data extraction
3. **Error Handling**: Always handle potential failures in tool execution
4. **Memory Management**: Configure appropriate token limits for conversations
5. **Tool Design**: Create focused, single-purpose tools
6. **Evaluation Criteria**: Define clear, objective criteria for loop evaluators
7. **Iteration Limits**: Set reasonable max iterations to prevent infinite loops
8. **Prompt Engineering**: Use prompt templates for consistent formatting

## Integration with Spring Boot

```java
@Service
public class AIService {
    private final LLMAgent agent;
    
    public AIService(ModelClient modelClient, ChatMemory chatMemory) {
        this.agent = LLMAgent.builder()
            .modelClient(modelClient)
            .systemMessage("Professional AI assistant")
            .chatMemory(chatMemory)
            .temperature(0.7)
            .build();
    }
    
    @PostMapping("/chat")
    public String chat(@RequestBody String message) {
        return agent.executeText(message).getText();
    }
    
    @PostMapping("/analyze")
    public AnalysisResult analyze(@RequestBody String data) {
        return agent.executeStructured(data, AnalysisResult.class)
                   .getStructuredData();
    }
}
```

## Performance Considerations

1. **Caching**: LLMAgent supports response caching for repeated queries
2. **Parallel Execution**: Use multiple agents in parallel when possible
3. **Token Optimization**: Monitor token usage with memory management
4. **Tool Efficiency**: Design tools to minimize LLM calls
5. **Batch Processing**: Process multiple items in single requests when possible

## Debugging and Monitoring

Enable request tracing for debugging:

```java
LLMAgent agent = LLMAgent.builder()
    .modelClient(modelClient)
    .requestTracingProvider(tracingProvider)
    .build();
```

Access trace information:
- Request/response details
- Token usage
- Execution time
- Tool call history
- Error details

## Migration from Direct ModelClient

Before (ModelClient):
```java
ModelTextRequest request = ModelTextRequest.builder()
    .messages(List.of(createMessage(text)))
    .temperature(0.7)
    .build();
ModelTextResponse response = modelClient.textToText(request);
String result = response.getChoices().get(0).getMessage().getContent();
```

After (LLMAgent):
```java
String result = agent.executeText(text).getText();
```

The LLMAgent provides a cleaner, more maintainable API while preserving all the power of the underlying ModelClient.
# DriftKit Clients Module

## Overview

The `driftkit-clients` module provides a unified abstraction layer for integrating with various AI model providers. It follows a modular architecture with core abstractions, specific implementations, and Spring Boot auto-configuration support. The module includes comprehensive integrations for OpenAI, Google Gemini, and Anthropic Claude, with extensible architecture for additional providers.

## Spring Boot Initialization

To use the clients module in your Spring Boot application, the module will be automatically configured when you provide vault configuration:

```java
@SpringBootApplication
// No additional annotations needed - auto-configuration handles everything
public class YourApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourApplication.class, args);
    }
}
```

Configuration in `application.yml`:

```yaml
driftkit:
  vault:
    - name: "primary-openai"
      apiKey: "${OPENAI_API_KEY}"
      model: "gpt-4"
      temperature: 0.7
      maxTokens: 2000
    - name: "gemini"
      apiKey: "${GEMINI_API_KEY}"
      model: "gemini-2.5-flash"
      temperature: 0.7
      maxTokens: 2000
    - name: "claude"
      apiKey: "${CLAUDE_API_KEY}"
      model: "claude-sonnet-4-20250514"
      temperature: 0.7
      maxTokens: 2000
```

The module provides:
- **Auto-configuration class**: `ModelClientAutoConfiguration`
- **Automatic bean creation**: Creates `ModelClient` beans from vault configurations
- **Primary client**: First vault config becomes the primary model client
- **Factory pattern**: Uses `ModelClientFactory` internally
- **Conditional activation**: Only when `driftkit.vault[0].name` is configured

## Architecture

### Module Structure

```
driftkit-clients/
├── driftkit-clients-core/           # Core abstractions and factory
├── driftkit-clients-openai/         # OpenAI-specific implementation
├── driftkit-clients-gemini/         # Google Gemini implementation
├── driftkit-clients-claude/         # Anthropic Claude implementation  
├── driftkit-clients-spring-boot-starter/  # Spring Boot auto-configuration
└── pom.xml                          # Parent module configuration
```

### Key Dependencies

- **Feign** - Declarative HTTP client for API integration
- **OkHttp** - HTTP client implementation
- **Jackson** - JSON serialization with JSR310 support
- **DriftKit Common** - Core domain objects and utilities
- **Spring Boot** - Auto-configuration and dependency injection

## Core Module (driftkit-clients-core)

### ModelClientFactory

Factory pattern implementation for creating and managing ModelClient instances.

**Key Features:**
- **Service Loader Pattern** - Dynamic client discovery via `ServiceLoader.load(ModelClient.class)`
- **Caching** - Reuses client instances using `ConcurrentHashMap`
- **Tracing Support** - Optional execution monitoring with `TraceableModelClient`
- **Configuration-based** - Initializes clients from `VaultConfig`

### TraceableModelClient

Decorator pattern implementation for adding tracing capabilities.

**Key Features:**
- **Execution Timing** - Measures request/response latency
- **Token Counting** - Estimates input/output token usage
- **Error Handling** - Captures exceptions and creates error traces
- **Transparent Wrapping** - Preserves original client behavior via delegation

## Core Abstractions

### ModelClient

Abstract base class for all AI model integrations. Provides configuration properties and capability methods.

**Supported Capabilities:**
- `TEXT_TO_TEXT` - Text completion and chat
- `TEXT_TO_IMAGE` - Image generation from text
- `IMAGE_TO_TEXT` - Image analysis and description
- `FUNCTION_CALLING` - Tool use and function execution
- `JSON_OBJECT` - JSON-formatted responses
- `JSON_SCHEMA` - Structured output with schema validation
- `TOOLS` - Tool/function calling capabilities

### ModelClientInit Interface

Interface for configurable model clients. Must be implemented by any client that supports configuration-based initialization.

## OpenAI Module (driftkit-clients-openai)

### OpenAIModelClient

Concrete implementation of ModelClient for OpenAI services.

**Model Constants:**
- `GPT_DEFAULT = "gpt-4o"`
- `GPT_SMART_DEFAULT = "o3-mini"`
- `GPT_MINI_DEFAULT = "gpt-4o-mini"`
- `IMAGE_MODEL = "gpt-image-1"`

**Key Features:**
- **Multiple Model Support** - GPT-4O, O3-Mini, GPT-4O-Mini
- **Multi-modal Processing** - Text, image, and audio capabilities
- **Advanced Parameters** - Support for O3/O4 reasoning effort parameter
- **JSON Mode** - Structured output with schema validation
- **Function Calling** - Tool use with comprehensive parameter mapping
- **Log Probabilities** - Token-level probability analysis

### OpenAI API Client

Feign-based HTTP client for OpenAI API integration supporting chat completions, image generation, embeddings, and audio transcription.

### Domain Objects

- **ChatCompletionRequest** - Comprehensive request model with messages, tools, and response format
- **ChatCompletionResponse** - Response model with usage statistics and log probabilities
- **CreateImageRequest/Response** - Image generation models
- **EmbeddingsRequest/Response** - Text embedding models

### OpenAI Utilities

**OpenAIUtils** provides image processing utilities for Base64 conversion and MIME type detection.

## Gemini Module (driftkit-clients-gemini)

### GeminiModelClient

Concrete implementation of ModelClient for Google Gemini services.

**Model Constants:**
- `GEMINI_DEFAULT = "gemini-2.5-flash"`
- `GEMINI_SMART_DEFAULT = "gemini-2.5-pro"`
- `GEMINI_MINI_DEFAULT = "gemini-2.5-flash-lite"`
- `GEMINI_IMAGE_DEFAULT = "gemini-2.0-flash-preview-image-generation"`

**Key Features:**
- **Latest Gemini 2.5 Models** - Support for Pro, Flash, and Flash-Lite variants
- **Native Multi-modal Processing** - Seamless text and image handling
- **Thinking/Reasoning Support** - Advanced reasoning with configurable thinking budgets
- **System Instructions** - Native support for system-level prompts
- **Structured Output** - JSON schema validation and response formatting
- **Function Calling** - Comprehensive tool use with automatic parameter mapping
- **Safety Settings** - Configurable content filtering and safety thresholds
- **Experimental Models** - Support for TTS and native audio models

### Gemini API Client

Feign-based HTTP client for Gemini API integration supporting content generation, token counting, and streaming.

### Domain Objects

- **GeminiChatRequest** - Request model with contents, tools, and generation config
- **GeminiChatResponse** - Response model with candidates, usage metadata, and safety ratings
- **GeminiContent** - Multi-modal content representation
- **GeminiTool** - Function declarations for tool use
- **GeminiGenerationConfig** - Advanced generation parameters including thinking config
- **GeminiSafetySettings** - Content safety configuration

### Gemini-Specific Features

**Thinking Configuration:**
```java
// Enable advanced reasoning with Gemini 2.5
ModelTextRequest request = ModelTextRequest.builder()
    .model("gemini-2.5-pro")
    .reasoningEffort(ReasoningEffort.high) // Enables thinking mode
    .messages(messages)
    .build();
```

**Safety Configuration:**
```java
List<GeminiSafetySettings> safetySettings = List.of(
    GeminiSafetySettings.builder()
        .category(HarmCategory.HARM_CATEGORY_DANGEROUS_CONTENT)
        .threshold(HarmBlockThreshold.BLOCK_ONLY_HIGH)
        .build()
);
```

**Multi-modal Content:**
```java
// Process images with Gemini
ModelTextRequest request = ModelTextRequest.builder()
    .messages(List.of(
        ModelContentMessage.create(Role.user, 
            "Analyze this image", 
            imageData)
    ))
    .model("gemini-2.5-flash")
    .build();
```

### Supported Models

The framework supports the following AI providers and models:

**OpenAI:**
- GPT-4, GPT-4o, GPT-4o-mini, O3-Mini
- DALL-E for image generation
- Text embeddings models

**Google Gemini:**
- Gemini 2.5 Pro, Flash, Flash-Lite
- Image generation (experimental)
- Native multi-modal support

**Anthropic Claude:**
- Claude Opus 4, Sonnet 4, Haiku 3.5
- Vision capabilities across all models
- 200K token context windows

All clients provide a unified interface through the `ModelClient` abstraction, allowing easy switching between providers via configuration.

## Spring Boot Starter Module

The Spring Boot starter module provides auto-configuration for model clients.

## Demo Examples

### 1. Conversational AI Assistant

This example demonstrates building a multi-turn conversational assistant with memory.

```java
@Service
public class ConversationalAssistant {
    
    private final ModelClient client;
    private final Map<String, List<Message>> conversations = new ConcurrentHashMap<>();
    
    public ConversationalAssistant(VaultConfig config) {
        this.client = ModelClientFactory.fromConfig(config);
    }
    
    public String chat(String userId, String message) {
        // Get or create conversation history
        List<Message> history = conversations.computeIfAbsent(userId, 
            k -> new ArrayList<>());
        
        // Add user message
        history.add(createMessage(Role.user, message));
        
        // Keep only last 10 messages for context
        if (history.size() > 10) {
            history = history.subList(history.size() - 10, history.size());
        }
        
        // Create request with full conversation
        ModelTextRequest request = ModelTextRequest.builder()
            .messages(history)
            .temperature(0.7)
            .maxTokens(500)
            .build();
        
        // Get response
        ModelTextResponse response = client.textToText(request);
        String assistantMessage = response.getChoices().get(0)
            .getMessage().getContent();
        
        // Add assistant response to history
        history.add(createMessage(Role.assistant, assistantMessage));
        
        return assistantMessage;
    }
    
    private Message createMessage(Role role, String content) {
        return ModelContentMessage.builder()
            .role(role)
            .content(List.of(
                ModelContentElement.builder()
                    .type(MessageType.text)
                    .text(content)
                    .build()
            ))
            .build();
    }
}
```

### 2. Document Analysis with Vision

This example shows how to analyze documents and images using multi-modal capabilities.

```java
@Service
public class DocumentAnalyzer {
    
    private final ModelClient client;
    
    public DocumentAnalyzer(VaultConfig config) {
        this.client = ModelClientFactory.fromConfig(config);
    }
    
    public AnalysisResult analyzeDocument(String imagePath, AnalysisType type) {
        try {
            // Convert image to base64
            String dataUrl = OpenAIUtils.createDataUrl(imagePath);
            
            // Create appropriate prompt based on analysis type
            String prompt = switch (type) {
                case INVOICE -> "Extract all invoice details including items, "
                    + "amounts, dates, and vendor information.";
                case RECEIPT -> "Extract transaction details, items purchased, "
                    + "total amount, and merchant information.";
                case CONTRACT -> "Identify key contract terms, parties involved, "
                    + "obligations, and important dates.";
                case GENERAL -> "Describe this document and extract any "
                    + "important information.";
            };
            
            ModelTextRequest request = ModelTextRequest.builder()
                .messages(List.of(
                    ModelContentMessage.builder()
                        .role(Role.user)
                        .content(List.of(
                            ModelContentElement.builder()
                                .type(MessageType.text)
                                .text(prompt)
                                .build(),
                            ModelContentElement.builder()
                                .type(MessageType.image)
                                .imageUrl(dataUrl)
                                .build()
                        ))
                        .build()
                ))
                .temperature(0.2)
                .maxTokens(1000)
                .build();
            
            ModelTextResponse response = client.imageToText(request);
            
            return AnalysisResult.builder()
                .type(type)
                .content(response.getChoices().get(0).getMessage().getContent())
                .confidence(0.95)
                .build();
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to analyze document", e);
        }
    }
    
    public enum AnalysisType {
        INVOICE, RECEIPT, CONTRACT, GENERAL
    }
    
    @Data
    @Builder
    public static class AnalysisResult {
        private AnalysisType type;
        private String content;
        private double confidence;
    }
}
```

### 3. AI-Powered Code Assistant

This example demonstrates function calling for code generation and analysis.

```java
@Service
public class CodeAssistant {
    
    private final ModelClient client;
    
    public CodeAssistant(VaultConfig config) {
        this.client = ModelClientFactory.fromConfig(config);
        
        // Define code-related tools
        List<Tool> tools = List.of(
            createTool("analyze_code", "Analyze code for bugs and improvements",
                Map.of(
                    "code", property("string", "Code to analyze"),
                    "language", property("string", "Programming language")
                ),
                List.of("code")
            ),
            createTool("generate_tests", "Generate unit tests for code",
                Map.of(
                    "code", property("string", "Code to test"),
                    "framework", property("string", "Test framework",
                        List.of("junit", "pytest", "jest"))
                ),
                List.of("code", "framework")
            ),
            createTool("refactor_code", "Refactor code for better quality",
                Map.of(
                    "code", property("string", "Code to refactor"),
                    "goal", property("string", "Refactoring goal")
                ),
                List.of("code")
            )
        );
        
        client.setTools(tools);
    }
    
    public CodeResponse processRequest(String userRequest) {
        ModelTextRequest request = ModelTextRequest.builder()
            .messages(List.of(
                createSystemMessage("You are an expert code assistant. "
                    + "Use the provided tools to help with code tasks."),
                createUserMessage(userRequest)
            ))
            .toolMode(ToolMode.auto)
            .temperature(0.3)
            .build();
        
        ModelTextResponse response = client.textToText(request);
        Choice choice = response.getChoices().get(0);
        
        // Check if tool was called
        if (choice.getMessage().getToolCalls() != null) {
            List<ToolCall> toolCalls = choice.getMessage().getToolCalls();
            return processToolCalls(toolCalls, userRequest);
        }
        
        return CodeResponse.builder()
            .type(ResponseType.DIRECT)
            .content(choice.getMessage().getContent())
            .build();
    }
    
    private CodeResponse processToolCalls(List<ToolCall> toolCalls, 
                                        String originalRequest) {
        // Process tool calls and generate response
        StringBuilder result = new StringBuilder();
        ResponseType type = ResponseType.DIRECT;
        
        for (ToolCall call : toolCalls) {
            String function = call.getFunction().getName();
            Map<String, Object> args = call.getFunction().getArguments();
            
            switch (function) {
                case "analyze_code" -> {
                    result.append("Code Analysis:\n");
                    result.append(analyzeCode(
                        (String) args.get("code"),
                        (String) args.get("language")
                    ));
                    type = ResponseType.ANALYSIS;
                }
                case "generate_tests" -> {
                    result.append("Generated Tests:\n");
                    result.append(generateTests(
                        (String) args.get("code"),
                        (String) args.get("framework")
                    ));
                    type = ResponseType.TESTS;
                }
                case "refactor_code" -> {
                    result.append("Refactored Code:\n");
                    result.append(refactorCode(
                        (String) args.get("code"),
                        (String) args.get("goal")
                    ));
                    type = ResponseType.REFACTORED;
                }
            }
        }
        
        return CodeResponse.builder()
            .type(type)
            .content(result.toString())
            .toolsUsed(toolCalls.stream()
                .map(tc -> tc.getFunction().getName())
                .collect(Collectors.toList()))
            .build();
    }
    
    private Tool createTool(String name, String description, 
                           Map<String, Property> properties,
                           List<String> required) {
        return new Tool(
            ResponseFormatType.function,
            new ToolFunction(
                name,
                description,
                new FunctionParameters(
                    ResponseFormatType.object,
                    properties,
                    required
                )
            )
        );
    }
    
    private Property property(String type, String description) {
        return property(type, description, null);
    }
    
    private Property property(String type, String description, List<String> enumValues) {
        return new Property(
            ResponseFormatType.valueOf(type),
            description,
            enumValues
        );
    }
    
    @Data
    @Builder
    public static class CodeResponse {
        private ResponseType type;
        private String content;
        private List<String> toolsUsed;
    }
    
    public enum ResponseType {
        DIRECT, ANALYSIS, TESTS, REFACTORED
    }
}
```

### 4. Creative Content Generator

This example shows image generation and creative text capabilities.

```java
@Service
public class CreativeContentGenerator {
    
    private final ModelClient client;
    
    public CreativeContentGenerator(VaultConfig config) {
        this.client = ModelClientFactory.fromConfig(config);
    }
    
    public CreativePackage generateCreativePackage(String theme, String style) {
        CreativePackage package = new CreativePackage();
        
        // Generate story
        package.setStory(generateStory(theme, style));
        
        // Generate poem
        package.setPoem(generatePoem(theme, style));
        
        // Generate image
        package.setImage(generateImage(theme, style));
        
        // Generate marketing copy
        package.setMarketingCopy(generateMarketingCopy(theme, style));
        
        return package;
    }
    
    private String generateStory(String theme, String style) {
        ModelTextRequest request = ModelTextRequest.builder()
            .messages(List.of(
                createMessage(Role.system, 
                    "You are a creative writer. Generate engaging stories."),
                createMessage(Role.user,
                    String.format("Write a short %s story about %s. "
                        + "Make it engaging and memorable.", style, theme))
            ))
            .temperature(0.9)
            .maxTokens(500)
            .build();
        
        return client.textToText(request).getChoices().get(0)
            .getMessage().getContent();
    }
    
    private String generatePoem(String theme, String style) {
        ModelTextRequest request = ModelTextRequest.builder()
            .messages(List.of(
                createMessage(Role.user,
                    String.format("Write a %s poem about %s.", style, theme))
            ))
            .temperature(0.8)
            .maxTokens(200)
            .build();
        
        return client.textToText(request).getChoices().get(0)
            .getMessage().getContent();
    }
    
    private byte[] generateImage(String theme, String style) {
        String prompt = String.format(
            "A %s artistic representation of %s, "
            + "high quality, detailed, professional",
            style, theme
        );
        
        ModelImageRequest request = ModelImageRequest.builder()
            .prompt(prompt)
            .quality(Quality.hd)
            .size("1024x1024")
            .n(1)
            .build();
        
        ModelImageResponse response = client.textToImage(request);
        return response.getBytes().get(0).getImage();
    }
    
    private String generateMarketingCopy(String theme, String style) {
        ModelTextRequest request = ModelTextRequest.builder()
            .messages(List.of(
                createMessage(Role.system,
                    "You are an expert marketing copywriter."),
                createMessage(Role.user,
                    String.format("Create compelling marketing copy for a %s "
                        + "product/service related to %s. Include a headline, "
                        + "tagline, and brief description.", style, theme))
            ))
            .temperature(0.7)
            .maxTokens(300)
            .build();
        
        return client.textToText(request).getChoices().get(0)
            .getMessage().getContent();
    }
    
    @Data
    public static class CreativePackage {
        private String story;
        private String poem;
        private byte[] image;
        private String marketingCopy;
    }
}
```

### 5. Structured Data Extractor

This example demonstrates JSON schema validation for structured output.

```java
@Service
public class StructuredDataExtractor {
    
    private final ModelClient client;
    
    public StructuredDataExtractor(VaultConfig config) {
        this.client = ModelClientFactory.fromConfig(config);
    }
    
    public <T> T extractStructuredData(String text, Class<T> targetClass, 
                                      JsonSchema schema) {
        // Configure response format
        ResponseFormat responseFormat = ResponseFormat.builder()
            .type(ResponseFormat.Type.json_schema)
            .jsonSchema(schema)
            .build();
        
        ModelTextRequest request = ModelTextRequest.builder()
            .messages(List.of(
                createMessage(Role.system,
                    "Extract structured data from text according to the schema."),
                createMessage(Role.user, text)
            ))
            .responseFormat(responseFormat)
            .temperature(0.1)
            .maxTokens(1000)
            .build();
        
        ModelTextResponse response = client.textToText(request);
        String jsonResponse = response.getChoices().get(0)
            .getMessage().getContent();
        
        try {
            return new ObjectMapper().readValue(jsonResponse, targetClass);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse structured response", e);
        }
    }
    
    // Example: Extract company information
    public CompanyInfo extractCompanyInfo(String text) {
        JsonSchema schema = JsonSchema.builder()
            .name("CompanyInfo")
            .strict(true)
            .schema(Map.of(
                "type", "object",
                "properties", Map.of(
                    "name", Map.of(
                        "type", "string",
                        "description", "Company name"
                    ),
                    "founded", Map.of(
                        "type", "integer",
                        "description", "Year founded"
                    ),
                    "employees", Map.of(
                        "type", "integer",
                        "description", "Number of employees"
                    ),
                    "industry", Map.of(
                        "type", "string",
                        "description", "Primary industry"
                    ),
                    "products", Map.of(
                        "type", "array",
                        "items", Map.of(
                            "type", "string"
                        ),
                        "description", "Main products or services"
                    )
                ),
                "required", List.of("name", "industry")
            ))
            .build();
        
        return extractStructuredData(text, CompanyInfo.class, schema);
    }
    
    // Example: Extract meeting notes
    public MeetingNotes extractMeetingNotes(String transcript) {
        JsonSchema schema = JsonSchema.builder()
            .name("MeetingNotes")
            .strict(true)
            .schema(Map.of(
                "type", "object",
                "properties", Map.of(
                    "date", Map.of("type", "string"),
                    "participants", Map.of(
                        "type", "array",
                        "items", Map.of("type", "string")
                    ),
                    "summary", Map.of("type", "string"),
                    "decisions", Map.of(
                        "type", "array",
                        "items", Map.of("type", "string")
                    ),
                    "actionItems", Map.of(
                        "type", "array",
                        "items", Map.of(
                            "type", "object",
                            "properties", Map.of(
                                "task", Map.of("type", "string"),
                                "assignee", Map.of("type", "string"),
                                "dueDate", Map.of("type", "string")
                            ),
                            "required", List.of("task", "assignee")
                        )
                    )
                ),
                "required", List.of("summary", "decisions", "actionItems")
            ))
            .build();
        
        return extractStructuredData(transcript, MeetingNotes.class, schema);
    }
    
    @Data
    public static class CompanyInfo {
        private String name;
        private Integer founded;
        private Integer employees;
        private String industry;
        private List<String> products;
    }
    
    @Data
    public static class MeetingNotes {
        private String date;
        private List<String> participants;
        private String summary;
        private List<String> decisions;
        private List<ActionItem> actionItems;
        
        @Data
        public static class ActionItem {
            private String task;
            private String assignee;
            private String dueDate;
        }
    }
}
```

## Error Handling

### Exception Types

- **UnsupportedCapabilityException** - Thrown when requesting unsupported model capabilities
- **API Exceptions** - HTTP and connection errors from the underlying API

### Retry Strategies

Implement exponential backoff for transient failures:

```java
@Service
public class ResilientModelClient {
    
    private final ModelClient client;
    private final int maxRetries = 3;
    private final long baseDelay = 1000;
    
    public ModelTextResponse executeWithRetry(ModelTextRequest request) {
        Exception lastException = null;
        
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                return client.textToText(request);
            } catch (Exception e) {
                lastException = e;
                if (attempt < maxRetries - 1) {
                    long delay = baseDelay * (1L << attempt);
                    Thread.sleep(delay);
                }
            }
        }
        
        throw new RuntimeException("All retry attempts failed", lastException);
    }
}
```

## Performance Considerations

### Connection Pooling

The OpenAI client uses OkHttp with connection pooling for efficient HTTP connections.

### Token Optimization

```java
@Service
public class TokenOptimizer {
    
    private final Tokenizer tokenizer;
    
    public String optimizePrompt(String prompt, int maxTokens) {
        int tokenCount = tokenizer.estimateTokenCount(prompt);
        
        if (tokenCount <= maxTokens) {
            return prompt;
        }
        
        // Truncate intelligently
        return truncateToTokenLimit(prompt, maxTokens);
    }
    
    private String truncateToTokenLimit(String text, int maxTokens) {
        // Implementation to truncate at sentence boundaries
        return text;
    }
}
```

## Extension Points

### Adding New Model Providers

1. Create a new submodule (e.g., `driftkit-clients-anthropic`)
2. Implement the `ModelClient` abstract class
3. Implement the `ModelClientInit` interface
4. Add `META-INF/services/ai.driftkit.common.domain.client.ModelClient` file
5. Register the client in the service loader

This comprehensive documentation covers all aspects of the driftkit-clients module, providing practical examples for real-world AI integration scenarios.
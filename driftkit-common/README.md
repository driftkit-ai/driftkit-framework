# DriftKit Common Module

## Overview

The `driftkit-common` module serves as the foundational layer for the DriftKit AI ETL framework, providing shared domain objects, utilities, and core services that other modules depend on. This module contains the essential building blocks for AI-powered applications including chat management, document processing, text analysis, and model integration.

## Spring Boot Initialization

The common module doesn't require special Spring Boot configuration as it provides only domain objects and utilities. Simply include it as a dependency:

```java
@SpringBootApplication
public class YourApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourApplication.class, args);
    }
}
```

The module provides:
- **Domain objects**: No Spring annotations, pure POJOs
- **Services**: Stateless utility services that can be instantiated directly
- **Configuration**: `EtlConfig` can be used with `@ConfigurationProperties`

## Architecture

### Module Structure

```
driftkit-common/
├── src/main/java/ai/driftkit/
│   ├── common/
│   │   ├── domain/           # Core domain objects
│   │   ├── service/          # Core services
│   │   └── utils/           # Utility classes
│   └── config/              # Configuration classes
```

### Key Dependencies

- **Lombok** - Code generation and boilerplate reduction
- **Jackson** - JSON serialization with JSR310 support
- **Apache Commons Lang3** - String and general utilities
- **Apache Commons Collections4** - Enhanced collection operations
- **SLF4J** - Logging facade
- **Jakarta Validation** - Bean validation annotations

## Core Domain Objects

### Chat Management

#### Chat
Represents a chat session with comprehensive metadata:

```java
@Data
@Builder
public class Chat {
    private String id;
    private String name;
    private String systemMessage;
    private Language language;
    private Integer memoryLength;
    private ModelRole modelRole;
    private boolean hidden;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

**Key Features:**
- Unique identification and naming
- System message configuration for AI behavior
- Language specification (GENERAL, SPANISH, ENGLISH)
- Memory length control for conversation context
- Model role assignment (MAIN, ABTEST, CHECKER, NONE)
- Visibility controls with hidden flag
- Automatic timestamp tracking

**Usage Example:**
```java
Chat chat = Chat.builder()
    .id("chat-123")
    .name("Customer Support Session")
    .systemMessage("You are a helpful customer support agent")
    .language(Language.ENGLISH)
    .memoryLength(50)
    .modelRole(ModelRole.MAIN)
    .hidden(false)
    .createdAt(LocalDateTime.now())
    .build();
```

#### Message
Comprehensive message representation supporting multi-modal content:

```java
@Data
@Builder
public class Message implements ChatItem {
    private String id;
    private String chatId;
    private String parentId;
    private String content;
    private ChatMessageType messageType;
    private MessageType contentType;
    private Grade grade;
    private Map<String, Object> workflowContext;
    private LogProbs logProbs;
    private LocalDateTime requestInitTime;
    private LocalDateTime responseTime;
    private Map<String, Object> variables;
    private List<String> imageUrls;
    private String audioUrl;
    private String videoUrl;
    private String fileUrl;
}
```

**Key Features:**
- Multi-modal support (TEXT, IMAGE, AUDIO, VIDEO, FILE)
- Hierarchical message structure with parent-child relationships
- Grading system for quality assessment
- Workflow context integration
- Token log probabilities for advanced analysis
- Performance timing tracking
- Variable and media URL storage

**Usage Example:**
```java
Message message = Message.builder()
    .id("msg-456")
    .chatId("chat-123")
    .content("Hello, how can I help you today?")
    .messageType(ChatMessageType.AI)
    .contentType(MessageType.TEXT)
    .grade(Grade.EXCELLENT)
    .requestInitTime(LocalDateTime.now())
    .build();
```

#### AITask
Comprehensive task representation for AI operations:

```java
@Data
@Builder
public class AITask {
    private String id;
    private String chatId;
    private String workflowId;
    private String prompt;
    private Map<String, Object> variables;
    private List<String> imageUrls;
    private String audioUrl;
    private String videoUrl;
    private String fileUrl;
    private Map<String, Object> workflowContext;
    private Grade grade;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private String errorMessage;
    private Map<String, Object> metadata;
}
```

**Key Features:**
- Multi-modal input support
- Workflow integration with context preservation
- Variable substitution support
- Performance and error tracking
- Metadata extensibility
- Quality grading system

### Model Client Abstraction

#### ModelClient
Abstract base class for AI model integrations. The ModelClient provides unified access to different AI capabilities including text generation, image generation, and function calling.

**Supported Capabilities:**
- `TEXT_TO_TEXT` - Text completion and chat
- `TEXT_TO_IMAGE` - Image generation from text
- `IMAGE_TO_TEXT` - Image analysis and description
- `FUNCTION_CALLING` - Tool use and function execution

**Configuration Parameters:**
- `temperature` - Randomness control (0.0-2.0)
- `top_p` - Nucleus sampling threshold
- `max_tokens` - Maximum response length
- `frequency_penalty` - Repetition penalty
- `presence_penalty` - Topic diversity control
- `stop_sequences` - Generation stopping conditions

**Usage Example:**
```java
ModelTextRequest request = ModelTextRequest.builder()
    .prompt("Explain quantum computing in simple terms")
    .temperature(0.7)
    .maxTokens(500)
    .build();

ModelTextResponse response = modelClient.generateText(request);
System.out.println(response.getContent());
```

## Core Services

### Chat Memory Management

#### ChatMemory
Interface for managing conversation history with methods to add, retrieve, clear, and filter messages by ID or type.

#### TokenWindowChatMemory
Advanced memory management with token-based capacity control:

```java
@Builder
public class TokenWindowChatMemory implements ChatMemory {
    private final int maxTokens;
    private final Tokenizer tokenizer;
    private final ChatMemoryStore store;
    
    @Override
    public void add(ChatItem message) {
        store.add(message);
        ensureTokenLimit();
    }
    
    private void ensureTokenLimit() {
        // Evict older messages while preserving system messages
        // and maintaining conversation context
    }
}
```

**Key Features:**
- Token-based capacity management
- Automatic message eviction
- System message preservation
- Context-aware pruning
- Multiple tokenizer support

**Configuration Example:**
```java
ChatMemory memory = TokenWindowChatMemory.builder()
    .maxTokens(4000)
    .tokenizer(new SimpleTokenizer())
    .store(new InMemoryChatMemoryStore())
    .build();

// Add messages - automatic pruning when limit exceeded
memory.add(systemMessage);
memory.add(userMessage);
memory.add(aiResponse);
```

#### InMemoryChatMemoryStore
Simple in-memory implementation for development and testing:

```java
public class InMemoryChatMemoryStore implements ChatMemoryStore {
    private final Map<String, List<ChatItem>> conversations = new HashMap<>();
    
    @Override
    public void add(String chatId, ChatItem message) {
        conversations.computeIfAbsent(chatId, k -> new ArrayList<>()).add(message);
    }
    
    @Override
    public List<ChatItem> getMessages(String chatId) {
        return conversations.getOrDefault(chatId, new ArrayList<>());
    }
}
```

## Utility Classes

### Document Processing

#### DocumentSplitter
Intelligent document chunking for RAG applications with configurable chunk sizes and overlap. The splitter uses a sentence-first splitting strategy to maintain context boundaries.

**Key Features:**
- Sentence-first splitting strategy
- Configurable overlap between chunks
- Token count validation
- Oversized content handling
- Context preservation

**Usage Example:**
```java
DocumentSplitter splitter = DocumentSplitter.builder()
    .maxChunkSize(512)
    .overlapSize(50)
    .tokenizer(new SimpleTokenizer())
    .build();

List<String> chunks = splitter.split(longDocument);
// Each chunk ≤ 512 tokens with 50-token overlap
```

### Text Analysis

#### TextSimilarityUtil
Comprehensive text similarity calculations with multiple algorithms including Levenshtein distance, Jaccard similarity, Cosine similarity, and a weighted combined similarity metric.

**Supported Algorithms:**
- **Levenshtein Distance** - Character-level edit distance
- **Jaccard Similarity** - Set-based similarity using word overlap
- **Cosine Similarity** - Vector-based similarity with TF-IDF
- **Combined Similarity** - Weighted combination of multiple metrics

**Usage Example:**
```java
String text1 = "The quick brown fox jumps over the lazy dog";
String text2 = "A quick brown fox leaps over a lazy dog";

double similarity = TextSimilarityUtil.combinedSimilarity(text1, text2);
// Returns weighted score: 0.4 * levenshtein + 0.3 * jaccard + 0.3 * cosine
```

#### VariableExtractor
Template variable extraction with advanced features for parsing templates. Supports simple variables, conditional blocks, list iterations, and nested properties with dot notation.

**Supported Features:**
- Simple variable extraction: `{{variable}}`
- Conditional blocks: `{{#if condition}}...{{/if}}`
- List iterations: `{{#each items}}...{{/each}}`
- Nested properties: `{{user.profile.name}}`
- Escape sequences: `{{{{literal}}}}`

**Usage Example:**
```java
String template = """
    Hello {{user.name}}!
    {{#if user.isPremium}}
        Welcome to our premium service.
    {{/if}}
    Your recent orders:
    {{#each orders}}
        - {{this.product}} ({{this.price}})
    {{/each}}
    """;

Set<String> variables = VariableExtractor.extractVariables(template);
// Returns: ["user.name", "user.isPremium", "orders", "this.product", "this.price"]
```

### JSON Processing

#### JsonUtils
Robust JSON parsing and repair utilities with support for malformed JSON, relaxed parsing (comments, trailing commas, single quotes), JSON extraction from mixed text, and safe type conversion.

**Key Features:**
- Automatic JSON repair for malformed input
- Relaxed parsing with comments and trailing commas
- JSON extraction from mixed text content
- Safe type conversion with error handling
- Support for single quotes and unquoted keys

**Usage Example:**
```java
String malformedJson = """
    {
        name: 'John Doe',  // User's full name
        age: 30,
        "active": true,    // Trailing comma
    }
    """;

Optional<JsonNode> parsed = JsonUtils.parseJsonRelaxed(malformedJson);
if (parsed.isPresent()) {
    String name = parsed.get().get("name").asText();
    int age = parsed.get().get("age").asInt();
}
```

### Tokenization

#### SimpleTokenizer
Basic tokenization for estimation:

```java
public class SimpleTokenizer implements Tokenizer {
    private final double tokenCostMultiplier;
    
    public SimpleTokenizer() {
        this(0.7); // Default multiplier
    }
    
    @Override
    public int estimateTokenCount(String text) {
        return (int) (text.length() * tokenCostMultiplier);
    }
    
    @Override
    public int estimateTokenCount(List<ChatItem> messages) {
        return messages.stream()
            .mapToInt(msg -> estimateTokenCount(msg.getContent()))
            .sum();
    }
}
```

## Configuration

### EtlConfig
Central configuration for the entire framework:

```java
@Data
@Builder
public class EtlConfig {
    private List<VectorStoreConfig> vectorStores;
    private List<EmbeddingServiceConfig> embeddingServices;
    private List<PromptServiceConfig> promptServices;
    private List<VaultConfig> vault;
    private YoutubeProxyConfig youtubeProxy;
    
    // Nested configuration classes
    @Data
    public static class VectorStoreConfig {
        private String name;
        private String type; // "inmemory", "filebased", "pinecone"
        private String url;
        private String apiKey;
        private String environment;
        private String index;
        private Integer dimension;
        private String metric;
        private String filePath;
    }
    
    @Data
    public static class EmbeddingServiceConfig {
        private String name;
        private String type; // "openai", "cohere", "local"
        private String apiKey;
        private String model;
        private String url;
        private String modelPath;
        private Integer maxTokens;
        private Double temperature;
    }
    
    @Data
    public static class VaultConfig {
        private String name;
        private String type; // "openai", "anthropic", "google"
        private String apiKey;
        private String model;
        private String baseUrl;
        private Double temperature;
        private Integer maxTokens;
        private Double topP;
        private Double frequencyPenalty;
        private Double presencePenalty;
        private List<String> stopSequences;
    }
}
```

**Configuration Example (application.yml):**
```yaml
driftkit:
  vault:
    - name: "primary-openai"
      type: "openai"
      apiKey: "${OPENAI_API_KEY}"
      model: "gpt-4"
      temperature: 0.7
      maxTokens: 2000
      
  vectorStores:
    - name: "main-vector-store"
      type: "pinecone"
      apiKey: "${PINECONE_API_KEY}"
      environment: "us-west1-gcp"
      index: "driftkit-vectors"
      dimension: 1536
      metric: "cosine"
      
  embeddingServices:
    - name: "primary-embedding"
      type: "openai"
      apiKey: "${OPENAI_API_KEY}"
      model: "text-embedding-ada-002"
      
  promptServices:
    - name: "file-prompts"
      type: "filesystem"
      basePath: "./prompts"
      
  youtubeProxy:
    proxyUrl: "http://proxy.example.com:8080"
    username: "proxyuser"
    password: "${PROXY_PASSWORD}"
```

## Usage Patterns

### Basic Chat Implementation

```java
@Service
public class ChatService {
    private final ChatMemory memory;
    private final ModelClient modelClient;
    
    public ChatService() {
        this.memory = TokenWindowChatMemory.builder()
            .maxTokens(4000)
            .tokenizer(new SimpleTokenizer())
            .store(new InMemoryChatMemoryStore())
            .build();
        
        this.modelClient = new OpenAIModelClient();
    }
    
    public String processMessage(String chatId, String userMessage) {
        // Add user message to memory
        Message userMsg = Message.builder()
            .id(UUID.randomUUID().toString())
            .chatId(chatId)
            .content(userMessage)
            .messageType(ChatMessageType.USER)
            .build();
        memory.add(userMsg);
        
        // Generate AI response
        ModelTextRequest request = ModelTextRequest.builder()
            .messages(memory.messages())
            .temperature(0.7)
            .maxTokens(1000)
            .build();
        
        ModelTextResponse response = modelClient.generateText(request);
        
        // Add AI response to memory
        Message aiMsg = Message.builder()
            .id(UUID.randomUUID().toString())
            .chatId(chatId)
            .content(response.getContent())
            .messageType(ChatMessageType.AI)
            .build();
        memory.add(aiMsg);
        
        return response.getContent();
    }
}
```

### Document Processing Pipeline

```java
@Service
public class DocumentProcessor {
    private final DocumentSplitter splitter;
    private final TextSimilarityUtil similarity;
    
    public DocumentProcessor() {
        this.splitter = DocumentSplitter.builder()
            .maxChunkSize(512)
            .overlapSize(50)
            .tokenizer(new SimpleTokenizer())
            .build();
    }
    
    public List<String> processDocument(String document) {
        // Split document into chunks
        List<String> chunks = splitter.split(document);
        
        // Remove duplicate chunks based on similarity
        List<String> uniqueChunks = new ArrayList<>();
        for (String chunk : chunks) {
            boolean isDuplicate = uniqueChunks.stream()
                .anyMatch(existing -> 
                    similarity.combinedSimilarity(chunk, existing) > 0.9);
            
            if (!isDuplicate) {
                uniqueChunks.add(chunk);
            }
        }
        
        return uniqueChunks;
    }
}
```

### Template Processing

```java
@Service
public class TemplateProcessor {
    private final VariableExtractor extractor;
    
    public String processTemplate(String template, Map<String, Object> variables) {
        // Extract required variables
        Set<String> requiredVars = extractor.extractVariables(template);
        
        // Validate all variables are provided
        for (String var : requiredVars) {
            if (!variables.containsKey(var)) {
                throw new IllegalArgumentException("Missing variable: " + var);
            }
        }
        
        // Process template (simplified - use actual template engine)
        String result = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", 
                                  String.valueOf(entry.getValue()));
        }
        
        return result;
    }
}
```

## Testing

### Unit Test Examples

```java
@Test
public void testDocumentSplitter() {
    DocumentSplitter splitter = DocumentSplitter.builder()
        .maxChunkSize(100)
        .overlapSize(20)
        .tokenizer(new SimpleTokenizer())
        .build();
    
    String document = "This is a long document that needs to be split...";
    List<String> chunks = splitter.split(document);
    
    assertThat(chunks).isNotEmpty();
    assertThat(chunks.get(0)).hasSizeLessThanOrEqualTo(100);
}

@Test
public void testTextSimilarity() {
    String text1 = "The quick brown fox";
    String text2 = "A quick brown fox";
    
    double similarity = TextSimilarityUtil.combinedSimilarity(text1, text2);
    assertThat(similarity).isBetween(0.7, 1.0);
}

@Test
public void testVariableExtraction() {
    String template = "Hello {{name}}! You have {{count}} messages.";
    Set<String> variables = VariableExtractor.extractVariables(template);
    
    assertThat(variables).containsExactlyInAnyOrder("name", "count");
}
```

## Performance Considerations

### Memory Management
- Use `TokenWindowChatMemory` for production to prevent memory leaks
- Configure appropriate token limits based on model context windows
- Consider persistent storage for long-term conversation history

### Text Processing
- `TextSimilarityUtil` operations are O(n²) for large texts
- Use caching for repeated similarity calculations
- Consider approximate algorithms for very large document sets

### JSON Processing
- `JsonUtils.repairJson()` has overhead - use sparingly
- Cache parsed JSON for repeated access
- Validate JSON structure before processing

## Integration with Other Modules

### With driftkit-clients
```java
// Use common domain objects with model clients
ModelTextRequest request = ModelTextRequest.builder()
    .messages(memory.messages()) // ChatItem list
    .temperature(0.7)
    .build();
```

### With driftkit-workflows
```java
// AITask integrates with workflow context
AITask task = AITask.builder()
    .workflowId("rag-workflow")
    .workflowContext(workflowContext)
    .variables(templateVariables)
    .build();
```

### With driftkit-vector
```java
// Document processing for vector storage
List<String> chunks = documentSplitter.split(document);
// Chunks can be embedded and stored in vector databases
```

## Error Handling

### Common Exceptions
- `IllegalArgumentException` - Invalid configuration or parameters
- `JsonProcessingException` - JSON parsing failures
- `ValidationException` - Bean validation failures
- `TokenLimitExceededException` - Memory capacity exceeded

### Best Practices
```java
// Always validate inputs
ValidationUtils.requireNonNull(chatId, "Chat ID cannot be null");

// Handle JSON parsing gracefully
Optional<JsonNode> json = JsonUtils.parseJson(inputJson);
if (json.isEmpty()) {
    log.warn("Failed to parse JSON: {}", inputJson);
    return defaultResponse;
}

// Use try-with-resources for cleanup
try (var resource = acquireResource()) {
    // Process resource
} catch (Exception e) {
    log.error("Processing failed", e);
    throw new ProcessingException("Failed to process request", e);
}
```

## Migration Guide

### From Version 1.x to 2.x
1. Update imports from `javax.annotation` to `jakarta.annotation`
2. Replace deprecated `@Getter/@Setter` with `@Data` where appropriate
3. Update `EtlConfig` usage to use Spring Boot configuration properties
4. Replace manual JSON parsing with `JsonUtils` methods

### Configuration Changes
```yaml
# Old format
etl:
  openai:
    apiKey: "sk-..."
    
# New format
driftkit:
  vault:
    - name: "primary"
      type: "openai"
      apiKey: "sk-..."
```

## Real-World Demo Examples

### 1. Building a Customer Support Chatbot

This example demonstrates building a complete customer support chatbot using the common module's chat management features.

```java
@Service
public class CustomerSupportBot {
    private final ChatMemory memory;
    private final ModelClient modelClient;
    private final TextSimilarityUtil similarity;
    
    public CustomerSupportBot() {
        // Initialize with 4K token window for GPT-4
        this.memory = TokenWindowChatMemory.builder()
            .maxTokens(4000)
            .tokenizer(new SimpleTokenizer())
            .store(new InMemoryChatMemoryStore())
            .build();
    }
    
    public String handleCustomerQuery(String chatId, String query) {
        // Add system message if it's a new conversation
        if (memory.messages().isEmpty()) {
            Message systemMsg = Message.builder()
                .id(UUID.randomUUID().toString())
                .chatId(chatId)
                .content("You are a helpful customer support agent for an e-commerce platform. Be professional, empathetic, and solution-oriented.")
                .messageType(ChatMessageType.SYSTEM)
                .build();
            memory.add(systemMsg);
        }
        
        // Check for similar previous queries
        String similarResponse = findSimilarResponse(query);
        if (similarResponse != null) {
            return similarResponse;
        }
        
        // Add user message
        Message userMsg = Message.builder()
            .id(UUID.randomUUID().toString())
            .chatId(chatId)
            .content(query)
            .messageType(ChatMessageType.USER)
            .requestInitTime(LocalDateTime.now())
            .build();
        memory.add(userMsg);
        
        // Generate response
        ModelTextRequest request = ModelTextRequest.builder()
            .messages(memory.messages())
            .temperature(0.7)
            .maxTokens(500)
            .build();
        
        ModelTextResponse response = modelClient.textToText(request);
        String aiResponse = response.getChoices().get(0).getMessage().getContent();
        
        // Add AI response to memory
        Message aiMsg = Message.builder()
            .id(UUID.randomUUID().toString())
            .chatId(chatId)
            .content(aiResponse)
            .messageType(ChatMessageType.AI)
            .responseTime(LocalDateTime.now())
            .build();
        memory.add(aiMsg);
        
        return aiResponse;
    }
    
    private String findSimilarResponse(String query) {
        // Look for similar queries in past conversations
        List<ChatItem> userMessages = memory.findByType(ChatMessageType.USER);
        
        for (ChatItem msg : userMessages) {
            double sim = TextSimilarityUtil.combinedSimilarity(query, msg.getContent());
            if (sim > 0.85) {
                // Find the AI response that followed this message
                // Return it as a cached response
                return "Based on a similar query...";
            }
        }
        return null;
    }
}
```

### 2. Document Intelligence System for Legal Contracts

This example shows how to build a document analysis system for legal contracts using document splitting and AI processing.

```java
@Service
public class LegalContractAnalyzer {
    private final DocumentSplitter splitter;
    private final ModelClient modelClient;
    private final Map<String, List<String>> contractClauses = new HashMap<>();
    
    public LegalContractAnalyzer() {
        this.splitter = DocumentSplitter.builder()
            .maxChunkSize(1024)  // Larger chunks for better context
            .overlapSize(100)    // Overlap to maintain clause continuity
            .tokenizer(new SimpleTokenizer())
            .build();
    }
    
    public ContractAnalysis analyzeContract(String contractId, String contractText) {
        // Split contract into analyzable chunks
        List<String> chunks = splitter.split(contractText);
        contractClauses.put(contractId, chunks);
        
        ContractAnalysis analysis = new ContractAnalysis();
        analysis.setContractId(contractId);
        analysis.setTotalClauses(chunks.size());
        
        // Analyze each chunk for specific legal elements
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            
            // Extract key information from each chunk
            ClauseAnalysis clauseAnalysis = analyzeClause(chunk, i);
            analysis.addClause(clauseAnalysis);
            
            // Check for risk factors
            if (containsRiskIndicators(chunk)) {
                analysis.addRiskFlag(new RiskFlag(i, chunk, assessRiskLevel(chunk)));
            }
        }
        
        // Generate executive summary
        analysis.setSummary(generateExecutiveSummary(analysis));
        
        return analysis;
    }
    
    private ClauseAnalysis analyzeClause(String clause, int index) {
        // Use AI to categorize and extract key information
        String prompt = """
            Analyze this legal clause and provide:
            1. Clause type (e.g., payment terms, liability, termination)
            2. Key obligations
            3. Important dates or deadlines
            4. Parties involved
            
            Clause: """ + clause;
            
        ModelTextRequest request = ModelTextRequest.builder()
            .prompt(prompt)
            .temperature(0.1)  // Low temperature for factual analysis
            .maxTokens(300)
            .jsonResponse(true)
            .build();
            
        ModelTextResponse response = modelClient.textToText(request);
        
        // Parse the structured response
        return parseClauseAnalysis(response.getContent());
    }
    
    private boolean containsRiskIndicators(String text) {
        String[] riskKeywords = {
            "unlimited liability", "indemnification", "penalty", 
            "liquidated damages", "non-compete", "exclusivity"
        };
        
        String normalizedText = text.toLowerCase();
        return Arrays.stream(riskKeywords)
            .anyMatch(normalizedText::contains);
    }
    
    public List<String> findSimilarClauses(String contractId, String searchClause) {
        List<String> clauses = contractClauses.get(contractId);
        if (clauses == null) return Collections.emptyList();
        
        return clauses.stream()
            .filter(clause -> TextSimilarityUtil.combinedSimilarity(searchClause, clause) > 0.7)
            .collect(Collectors.toList());
    }
}

@Data
class ContractAnalysis {
    private String contractId;
    private int totalClauses;
    private List<ClauseAnalysis> clauses = new ArrayList<>();
    private List<RiskFlag> riskFlags = new ArrayList<>();
    private String summary;
    
    public void addClause(ClauseAnalysis clause) {
        clauses.add(clause);
    }
    
    public void addRiskFlag(RiskFlag flag) {
        riskFlags.add(flag);
    }
}
```

### 3. Multi-Language Content Processing Pipeline

This example demonstrates handling multi-language content with automatic translation and cultural adaptation.

```java
@Service
public class MultiLanguageContentProcessor {
    private final VariableExtractor extractor;
    private final ModelClient modelClient;
    private final Map<Language, Map<String, String>> translations = new HashMap<>();
    
    public ProcessedContent processContent(String template, Language targetLanguage, Map<String, Object> data) {
        // Extract all variables from template
        Set<String> variables = extractor.extractVariables(template);
        Set<String> conditionals = extractor.extractConditionalVariables(template);
        
        // Validate all required variables are present
        for (String var : variables) {
            if (!data.containsKey(var) && !conditionals.contains(var)) {
                throw new IllegalArgumentException("Missing required variable: " + var);
            }
        }
        
        // Process template with language-specific adaptations
        String processed = processTemplate(template, targetLanguage, data);
        
        // Apply cultural adaptations
        processed = applyCulturalAdaptations(processed, targetLanguage);
        
        // Generate language-specific metadata
        ProcessedContent content = new ProcessedContent();
        content.setContent(processed);
        content.setLanguage(targetLanguage);
        content.setVariablesUsed(variables);
        content.setProcessingTime(LocalDateTime.now());
        
        return content;
    }
    
    private String processTemplate(String template, Language language, Map<String, Object> data) {
        // First, handle language-specific number and date formatting
        Map<String, Object> localizedData = localizeData(data, language);
        
        // Process the template
        String processed = TemplateEngine.renderTemplate(template, localizedData);
        
        // Translate if needed
        if (language != Language.ENGLISH) {
            processed = translateContent(processed, language);
        }
        
        return processed;
    }
    
    private Map<String, Object> localizeData(Map<String, Object> data, Language language) {
        Map<String, Object> localized = new HashMap<>(data);
        
        // Format numbers based on locale
        data.forEach((key, value) -> {
            if (value instanceof Number) {
                localized.put(key, formatNumber((Number) value, language));
            } else if (value instanceof LocalDateTime) {
                localized.put(key, formatDate((LocalDateTime) value, language));
            }
        });
        
        return localized;
    }
    
    private String translateContent(String content, Language targetLanguage) {
        // Use AI for context-aware translation
        String prompt = String.format(
            "Translate the following content to %s. Maintain the tone and context:\n\n%s",
            targetLanguage.name(), content
        );
        
        ModelTextRequest request = ModelTextRequest.builder()
            .prompt(prompt)
            .temperature(0.3)
            .maxTokens(1000)
            .build();
            
        ModelTextResponse response = modelClient.textToText(request);
        return response.getContent();
    }
    
    public void preloadTranslations(String key, Map<Language, String> translations) {
        translations.forEach((lang, translation) -> {
            this.translations.computeIfAbsent(lang, k -> new HashMap<>())
                .put(key, translation);
        });
    }
}
```

### 4. Intelligent Task Routing System

This example shows how to build an intelligent task routing system using AITask and workflow context.

```java
@Service
public class IntelligentTaskRouter {
    private final Map<String, WorkflowHandler> handlers = new HashMap<>();
    private final ModelClient modelClient;
    
    public TaskResult routeTask(AITask task) {
        // Analyze task to determine best workflow
        String workflowId = determineWorkflow(task);
        task.setWorkflowId(workflowId);
        
        // Get appropriate handler
        WorkflowHandler handler = handlers.get(workflowId);
        if (handler == null) {
            task.setErrorMessage("No handler found for workflow: " + workflowId);
            task.setCompletedAt(LocalDateTime.now());
            return TaskResult.failure(task);
        }
        
        try {
            // Execute task with context preservation
            TaskResult result = handler.execute(task);
            
            // Update task with results
            task.setCompletedAt(LocalDateTime.now());
            task.setGrade(evaluateResult(result));
            
            return result;
        } catch (Exception e) {
            task.setErrorMessage("Task execution failed: " + e.getMessage());
            task.setCompletedAt(LocalDateTime.now());
            task.setGrade(Grade.POOR);
            return TaskResult.failure(task);
        }
    }
    
    private String determineWorkflow(AITask task) {
        // Use AI to classify the task
        String classificationPrompt = buildClassificationPrompt(task);
        
        ModelTextRequest request = ModelTextRequest.builder()
            .prompt(classificationPrompt)
            .temperature(0.1)
            .maxTokens(50)
            .jsonResponse(true)
            .build();
            
        ModelTextResponse response = modelClient.textToText(request);
        
        // Parse workflow ID from response
        JsonNode result = JsonUtils.parseJson(response.getContent()).orElse(null);
        return result != null ? result.get("workflow").asText() : "default";
    }
    
    private String buildClassificationPrompt(AITask task) {
        return String.format("""
            Classify this task into one of the following workflows:
            - customer-service: Customer inquiries and support
            - content-generation: Creating marketing or educational content
            - data-analysis: Analyzing data and generating reports
            - document-processing: Processing and extracting information from documents
            
            Task details:
            Prompt: %s
            Has Images: %s
            Has Audio: %s
            Variables: %s
            
            Respond with JSON: {"workflow": "workflow-id", "confidence": 0.0-1.0}
            """,
            task.getPrompt(),
            task.getImageUrls() != null && !task.getImageUrls().isEmpty(),
            task.getAudioUrl() != null,
            task.getVariables()
        );
    }
    
    private Grade evaluateResult(TaskResult result) {
        if (!result.isSuccess()) return Grade.POOR;
        
        double score = result.getConfidenceScore();
        if (score >= 0.9) return Grade.EXCELLENT;
        if (score >= 0.7) return Grade.GOOD;
        if (score >= 0.5) return Grade.AVERAGE;
        return Grade.POOR;
    }
    
    public void registerHandler(String workflowId, WorkflowHandler handler) {
        handlers.put(workflowId, handler);
    }
}

interface WorkflowHandler {
    TaskResult execute(AITask task);
}

@Data
class TaskResult {
    private boolean success;
    private String output;
    private double confidenceScore;
    private Map<String, Object> metadata;
    
    public static TaskResult failure(AITask task) {
        TaskResult result = new TaskResult();
        result.setSuccess(false);
        result.setOutput(task.getErrorMessage());
        result.setConfidenceScore(0.0);
        return result;
    }
}
```

### 5. Intelligent Chat Memory Optimization

This example shows advanced memory management for long-running conversations.

```java
@Service
public class OptimizedChatMemoryService {
    private final Map<String, TokenWindowChatMemory> userMemories = new ConcurrentHashMap<>();
    private final ModelClient modelClient;
    
    public String processUserMessage(String userId, String message) {
        TokenWindowChatMemory memory = getUserMemory(userId);
        
        // Add user message
        Message userMsg = Message.builder()
            .id(UUID.randomUUID().toString())
            .chatId(userId)
            .content(message)
            .messageType(ChatMessageType.USER)
            .requestInitTime(LocalDateTime.now())
            .build();
        memory.add(userMsg);
        
        // Check if we need to summarize old messages
        if (shouldSummarize(memory)) {
            summarizeOldMessages(memory);
        }
        
        // Generate response with optimized context
        String response = generateResponse(memory);
        
        // Add AI response
        Message aiMsg = Message.builder()
            .id(UUID.randomUUID().toString())
            .chatId(userId)
            .content(response)
            .messageType(ChatMessageType.AI)
            .responseTime(LocalDateTime.now())
            .build();
        memory.add(aiMsg);
        
        return response;
    }
    
    private TokenWindowChatMemory getUserMemory(String userId) {
        return userMemories.computeIfAbsent(userId, k -> 
            TokenWindowChatMemory.builder()
                .maxTokens(3500)  // Leave room for response
                .tokenizer(new SimpleTokenizer())
                .store(new InMemoryChatMemoryStore())
                .build()
        );
    }
    
    private boolean shouldSummarize(TokenWindowChatMemory memory) {
        // Summarize when we have more than 10 message pairs
        long messageCount = memory.messages().stream()
            .filter(m -> m.getMessageType() != ChatMessageType.SYSTEM)
            .count();
        return messageCount > 20;
    }
    
    private void summarizeOldMessages(TokenWindowChatMemory memory) {
        List<ChatItem> messages = memory.messages();
        
        // Keep system message and recent messages
        List<ChatItem> toSummarize = messages.stream()
            .filter(m -> m.getMessageType() != ChatMessageType.SYSTEM)
            .limit(messages.size() - 10)  // Keep last 10 messages
            .collect(Collectors.toList());
        
        if (toSummarize.isEmpty()) return;
        
        // Generate summary
        String summaryPrompt = buildSummaryPrompt(toSummarize);
        ModelTextRequest request = ModelTextRequest.builder()
            .prompt(summaryPrompt)
            .temperature(0.3)
            .maxTokens(500)
            .build();
        
        ModelTextResponse response = modelClient.textToText(request);
        String summary = response.getContent();
        
        // Create summary message
        Message summaryMsg = Message.builder()
            .id(UUID.randomUUID().toString())
            .chatId(memory.messages().get(0).getChatId())
            .content("Previous conversation summary: " + summary)
            .messageType(ChatMessageType.SYSTEM)
            .createdTime(System.currentTimeMillis())
            .build();
        
        // Clear old messages and add summary
        memory.clear();
        
        // Re-add system message if exists
        messages.stream()
            .filter(m -> m.getMessageType() == ChatMessageType.SYSTEM)
            .findFirst()
            .ifPresent(memory::add);
            
        // Add summary
        memory.add(summaryMsg);
        
        // Add recent messages
        messages.stream()
            .skip(Math.max(0, messages.size() - 10))
            .forEach(memory::add);
    }
    
    private String buildSummaryPrompt(List<ChatItem> messages) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Summarize the following conversation, keeping key points and context:\n\n");
        
        for (ChatItem msg : messages) {
            String role = msg.getMessageType() == ChatMessageType.USER ? "User" : "Assistant";
            prompt.append(role).append(": ").append(msg.getContent()).append("\n\n");
        }
        
        prompt.append("Provide a concise summary that preserves important information and context.");
        return prompt.toString();
    }
}
```

This comprehensive documentation provides a complete reference for the driftkit-common module, covering all major components, usage patterns, and integration points. The module serves as the foundation for building sophisticated AI applications with robust chat management, document processing, and text analysis capabilities.
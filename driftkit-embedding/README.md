# DriftKit Embedding Module

## Overview

The `driftkit-embedding` module provides a unified abstraction layer for text embedding services, supporting multiple providers including OpenAI, Cohere, and local BERT models. It offers a consistent API for generating vector representations of text while maintaining flexibility for different embedding backends.

## Spring Boot Initialization

To use the embedding module in your Spring Boot application, the module will be automatically configured when you provide embedding configuration:

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
  embedding:
    name: "openai"  # or "cohere", "local-bert"
    config:
      apiKey: "${OPENAI_API_KEY}"
      modelName: "text-embedding-ada-002"
      dimension: "1536"
```

The module provides:
- **Auto-configuration class**: `EmbeddingAutoConfiguration`
- **Conditional activation**: Only when `driftkit.embedding.name` is configured
- **Bean creation**: Automatically creates `EmbeddingModel` bean from configuration

## Architecture

### Module Structure

```
driftkit-embedding/
├── driftkit-embedding-core/           # Core embedding functionality
├── driftkit-embedding-spring-ai/      # Spring AI integration
├── driftkit-embedding-spring-boot-starter/  # Spring Boot auto-configuration
└── pom.xml                           # Parent module configuration
```

### Key Dependencies

- **DJL API** - Deep Java Library for AI model inference
- **HuggingFace Tokenizers** - Text tokenization for local models
- **ONNX Runtime** - Efficient model inference for local BERT models
- **OpenFeign** - HTTP client for external API integrations
- **DriftKit Common** - Shared domain objects and utilities

## Core Abstractions

### EmbeddingModel Interface

The central abstraction for all embedding providers:

The central abstraction for all embedding providers. It provides provider identification through `supportsName()`, model access for local models, configuration handling, and core embedding methods with default implementations for single and batch text processing.

**Key Features:**
- **Provider Identification** - Each implementation declares support via `supportsName()`
- **Flexible Architecture** - Default implementations handle common patterns
- **Token Counting** - Built-in support for usage estimation
- **Batch Processing** - Efficient handling of multiple text segments

### EmbeddingFactory

Factory pattern for dynamic model loading using Java ServiceLoader:

Factory pattern implementation for dynamic model loading using Java ServiceLoader. It discovers available embedding providers at runtime and initializes them with configuration.

**Usage Example:**
```java
Map<String, String> config = Map.of(
    "apiKey", "your-api-key",
    "model", "text-embedding-ada-002"
);

EmbeddingModel model = EmbeddingFactory.fromName("openai", config);
```

## Domain Objects

### Embedding

Vector representation wrapper with utility methods:

Vector representation wrapper with factory methods for different input types (double[], float[], List<Float>). Provides utility methods including normalization, dimension retrieval, and vector format conversion.

### TextSegment

Text container with optional metadata:

Text container with optional metadata. Provides factory methods for creating segments with or without metadata.

### Metadata

Type-safe metadata management system:

Type-safe metadata management system supporting String, UUID, Integer, Long, Float, and Double types. Provides type-safe getters and a fluent API for adding metadata.

**Usage Example:**
```java
Metadata metadata = new Metadata()
    .put("source", "document.pdf")
    .put("page", 1)
    .put("confidence", 0.95f);

TextSegment segment = TextSegment.from("Your text content", metadata);
```

## Provider Implementations

### Spring AI Integration

The `driftkit-embedding-spring-ai` module provides integration with Spring AI's embedding capabilities, supporting multiple providers through a unified interface.

#### Supported Spring AI Providers

- **OpenAI** - GPT embeddings (text-embedding-3-small, text-embedding-3-large)
- **Azure OpenAI** - Microsoft's hosted OpenAI service
- **Ollama** - Local embedding models

#### Configuration

```yaml
driftkit:
  embedding:
    name: "spring-ai"
    config:
      provider: "openai"  # or "azure-openai", "ollama"
      modelName: "text-embedding-3-small"
      apiKey: "${OPENAI_API_KEY}"
```

#### Implementation Details

The Spring AI integration adapter:
- Implements the DriftKit `EmbeddingModel` interface
- Handles provider-specific configuration automatically
- Converts between Spring AI and DriftKit embedding formats
- Provides comprehensive error handling and validation
- Supports batch embedding operations

For detailed Spring AI configuration options, see the [Spring AI module documentation](driftkit-embedding-spring-ai/README.md).

### OpenAI Integration

#### OpenAIEmbeddingModel

```java
@NoArgsConstructor
public class OpenAIEmbeddingModel implements EmbeddingModel {
    private EmbeddingOpenAIApiClient apiClient;
    private String modelName;
    
    @Override
    public boolean supportsName(String name) {
        return "openai".equals(name);
    }
    
    @Override
    public void configure(EmbeddingServiceConfig config) {
        this.modelName = config.getConfig().get(EtlConfig.MODEL_NAME);
        this.apiClient = Feign.builder()
            .encoder(new JacksonEncoder())
            .decoder(new JacksonDecoder())
            .requestInterceptor(new OpenAIAuthInterceptor(config.get(EtlConfig.API_KEY)))
            .target(EmbeddingOpenAIApiClient.class, 
                    config.get(EtlConfig.HOST, "https://api.openai.com"));
    }
    
    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
        List<String> texts = segments.stream()
            .map(TextSegment::text)
            .collect(Collectors.toList());
            
        EmbeddingRequest request = new EmbeddingRequest(modelName, texts);
        EmbeddingResponse response = apiClient.getEmbeddings(request);
        
        List<Embedding> embeddings = response.getData().stream()
            .map(data -> Embedding.from(data.getEmbedding()))
            .collect(Collectors.toList());
            
        Usage usage = response.getUsage();
        TokenUsage tokenUsage = new TokenUsage(
            usage.getPromptTokens(),
            usage.getTotalTokens() - usage.getPromptTokens()
        );
        
        return Response.from(embeddings, tokenUsage);
    }
    
    @Override
    public int estimateTokenCount(String text) {
        return text.length() / 4; // Approximate estimation
    }
    
    @Override
    public AIOnnxBertBiEncoder model() {
        throw new UnsupportedOperationException("OpenAI models don't expose ONNX encoder");
    }
}
```

#### API Client Definition

```java
public interface EmbeddingOpenAIApiClient {
    @RequestLine("POST /v1/embeddings")
    @Headers("Content-Type: application/json")
    EmbeddingResponse getEmbeddings(EmbeddingRequest request);
}

public class OpenAIAuthInterceptor implements RequestInterceptor {
    private final String apiKey;
    
    public OpenAIAuthInterceptor(String apiKey) {
        this.apiKey = apiKey;
    }
    
    @Override
    public void apply(RequestTemplate template) {
        template.header("Authorization", "Bearer " + apiKey);
        template.header("Content-Type", "application/json");
    }
}
```

**Configuration Example:**
```yaml
driftkit:
  embeddingServices:
    - name: "openai-embeddings"
      type: "openai"
      config:
        apiKey: "${OPENAI_API_KEY}"
        model: "text-embedding-ada-002"
        host: "https://api.openai.com"
```

### Cohere Integration

#### CohereEmbeddingModel

```java
public class CohereEmbeddingModel implements EmbeddingModel {
    private final CohereApiClient apiClient;
    
    @Override
    public boolean supportsName(String name) {
        return "cohere".equals(name);
    }
    
    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
        List<String> texts = segments.stream()
            .map(TextSegment::text)
            .collect(Collectors.toList());
            
        CohereEmbeddingRequest request = CohereEmbeddingRequest.builder()
            .texts(texts)
            .model("embed-english-v2.0")
            .build();
            
        CohereEmbeddingResponse response = apiClient.getEmbeddings(request);
        
        List<Embedding> embeddings = new ArrayList<>();
        for (List<Double> embeddingValues : response.getEmbeddings()) {
            double[] embeddingArray = embeddingValues.stream()
                .mapToDouble(Double::doubleValue)
                .toArray();
            embeddings.add(Embedding.from(embeddingArray));
        }
        
        return Response.from(embeddings, new TokenUsage(0)); // Cohere doesn't provide token usage
    }
    
    @Override
    public int estimateTokenCount(String text) {
        return text.length() / 5; // Approximate estimation
    }
    
    @Override
    public AIOnnxBertBiEncoder model() {
        throw new UnsupportedOperationException("Cohere models don't expose ONNX encoder");
    }
}
```

### Local BERT Models

#### AIOnnxBertBiEncoder

Sophisticated ONNX-based BERT encoder with comprehensive features:
- **Token Management**: Handles texts up to 510 tokens (512 - 2 special tokens)
- **Text Partitioning**: Automatically splits long texts at sentence boundaries
- **Embedding Combination**: Uses weighted averaging based on token count
- **Pooling Modes**: Supports CLS and MEAN pooling strategies
- **L2 Normalization**: Normalizes final embeddings for consistent similarity calculations
- **HuggingFace Integration**: Uses HuggingFace tokenizers for accurate token counting

#### BertGenericEmbeddingModel

Implementation of EmbeddingModel for local BERT models. Configures an AIOnnxBertBiEncoder with specified model and tokenizer paths, using MEAN pooling mode by default.

**Configuration Example:**
```yaml
driftkit:
  embeddingServices:
    - name: "local-bert"
      type: "local"
      config:
        modelPath: "/path/to/bert-base-uncased.onnx"
        tokenizerPath: "/path/to/tokenizer"
```

## Usage Patterns

### Basic Embedding

```java
@Service
public class EmbeddingService {
    
    public Embedding embedText(String text) throws Exception {
        Map<String, String> config = Map.of(
            "apiKey", openaiApiKey,
            "model", "text-embedding-ada-002"
        );
        
        EmbeddingModel model = EmbeddingFactory.fromName("openai", config);
        TextSegment segment = TextSegment.from(text);
        
        Response<Embedding> response = model.embed(segment);
        return response.content();
    }
}
```

### Batch Processing

```java
@Service
public class BatchEmbeddingService {
    
    public List<Embedding> embedTexts(List<String> texts) throws Exception {
        Map<String, String> config = Map.of(
            "apiKey", openaiApiKey,
            "model", "text-embedding-ada-002"
        );
        
        EmbeddingModel model = EmbeddingFactory.fromName("openai", config);
        
        List<TextSegment> segments = texts.stream()
            .map(TextSegment::from)
            .collect(Collectors.toList());
            
        Response<List<Embedding>> response = model.embedAll(segments);
        return response.content();
    }
}
```

### Document Processing with Metadata

```java
@Service
public class DocumentEmbeddingService {
    
    public List<Embedding> processDocument(String documentContent, String source) throws Exception {
        // Split document into chunks
        DocumentSplitter splitter = DocumentSplitter.builder()
            .maxChunkSize(512)
            .overlapSize(50)
            .build();
            
        List<String> chunks = splitter.split(documentContent);
        
        // Create segments with metadata
        List<TextSegment> segments = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Metadata metadata = new Metadata()
                .put("source", source)
                .put("chunk_index", i)
                .put("total_chunks", chunks.size());
                
            segments.add(TextSegment.from(chunks.get(i), metadata));
        }
        
        // Generate embeddings
        EmbeddingModel model = EmbeddingFactory.fromName("openai", getOpenAIConfig());
        Response<List<Embedding>> response = model.embedAll(segments);
        
        return response.content();
    }
}
```

### Local Model Usage

```java
@Service
public class LocalEmbeddingService {
    
    public Embedding embedWithLocalModel(String text) throws Exception {
        Map<String, String> config = Map.of(
            "modelPath", "/models/bert-base-uncased.onnx",
            "tokenizerPath", "/models/tokenizer"
        );
        
        EmbeddingModel model = EmbeddingFactory.fromName("local", config);
        TextSegment segment = TextSegment.from(text);
        
        Response<Embedding> response = model.embed(segment);
        Embedding embedding = response.content();
        
        // Optional: normalize the embedding
        embedding.normalize();
        
        return embedding;
    }
}
```

### Similarity Search

```java
@Service
public class SimilaritySearchService {
    
    public double calculateCosineSimilarity(Embedding embedding1, Embedding embedding2) {
        float[] vector1 = embedding1.vector();
        float[] vector2 = embedding2.vector();
        
        if (vector1.length != vector2.length) {
            throw new IllegalArgumentException("Embeddings must have the same dimension");
        }
        
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        
        for (int i = 0; i < vector1.length; i++) {
            dotProduct += vector1[i] * vector2[i];
            norm1 += vector1[i] * vector1[i];
            norm2 += vector2[i] * vector2[i];
        }
        
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
    
    public List<ScoredEmbedding> findSimilar(Embedding queryEmbedding, 
                                           List<Embedding> candidates, 
                                           int topK) {
        return candidates.stream()
            .map(candidate -> new ScoredEmbedding(
                candidate, 
                calculateCosineSimilarity(queryEmbedding, candidate)
            ))
            .sorted((a, b) -> Double.compare(b.score, a.score))
            .limit(topK)
            .collect(Collectors.toList());
    }
    
    @Data
    @AllArgsConstructor
    public static class ScoredEmbedding {
        private final Embedding embedding;
        private final double score;
    }
}
```

## Configuration and Best Practices

### Provider Selection

```java
@Configuration
public class EmbeddingConfiguration {
    
    @Value("${driftkit.embedding.provider:openai}")
    private String defaultProvider;
    
    @Value("${openai.api.key}")
    private String openaiApiKey;
    
    @Bean
    public EmbeddingModel primaryEmbeddingModel() throws Exception {
        Map<String, String> config = switch (defaultProvider) {
            case "openai" -> Map.of(
                "apiKey", openaiApiKey,
                "model", "text-embedding-ada-002"
            );
            case "cohere" -> Map.of(
                "apiKey", cohereApiKey,
                "model", "embed-english-v2.0"
            );
            case "local" -> Map.of(
                "modelPath", "/models/bert-base-uncased.onnx",
                "tokenizerPath", "/models/tokenizer"
            );
            default -> throw new IllegalArgumentException("Unknown provider: " + defaultProvider);
        };
        
        return EmbeddingFactory.fromName(defaultProvider, config);
    }
}
```

### Error Handling

```java
@Service
public class RobustEmbeddingService {
    
    private final EmbeddingModel primaryModel;
    private final EmbeddingModel fallbackModel;
    
    public Embedding embedWithFallback(String text) {
        try {
            TextSegment segment = TextSegment.from(text);
            Response<Embedding> response = primaryModel.embed(segment);
            return response.content();
        } catch (Exception e) {
            log.warn("Primary embedding model failed, using fallback", e);
            try {
                TextSegment segment = TextSegment.from(text);
                Response<Embedding> response = fallbackModel.embed(segment);
                return response.content();
            } catch (Exception fallbackException) {
                log.error("Both primary and fallback embedding models failed", fallbackException);
                throw new EmbeddingException("Failed to generate embedding", fallbackException);
            }
        }
    }
}
```

### Performance Optimization

```java
@Service
public class OptimizedEmbeddingService {
    
    private final EmbeddingModel model;
    private final Cache<String, Embedding> embeddingCache;
    
    public OptimizedEmbeddingService(EmbeddingModel model) {
        this.model = model;
        this.embeddingCache = Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();
    }
    
    public Embedding embedWithCaching(String text) throws Exception {
        return embeddingCache.get(text, key -> {
            try {
                TextSegment segment = TextSegment.from(key);
                Response<Embedding> response = model.embed(segment);
                return response.content();
            } catch (Exception e) {
                throw new RuntimeException("Failed to generate embedding", e);
            }
        });
    }
    
    public List<Embedding> embedBatch(List<String> texts, int batchSize) throws Exception {
        List<Embedding> results = new ArrayList<>();
        
        for (int i = 0; i < texts.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, texts.size());
            List<String> batch = texts.subList(i, endIndex);
            
            List<TextSegment> segments = batch.stream()
                .map(TextSegment::from)
                .collect(Collectors.toList());
                
            Response<List<Embedding>> response = model.embedAll(segments);
            results.addAll(response.content());
            
            // Rate limiting for API providers
            if (endIndex < texts.size()) {
                Thread.sleep(100); // 100ms delay between batches
            }
        }
        
        return results;
    }
}
```

## Testing

### Unit Tests

```java
@ExtendWith(MockitoExtension.class)
class EmbeddingModelTest {
    
    @Test
    void shouldCreateEmbeddingFromText() throws Exception {
        Map<String, String> config = Map.of(
            "apiKey", "test-key",
            "model", "text-embedding-ada-002"
        );
        
        EmbeddingModel model = EmbeddingFactory.fromName("openai", config);
        TextSegment segment = TextSegment.from("test text");
        
        Response<Embedding> response = model.embed(segment);
        
        assertThat(response).isNotNull();
        assertThat(response.content()).isNotNull();
        assertThat(response.content().dimension()).isGreaterThan(0);
    }
    
    @Test
    void shouldHandleBatchEmbedding() throws Exception {
        List<TextSegment> segments = List.of(
            TextSegment.from("first text"),
            TextSegment.from("second text"),
            TextSegment.from("third text")
        );
        
        EmbeddingModel model = EmbeddingFactory.fromName("openai", config);
        Response<List<Embedding>> response = model.embedAll(segments);
        
        assertThat(response.content()).hasSize(3);
        assertThat(response.tokenUsage().inputTokenCount()).isGreaterThan(0);
    }
}
```

### Integration Tests

```java
@SpringBootTest
@TestPropertySource(properties = {
    "driftkit.embedding.provider=openai",
    "openai.api.key=test-key"
})
class EmbeddingIntegrationTest {
    
    @Autowired
    private EmbeddingModel embeddingModel;
    
    @Test
    void shouldIntegrateWithSpringBoot() throws Exception {
        TextSegment segment = TextSegment.from("integration test");
        Response<Embedding> response = embeddingModel.embed(segment);
        
        assertThat(response).isNotNull();
        assertThat(response.content().dimension()).isEqualTo(1536); // OpenAI ada-002 dimension
    }
}
```

## Extension Points

### Adding New Providers

1. Implement the `EmbeddingModel` interface
2. Add provider identification logic in `supportsName()`
3. Implement configuration handling in `configure()`
4. Add the implementation to `META-INF/services/ai.driftkit.embedding.core.service.EmbeddingModel`

```java
public class CustomEmbeddingModel implements EmbeddingModel {
    
    @Override
    public boolean supportsName(String name) {
        return "custom".equals(name);
    }
    
    @Override
    public void configure(EmbeddingServiceConfig config) {
        // Custom configuration logic
    }
    
    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
        // Custom embedding logic
        return null;
    }
    
    @Override
    public AIOnnxBertBiEncoder model() {
        throw new UnsupportedOperationException("Custom models don't expose ONNX encoder");
    }
}
```

### Custom Metadata Types

Extend the `Metadata` class to support additional data types:

```java
public class ExtendedMetadata extends Metadata {
    private static final Set<Class<?>> EXTENDED_TYPES = Set.of(
        LocalDateTime.class, BigDecimal.class, CustomType.class
    );
    
    @Override
    public <T> Metadata put(String key, T value) {
        if (value != null && !EXTENDED_TYPES.contains(value.getClass())) {
            return super.put(key, value);
        }
        // Handle extended types
        return this;
    }
}
```

## Demo Examples

### 1. Simple Document Search

This example demonstrates basic document search using embeddings.

```java
@Service
public class SimpleDocumentSearch {
    
    private final EmbeddingModel embeddingModel;
    private final Map<String, SearchDocument> documents = new HashMap<>();
    
    public SimpleDocumentSearch() throws Exception {
        Map<String, String> config = Map.of(
            "apiKey", System.getenv("OPENAI_API_KEY"),
            "model", "text-embedding-ada-002"
        );
        this.embeddingModel = EmbeddingFactory.fromName("openai", config);
    }
    
    public void addDocument(String id, String title, String content) throws Exception {
        String fullText = title + " " + content;
        TextSegment segment = TextSegment.from(fullText);
        Response<Embedding> response = embeddingModel.embed(segment);
        
        SearchDocument doc = new SearchDocument(id, title, content, response.content());
        documents.put(id, doc);
    }
    
    public List<SearchResult> search(String query, int limit) throws Exception {
        TextSegment querySegment = TextSegment.from(query);
        Response<Embedding> queryResponse = embeddingModel.embed(querySegment);
        Embedding queryEmbedding = queryResponse.content();
        
        return documents.values().stream()
            .map(doc -> new SearchResult(doc, calculateSimilarity(queryEmbedding, doc.getEmbedding())))
            .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    private double calculateSimilarity(Embedding a, Embedding b) {
        float[] vectorA = a.vector();
        float[] vectorB = b.vector();
        
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += vectorA[i] * vectorA[i];
            normB += vectorB[i] * vectorB[i];
        }
        
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
    
    @Data
    @AllArgsConstructor
    public static class SearchDocument {
        private String id;
        private String title;
        private String content;
        private Embedding embedding;
    }
    
    @Data
    @AllArgsConstructor
    public static class SearchResult {
        private SearchDocument document;
        private double score;
    }
}
```

### 2. Content Recommendation

This example shows simple content recommendations based on user preferences.

```java
@Service
public class SimpleContentRecommendation {
    
    private final EmbeddingModel embeddingModel;
    private final Map<String, ContentItem> content = new HashMap<>();
    private final Map<String, Embedding> userPreferences = new HashMap<>();
    
    public SimpleContentRecommendation() throws Exception {
        Map<String, String> config = Map.of(
            "apiKey", System.getenv("OPENAI_API_KEY"),
            "model", "text-embedding-ada-002"
        );
        this.embeddingModel = EmbeddingFactory.fromName("openai", config);
    }
    
    public void addContent(String contentId, String title, String description) throws Exception {
        String text = title + " " + description;
        TextSegment segment = TextSegment.from(text);
        Response<Embedding> response = embeddingModel.embed(segment);
        
        ContentItem item = new ContentItem(contentId, title, description, response.content());
        content.put(contentId, item);
    }
    
    public void updateUserPreferences(String userId, String preferencesText) throws Exception {
        TextSegment segment = TextSegment.from(preferencesText);
        Response<Embedding> response = embeddingModel.embed(segment);
        userPreferences.put(userId, response.content());
    }
    
    public List<RecommendationResult> getRecommendations(String userId, int count) {
        Embedding userEmbedding = userPreferences.get(userId);
        if (userEmbedding == null) {
            return Collections.emptyList();
        }
        
        return content.values().stream()
            .map(item -> new RecommendationResult(item, calculateSimilarity(userEmbedding, item.getEmbedding())))
            .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
            .limit(count)
            .collect(Collectors.toList());
    }
    
    private double calculateSimilarity(Embedding a, Embedding b) {
        float[] vectorA = a.vector();
        float[] vectorB = b.vector();
        
        double dotProduct = 0.0;
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
        }
        return dotProduct;
    }
    
    @Data
    @AllArgsConstructor
    public static class ContentItem {
        private String id;
        private String title;
        private String description;
        private Embedding embedding;
    }
    
    @Data
    @AllArgsConstructor
    public static class RecommendationResult {
        private ContentItem content;
        private double score;
    }
}
```

### 3. Document Similarity

This example demonstrates finding similar documents.

```java
@Service
public class DocumentSimilarity {
    
    private final EmbeddingModel embeddingModel;
    private final Map<String, DocumentItem> documents = new HashMap<>();
    
    public DocumentSimilarity() throws Exception {
        Map<String, String> config = Map.of(
            "apiKey", System.getenv("OPENAI_API_KEY"),
            "model", "text-embedding-ada-002"
        );
        this.embeddingModel = EmbeddingFactory.fromName("openai", config);
    }
    
    public void addDocument(String id, String content, String category) throws Exception {
        TextSegment segment = TextSegment.from(content);
        Response<Embedding> response = embeddingModel.embed(segment);
        
        DocumentItem doc = new DocumentItem(id, content, category, response.content());
        documents.put(id, doc);
    }
    
    public List<SimilarDocument> findSimilar(String documentId, int count) {
        DocumentItem targetDoc = documents.get(documentId);
        if (targetDoc == null) {
            return Collections.emptyList();
        }
        
        return documents.values().stream()
            .filter(doc -> !doc.getId().equals(documentId))
            .map(doc -> new SimilarDocument(doc, calculateSimilarity(targetDoc.getEmbedding(), doc.getEmbedding())))
            .sorted((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()))
            .limit(count)
            .collect(Collectors.toList());
    }
    
    public Map<String, List<DocumentItem>> groupByCategory() {
        return documents.values().stream()
            .collect(Collectors.groupingBy(DocumentItem::getCategory));
    }
    
    private double calculateSimilarity(Embedding a, Embedding b) {
        float[] vectorA = a.vector();
        float[] vectorB = b.vector();
        
        double sum = 0.0;
        for (int i = 0; i < vectorA.length; i++) {
            sum += vectorA[i] * vectorB[i];
        }
        return sum;
    }
    
    @Data
    @AllArgsConstructor
    public static class DocumentItem {
        private String id;
        private String content;
        private String category;
        private Embedding embedding;
    }
    
    @Data
    @AllArgsConstructor
    public static class SimilarDocument {
        private DocumentItem document;
        private double similarity;
    }
}
```

### 4. Content Tagging

This example shows automatic content tagging using predefined tag embeddings.

```java
@Service
public class SimpleContentTagging {
    
    private final EmbeddingModel embeddingModel;
    private final Map<String, TagItem> tags = new HashMap<>();
    
    public SimpleContentTagging() throws Exception {
        Map<String, String> config = Map.of(
            "apiKey", System.getenv("OPENAI_API_KEY"),
            "model", "text-embedding-ada-002"
        );
        this.embeddingModel = EmbeddingFactory.fromName("openai", config);
        initializeTags();
    }
    
    private void initializeTags() throws Exception {
        addTag("technology", "Technology related content including programming, AI, and software");
        addTag("business", "Business topics including marketing, finance, and strategy");
        addTag("education", "Educational content, tutorials, and learning materials");
        addTag("health", "Health and wellness topics");
        addTag("sports", "Sports, fitness, and athletic activities");
    }
    
    public void addTag(String name, String description) throws Exception {
        TextSegment segment = TextSegment.from(description);
        Response<Embedding> response = embeddingModel.embed(segment);
        
        TagItem tag = new TagItem(name, description, response.content());
        tags.put(name, tag);
    }
    
    public List<TagResult> suggestTags(String content, int maxTags) throws Exception {
        TextSegment segment = TextSegment.from(content);
        Response<Embedding> response = embeddingModel.embed(segment);
        Embedding contentEmbedding = response.content();
        
        return tags.values().stream()
            .map(tag -> new TagResult(tag.getName(), calculateSimilarity(contentEmbedding, tag.getEmbedding())))
            .filter(result -> result.getScore() > 0.5)
            .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
            .limit(maxTags)
            .collect(Collectors.toList());
    }
    
    private double calculateSimilarity(Embedding a, Embedding b) {
        float[] vectorA = a.vector();
        float[] vectorB = b.vector();
        
        double score = 0.0;
        for (int i = 0; i < vectorA.length; i++) {
            score += vectorA[i] * vectorB[i];
        }
        return score;
    }
    
    @Data
    @AllArgsConstructor
    public static class TagItem {
        private String name;
        private String description;
        private Embedding embedding;
    }
    
    @Data
    @AllArgsConstructor
    public static class TagResult {
        private String name;
        private double score;
    }
}
```

### 5. Multilingual Content Matching

This example demonstrates matching content across different languages.

```java
@Service
public class MultilingualContentMatcher {
    
    private final EmbeddingModel embeddingModel;
    private final Map<String, ContentEntry> contentByLanguage = new HashMap<>();
    
    public MultilingualContentMatcher() throws Exception {
        Map<String, String> config = Map.of(
            "apiKey", System.getenv("OPENAI_API_KEY"),
            "model", "text-embedding-ada-002"
        );
        this.embeddingModel = EmbeddingFactory.fromName("openai", config);
    }
    
    public void addContent(String id, String content, String language) throws Exception {
        TextSegment segment = TextSegment.from(content);
        Response<Embedding> response = embeddingModel.embed(segment);
        
        ContentEntry entry = new ContentEntry(id, content, language, response.content());
        contentByLanguage.put(id, entry);
    }
    
    public List<MatchResult> findSimilarAcrossLanguages(String contentId, String excludeLanguage) {
        ContentEntry sourceContent = contentByLanguage.get(contentId);
        if (sourceContent == null) {
            return Collections.emptyList();
        }
        
        return contentByLanguage.values().stream()
            .filter(entry -> !entry.getId().equals(contentId))
            .filter(entry -> !entry.getLanguage().equals(excludeLanguage))
            .map(entry -> new MatchResult(entry, calculateSimilarity(sourceContent.getEmbedding(), entry.getEmbedding())))
            .filter(result -> result.getScore() > 0.7)
            .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
            .collect(Collectors.toList());
    }
    
    public Map<String, Long> getLanguageStats() {
        return contentByLanguage.values().stream()
            .collect(Collectors.groupingBy(ContentEntry::getLanguage, Collectors.counting()));
    }
    
    private double calculateSimilarity(Embedding a, Embedding b) {
        float[] vectorA = a.vector();
        float[] vectorB = b.vector();
        
        double similarity = 0.0;
        for (int i = 0; i < vectorA.length; i++) {
            similarity += vectorA[i] * vectorB[i];
        }
        return similarity;
    }
    
    @Data
    @AllArgsConstructor
    public static class ContentEntry {
        private String id;
        private String content;
        private String language;
        private Embedding embedding;
    }
    
    @Data
    @AllArgsConstructor
    public static class MatchResult {
        private ContentEntry content;
        private double score;
    }
}
```

This comprehensive documentation provides a complete reference for the driftkit-embedding module, covering all major components, usage patterns, and extension points. The module offers a flexible and powerful abstraction for working with text embeddings across multiple providers while maintaining a consistent API.
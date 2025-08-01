# DriftKit Spring AI Embedding Integration

This module provides Spring AI integration for DriftKit's embedding services, allowing you to use Spring AI's embedding models within the DriftKit framework.

## Overview

The `driftkit-embedding-spring-ai` module implements the DriftKit `EmbeddingModel` interface using Spring AI's embedding capabilities. This allows seamless integration of various embedding providers through Spring AI's unified API.

## Supported Providers

- **OpenAI** - Including GPT embeddings (text-embedding-3-small, text-embedding-3-large, text-embedding-ada-002)
- **Azure OpenAI** - Microsoft's hosted OpenAI service
- **Ollama** - Local embedding models

## Installation

Add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>ai.driftkit</groupId>
    <artifactId>driftkit-embedding-spring-ai</artifactId>
    <version>${driftkit.version}</version>
</dependency>
```

## Configuration

### Basic Configuration

Configure the embedding service in your application properties or YAML:

```yaml
driftkit:
  embedding:
    name: spring-ai
    config:
      provider: openai  # Options: openai, azure-openai, ollama
      model-name: text-embedding-3-small
      api-key: ${OPENAI_API_KEY}
```

### Provider-Specific Configuration

#### OpenAI Configuration

```yaml
driftkit:
  embedding:
    name: spring-ai
    config:
      provider: openai
      model-name: text-embedding-3-small  # or text-embedding-3-large, text-embedding-ada-002
      api-key: ${OPENAI_API_KEY}
      host: https://api.openai.com  # Optional, defaults to OpenAI's API
```

#### Azure OpenAI Configuration

```yaml
driftkit:
  embedding:
    name: spring-ai
    config:
      provider: azure-openai
      api-key: ${AZURE_OPENAI_API_KEY}
      endpoint: https://your-resource.openai.azure.com/
      deployment-name: your-embedding-deployment
      api-version: 2024-02-01  # Optional
```

#### Ollama Configuration

```yaml
driftkit:
  embedding:
    name: spring-ai
    config:
      provider: ollama
      model-name: all-minilm  # or any other Ollama embedding model
      host: http://localhost:11434  # Optional, defaults to localhost
```

## Usage

### Basic Usage

```java
@Service
public class EmbeddingService {
    
    @Autowired
    private EmbeddingModel embeddingModel;
    
    public void embedText() {
        // Single text embedding
        TextSegment segment = TextSegment.from("Hello, world!");
        Response<Embedding> response = embeddingModel.embed(segment);
        Embedding embedding = response.content();
        
        // Access the embedding vector
        float[] vector = embedding.vector();
        
        // Get token usage
        TokenUsage usage = response.tokenUsage();
        int tokens = usage.inputTokenCount();
    }
    
    public void embedMultipleTexts() {
        // Multiple text embeddings
        List<TextSegment> segments = Arrays.asList(
            TextSegment.from("First text"),
            TextSegment.from("Second text"),
            TextSegment.from("Third text")
        );
        
        Response<List<Embedding>> response = embeddingModel.embedAll(segments);
        List<Embedding> embeddings = response.content();
    }
}
```

### With Spring Boot Auto-Configuration

If you're using the Spring Boot starter, the embedding model will be automatically configured:

```java
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}

@RestController
public class EmbeddingController {
    
    @Autowired
    private EmbeddingModel embeddingModel;
    
    @PostMapping("/embed")
    public float[] embed(@RequestBody String text) {
        TextSegment segment = TextSegment.from(text);
        Response<Embedding> response = embeddingModel.embed(segment);
        return response.content().vector();
    }
}
```

## Features

### Token Counting

The module provides token estimation using DriftKit's `SimpleTokenizer`:

```java
int estimatedTokens = embeddingModel.estimateTokenCount("Your text here");
```

### Error Handling

The module includes comprehensive error handling:

- **Configuration errors** - Clear messages for missing or invalid configuration
- **API errors** - Proper exception handling for API failures
- **Validation errors** - Input validation with helpful error messages

### Logging

Detailed logging is available at different levels:

- `INFO` - Configuration and successful operations
- `DEBUG` - Detailed operation information
- `WARN` - Skipped segments and recoverable issues
- `ERROR` - Failures and exceptions

## Performance Considerations

1. **Batch Processing** - Use `embedAll()` for multiple texts to reduce API calls
2. **Token Limits** - Be aware of model-specific token limits
3. **Rate Limiting** - Consider implementing rate limiting for production use
4. **Caching** - Consider caching embeddings for frequently used texts

## Troubleshooting

### Common Issues

1. **API Key Not Set**
   ```
   IllegalArgumentException: OpenAI API key is required
   ```
   Solution: Ensure your API key is properly configured

2. **Unsupported Provider**
   ```
   IllegalArgumentException: Unsupported Spring AI provider: xyz
   ```
   Solution: Use one of the supported providers: openai, azure-openai, ollama

3. **Model Not Found**
   ```
   RuntimeException: Failed to generate embeddings: Model not found
   ```
   Solution: Check that the model name is correct for your provider

### Debug Mode

Enable debug logging to see detailed information:

```yaml
logging:
  level:
    ai.driftkit.embedding.springai: DEBUG
    org.springframework.ai: DEBUG
```

## Spring AI Version Compatibility

This module is built with Spring AI 1.0.0-M4. Ensure your project uses compatible versions:

```xml
<properties>
    <spring-ai.version>1.0.0</spring-ai.version>
</properties>
```

## Contributing

When adding new providers:

1. Update `SpringAIEmbeddingModelFactory` with the new provider
2. Add provider-specific configuration handling
3. Update documentation with configuration examples
4. Add integration tests for the new provider

## License

This module is part of the DriftKit framework and follows the same license terms.
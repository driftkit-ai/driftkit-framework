# DriftKit RAG Module

High-level Fluent API for building Retrieval-Augmented Generation (RAG) and Content-Augmented Generation (CAG) pipelines in DriftKit.

## Overview

The DriftKit RAG module provides a clean, fluent API for:
- **Document Ingestion** - ETL pipeline for loading, splitting, embedding, and storing documents
- **Document Retrieval** - Search pipeline for finding relevant documents with optional reranking

## Key Features

- 🚀 **Fluent Builder Pattern** - Intuitive, chainable API
- 📁 **Multiple Document Sources** - File system, URLs, databases (extensible)
- ✂️ **Smart Text Splitting** - Character-based and semantic splitting strategies
- 🔍 **Flexible Retrieval** - Support for both text and embedding-based search
- 🎯 **Reranking** - Model-based reranking for improved relevance
- 🔄 **Streaming Support** - Memory-efficient processing of large document sets
- ⚡ **Virtual Threads** - Efficient concurrent processing (Java 21+)
- 🔧 **Extensible** - All components use interfaces for easy customization

## Quick Start

### 1. Document Ingestion

```java
// Simple ingestion from file system
IngestionPipeline pipeline = IngestionPipeline.builder()
    .documentLoader(FileSystemLoader.builder()
        .rootPath(Paths.get("./docs"))
        .parser(unifiedParser)
        .extensions(Set.of("txt", "md", "pdf", "jpg"))
        .build())
    .textSplitter(RecursiveCharacterTextSplitter.withChunkSize(512))
    .vectorStore(vectorStore)
    .indexName("knowledge-base")
    .build();

// Run ingestion with acknowledgment pattern
pipeline.run(result -> {
    if (result.isSuccess()) {
        log.info("Ingested: {} ({} chunks)", result.documentId(), result.chunksStored());
        // Acknowledge successful processing
    } else {
        log.error("Failed: {}", result.errors());
        // Handle errors, potentially retry
    }
});
```

### 2. Document Retrieval

```java
// Simple retrieval
RetrievalPipeline pipeline = RetrievalPipeline.builder()
    .vectorStore(vectorStore)
    .indexName("knowledge-base")
    .topK(10)
    .build();

List<Document> results = pipeline.retrieve("How to implement RAG?");
```

### 3. Advanced Retrieval with Reranking

```java
// Retrieval with model-based reranking
RetrievalPipeline pipeline = RetrievalPipeline.builder()
    .vectorStore(vectorStore)
    .indexName("knowledge-base")
    .reranker(ModelBasedReranker.builder()
        .modelClient(openAIClient)
        .promptService(promptService)
        .model("gpt-4o")
        .build())
    .topK(20)  // Retrieve more initially
    .minScore(0.5f)
    .build();

List<Document> results = pipeline.retrieve("Best practices for embeddings");
```

## Architecture

### Components

1. **DocumentLoader** - Load documents from various sources
   - `FileSystemLoader` - Load from local files (supports PDF, images via UnifiedParser)
   - `UrlLoader` - Load from web URLs
   - Custom implementations for databases, S3, etc.

2. **TextSplitter** - Split documents into chunks
   - `RecursiveCharacterTextSplitter` - Character-based with overlap
   - `SemanticTextSplitter` - Groups semantically similar sentences

3. **Retriever** - Custom retrieval strategies
   - `VectorStoreRetriever` - Default vector search implementation
   - Custom implementations for hybrid search, etc.

4. **Reranker** - Improve relevance of retrieved documents
   - `ModelBasedReranker` - Uses LLM with structured output
   - Custom implementations for cross-encoders, etc.

### Vector Store Support

The module works with both types of DriftKit vector stores:
- **TextVectorStore** - Handles embedding internally (simpler)
- **EmbeddingVectorStore** - Requires external embedding model (more control)

## Advanced Features

### Progress Tracking

```java
IngestionPipeline.ProgressListener listener = new IngestionPipeline.ProgressListener() {
    @Override
    public void onDocumentLoaded(String documentId, String source) {
        log.info("Loading: {}", source);
    }
    
    @Override
    public void onDocumentProcessed(String documentId, int chunks) {
        log.info("Processed: {} -> {} chunks", documentId, chunks);
    }
    
    @Override
    public void onChunkStored(String chunkId) {
        // Track individual chunk storage
    }
};

pipeline.run(listener);
```

### Streaming Processing

```java
// Process documents as a stream (memory efficient)
try (Stream<IngestionPipeline.DocumentResult> results = pipeline.run()) {
    results
        .filter(result -> result.isSuccess())
        .forEach(result -> {
            // Process each result
        });
}
```

### Retry and Error Handling

```java
IngestionPipeline pipeline = IngestionPipeline.builder()
    // ... other configuration
    .maxRetries(3)
    .retryDelayMs(1000)
    .useVirtualThreads(true)  // Efficient concurrency
    .build();
```

### Metadata Filtering

```java
RetrievalPipeline pipeline = RetrievalPipeline.builder()
    .vectorStore(vectorStore)
    .indexName("knowledge-base")
    .filters(Map.of(
        "contentType", "PDF",
        "department", "engineering"
    ))
    .build();
```

## Integration with DriftKit Ecosystem

- Uses existing `UnifiedParser` for multi-format document parsing
- Integrates with DriftKit embedding models
- Works with all DriftKit vector store implementations
- Compatible with Spring Boot auto-configuration
- Supports DriftKit's prompt management system

## Dependencies

```xml
<dependency>
    <groupId>ai.driftkit</groupId>
    <artifactId>driftkit-rag-core</artifactId>
    <version>${driftkit.version}</version>
</dependency>
```

For Spring Boot applications:
```xml
<dependency>
    <groupId>ai.driftkit</groupId>
    <artifactId>driftkit-rag-spring-boot-starter</artifactId>
    <version>${driftkit.version}</version>
</dependency>
```

## Requirements

- Java 21+ (for virtual threads support)
- DriftKit 0.8.7+

## Spring Boot Integration

The Spring Boot starter provides auto-configuration and convenient services:

### Configuration

```yaml
driftkit:
  rag:
    enabled: true
    default-index: knowledge-base
    
    splitter:
      type: recursive  # or 'semantic'
      chunk-size: 512
      chunk-overlap: 128
    
    reranker:
      enabled: true
      model: gpt-4o
      temperature: 0.0
    
    retriever:
      default-top-k: 10
      default-min-score: 0.0
```

### Using RagService

```java
@RestController
@RequiredArgsConstructor
public class DocumentController {
    
    private final RagService ragService;
    private final DocumentLoaderFactory loaderFactory;
    
    @PostMapping("/ingest/urls")
    public void ingestUrls(@RequestBody List<String> urls) {
        try (Stream<DocumentResult> results = ragService.ingestFromUrls(urls, "web-docs")) {
            results.forEach(result -> {
                if (result.isSuccess()) {
                    log.info("Ingested: {}", result.documentId());
                }
            });
        }
    }
    
    @GetMapping("/search")
    public List<Document> search(@RequestParam String query) {
        return ragService.retrieve(query);
    }
}
```

### Dynamic Document Loading

Create loaders at runtime with the factory:

```java
// Load from different sources dynamically
DocumentLoader urlLoader = loaderFactory.urlLoader(urls);
DocumentLoader fileLoader = loaderFactory.fileSystemLoader("/path/to/docs");

// Combine multiple loaders
DocumentLoader combined = loaderFactory.compositeLoader(urlLoader, fileLoader);

// Use with custom pipeline
ragService.ingest(combined, "my-index");
```

## Future Enhancements

- [ ] Hybrid search support (keyword + semantic)
- [ ] Incremental indexing
- [ ] Document update detection
- [ ] Caching layer for retrieval
- [ ] More reranker implementations
- [ ] Batch API for large-scale operations
- [ ] Streaming ingestion API
- [ ] WebSocket support for real-time progress
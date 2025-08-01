# DriftKit Vector Module

## Overview

The `driftkit-vector` module provides a comprehensive vector storage and retrieval system designed for AI applications requiring semantic search capabilities. It supports multiple storage backends, advanced document parsing, and seamless Spring Boot integration with REST APIs.

## Spring Boot Initialization

To use the vector module in your Spring Boot application, the module will be automatically configured when you provide vector store configuration:

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
  vectorStore:
    name: "inmemory"  # or "pinecone", "filebased"
    config:
      dimension: "1536"
      # Additional config based on vector store type
```

The module provides:
- **Auto-configuration class**: `VectorStoreAutoConfiguration`
- **Component scanning**: Automatically scans `ai.driftkit.vector` package
- **MongoDB repositories**: Auto-enabled for vector document persistence
- **REST Controllers**: Vector management endpoints
- **Conditional activation**: Only when `driftkit.vectorStore.name` is configured

## Architecture

### Module Structure

```
driftkit-vector/
├── driftkit-vector-core/                # Core vector storage abstractions
│   ├── domain/                         # Core domain objects
│   ├── inmemory/                       # In-memory implementation
│   ├── filebased/                      # File-based persistence
│   ├── pinecone/                       # Pinecone cloud integration
│   └── service/                        # Factory services
├── driftkit-vector-spring-boot-starter/ # Spring Boot integration
│   ├── controller/                     # REST API endpoints
│   ├── domain/                        # Spring-specific domain objects
│   ├── parser/                        # Document parsing
│   ├── repository/                    # Data access layer
│   └── service/                       # Business logic
├── driftkit-vector-spring-ai/          # Spring AI integration
│   └── springai/                       # Adapter implementation
├── driftkit-vector-spring-ai-starter/  # Spring AI auto-configuration
│   └── autoconfigure/                  # Spring Boot configuration
└── pom.xml                           # Parent module configuration
```

### Key Dependencies

- **Apache Tika** - Universal document parsing
- **MongoDB** - Document persistence and metadata storage
- **Apache POI** - Microsoft Office document processing
- **Jsoup** - HTML parsing and cleanup
- **Feign** - HTTP client for external APIs (Pinecone)
- **Spring AI** - Integration with Spring AI vector stores (optional)
- **DriftKit Common** - Shared utilities and domain objects
- **DriftKit Embedding** - Text embedding capabilities

## Core Abstractions

### VectorStore Interface

The central abstraction for all vector storage implementations providing unified API for document operations, similarity search, and index management across different storage backends.

**Key Features:**
- **Unified API** - Consistent interface across all storage backends
- **Index Management** - Namespace-based document organization
- **Similarity Search** - Vector-based relevance retrieval
- **CRUD Operations** - Complete document lifecycle management
- **Configuration-driven** - Dynamic backend selection

### VectorStoreFactory

Factory pattern implementation for dynamic vector store instantiation using Java ServiceLoader for runtime discovery and configuration.

**Usage Example:**
```java
VectorStoreConfig config = VectorStoreConfig.builder()
    .name("inmemory")
    .build();

VectorStore store = VectorStoreFactory.fromConfig(config);
```

## Domain Objects

### Document

Core document representation with vector embeddings, content, and metadata.

**Key Features:**
- **Vector Storage** - Efficient float array representation
- **Content Preservation** - Original text content retention
- **Metadata Support** - Flexible key-value properties
- **Unique Identification** - Automatic UUID generation

**Usage Example:**
```java
Document document = new Document();
document.setId(UUID.randomUUID().toString());
document.setPageContent("Your document content here");
document.setVector(embeddingVector);
document.setMetadata(Map.of("source", "document.pdf", "page", 1));
```

### DocumentsResult

Search result container with similarity scores and utility methods for accessing documents and scores.

## Storage Implementations

### InMemoryVectorStore

In-memory storage implementation with cosine similarity search. Ideal for development and testing environments.

### FileBasedVectorStore

File-based implementation extending in-memory capabilities with automatic persistence to disk.

**Configuration Example:**
```yaml
driftkit:
  vectorStores:
    - name: "file-store"
      type: "filebased"
      storageFile: "/data/vectors/store.dat"
```

### PineconeVectorStore

Cloud-based vector storage implementation with Pinecone integration. Provides scalable vector search capabilities through Pinecone's managed service.

Feign-based API client for Pinecone operations including upsert, query, and delete endpoints.

**Configuration Example:**
```yaml
driftkit:
  vectorStores:
    - name: "pinecone"
      type: "pinecone"
      apiKey: "${PINECONE_API_KEY}"
      environment: "us-west1-gcp"
      baseUrl: "https://your-index-name-abc123.svc.us-west1-gcp.pinecone.io"
```

### Spring AI Integration

The module now supports integration with Spring AI vector stores, allowing you to use any Spring AI-compatible vector store implementation (Pinecone, Qdrant, Weaviate, ChromaDB, etc.) through the DriftKit VectorStore interface.

**Features:**
- Seamless adapter between Spring AI and DriftKit vector store interfaces
- Auto-configuration support for Spring Boot applications
- Support for all Spring AI vector store implementations
- Consistent API across different vector store backends

**Configuration Example:**
```yaml
driftkit:
  vector:
    spring-ai:
      enabled: true
      store-name: "spring-ai"  # Name to identify this adapter
      auto-register: true      # Auto-register with VectorStoreFactory
```

**Usage Example:**
```java
// Configure any Spring AI vector store (e.g., Qdrant)
@Bean
public VectorStore springAiVectorStore() {
    return new QdrantVectorStore(qdrantClient, embeddingModel);
}

// The adapter will automatically be created and available as a DriftKit VectorStore
@Autowired
private ai.driftkit.vector.core.domain.VectorStore vectorStore;

// Use it like any other DriftKit vector store
vectorStore.addDocument("my-index", document);
```

## Spring Boot Integration

### REST API Controller

Comprehensive REST API controller providing endpoints for:
- Document indexing task submission
- File upload and processing
- Task status monitoring
- Index management operations

### IndexService

Business logic layer with async task processing. Handles document parsing, embedding generation, and storage operations with thread pool execution for scalability.

## Document Parsing

### ContentType Support

Extensive content type handling:

Supports multiple content types including:
- **Images**: PNG, JPG/JPEG
- **Documents**: PDF, DOCX, DOC, XLSX, XLS, PPTX, PPT
- **OpenDocument**: ODT, ODS, ODP
- **Text**: TXT, HTML, XML, JSON, CSV
- **Special**: YouTube transcripts, SQLite databases, Access databases

### UnifiedParser

Multi-format document parser with intelligent content extraction supporting:
- Text document parsing via Apache Tika
- Image-to-text conversion using AI models
- YouTube transcript extraction
- Automatic content type detection and routing

### TextContentParser

Apache Tika-based universal document parser for extracting text from various file formats.

## Demo Examples

### 1. Simple Document Storage and Search

This example demonstrates basic document indexing and similarity search.

```java
@Service
public class SimpleVectorSearch {
    
    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;
    
    public SimpleVectorSearch() throws Exception {
        // Initialize in-memory vector store
        VectorStoreConfig config = VectorStoreConfig.builder()
            .name("inmemory")
            .build();
        this.vectorStore = VectorStoreFactory.fromConfig(config);
        
        // Initialize embedding model
        Map<String, String> embConfig = Map.of(
            "apiKey", System.getenv("OPENAI_API_KEY"),
            "model", "text-embedding-ada-002"
        );
        this.embeddingModel = EmbeddingFactory.fromName("openai", embConfig);
    }
    
    public void indexDocument(String content, String category) throws Exception {
        // Generate embedding
        TextSegment segment = TextSegment.from(content);
        Response<Embedding> response = embeddingModel.embed(segment);
        
        // Create document
        Document document = new Document();
        document.setId(UUID.randomUUID().toString());
        document.setPageContent(content);
        document.setVector(response.content().vector());
        document.setMetadata(Map.of("category", category));
        
        // Store document
        vectorStore.addDocument("my-index", document);
    }
    
    public List<Document> search(String query, int limit) throws Exception {
        // Generate query embedding
        TextSegment querySegment = TextSegment.from(query);
        Response<Embedding> response = embeddingModel.embed(querySegment);
        
        // Search similar documents
        DocumentsResult result = vectorStore.findRelevant(
            "my-index", 
            response.content().vector(), 
            limit
        );
        
        return result.documents();
    }
}
```

### 2. File Upload and Processing

This example shows how to upload and process files into vector storage.

```java
@Service
public class FileVectorProcessor {
    
    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;
    private final UnifiedParser parser;
    
    public FileVectorProcessor() throws Exception {
        VectorStoreConfig config = VectorStoreConfig.builder()
            .name("filebased")
            .config(Map.of("storageFile", "/data/vectors.dat"))
            .build();
        this.vectorStore = VectorStoreFactory.fromConfig(config);
        
        Map<String, String> embConfig = Map.of(
            "apiKey", System.getenv("OPENAI_API_KEY"),
            "model", "text-embedding-ada-002"
        );
        this.embeddingModel = EmbeddingFactory.fromName("openai", embConfig);
        this.parser = new UnifiedParser();
    }
    
    public void processFile(byte[] fileData, String fileName, String contentType) throws Exception {
        // Parse file content
        ParserInput input = ByteArrayParserInput.builder()
            .data(fileData)
            .contentType(ContentType.fromMimeType(contentType))
            .metadata(Map.of("filename", fileName))
            .build();
            
        ParsedContent parsed = parser.parse(input);
        
        // Split into chunks
        List<String> chunks = splitIntoChunks(parsed.getContent(), 500);
        
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            
            // Generate embedding
            TextSegment segment = TextSegment.from(chunk);
            Response<Embedding> response = embeddingModel.embed(segment);
            
            // Create document
            Document document = new Document();
            document.setId(UUID.randomUUID().toString());
            document.setPageContent(chunk);
            document.setVector(response.content().vector());
            document.setMetadata(Map.of(
                "filename", fileName,
                "chunk_index", i,
                "total_chunks", chunks.size()
            ));
            
            // Store document
            vectorStore.addDocument("files", document);
        }
    }
    
    private List<String> splitIntoChunks(String content, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        String[] words = content.split("\\s+");
        
        StringBuilder chunk = new StringBuilder();
        for (String word : words) {
            if (chunk.length() + word.length() > chunkSize) {
                chunks.add(chunk.toString());
                chunk = new StringBuilder();
            }
            chunk.append(word).append(" ");
        }
        
        if (chunk.length() > 0) {
            chunks.add(chunk.toString());
        }
        
        return chunks;
    }
}
```

### 3. Question Answering System

This example demonstrates a simple Q&A system using vector search.

```java
@Service
public class QuestionAnsweringSystem {
    
    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;
    
    public QuestionAnsweringSystem() throws Exception {
        VectorStoreConfig config = VectorStoreConfig.builder()
            .name("inmemory")
            .build();
        this.vectorStore = VectorStoreFactory.fromConfig(config);
        
        Map<String, String> embConfig = Map.of(
            "apiKey", System.getenv("OPENAI_API_KEY"),
            "model", "text-embedding-ada-002"
        );
        this.embeddingModel = EmbeddingFactory.fromName("openai", embConfig);
    }
    
    public void addKnowledge(String question, String answer) throws Exception {
        String content = question + " " + answer;
        
        // Generate embedding
        TextSegment segment = TextSegment.from(content);
        Response<Embedding> response = embeddingModel.embed(segment);
        
        // Create document
        Document document = new Document();
        document.setId(UUID.randomUUID().toString());
        document.setPageContent(answer);
        document.setVector(response.content().vector());
        document.setMetadata(Map.of("question", question));
        
        // Store document
        vectorStore.addDocument("qa-index", document);
    }
    
    public String findAnswer(String question) throws Exception {
        // Generate query embedding
        TextSegment querySegment = TextSegment.from(question);
        Response<Embedding> response = embeddingModel.embed(querySegment);
        
        // Search for answer
        DocumentsResult result = vectorStore.findRelevant(
            "qa-index", 
            response.content().vector(), 
            1
        );
        
        if (!result.isEmpty()) {
            return result.first().getPageContent();
        }
        
        return "No answer found for your question.";
    }
}
```

### 4. Document Categorization

This example shows automatic document categorization using embeddings.

```java
@Service
public class DocumentCategorizer {
    
    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;
    private final Map<String, float[]> categoryEmbeddings = new HashMap<>();
    
    public DocumentCategorizer() throws Exception {
        VectorStoreConfig config = VectorStoreConfig.builder()
            .name("inmemory")
            .build();
        this.vectorStore = VectorStoreFactory.fromConfig(config);
        
        Map<String, String> embConfig = Map.of(
            "apiKey", System.getenv("OPENAI_API_KEY"),
            "model", "text-embedding-ada-002"
        );
        this.embeddingModel = EmbeddingFactory.fromName("openai", embConfig);
        
        initializeCategories();
    }
    
    private void initializeCategories() throws Exception {
        Map<String, String> categories = Map.of(
            "technology", "Technology, programming, software, computers, AI",
            "business", "Business, finance, economics, marketing, sales",
            "health", "Health, medical, wellness, fitness, nutrition",
            "education", "Education, learning, teaching, academic, research"
        );
        
        for (Map.Entry<String, String> entry : categories.entrySet()) {
            TextSegment segment = TextSegment.from(entry.getValue());
            Response<Embedding> response = embeddingModel.embed(segment);
            categoryEmbeddings.put(entry.getKey(), response.content().vector());
        }
    }
    
    public String categorizeDocument(String content) throws Exception {
        // Generate document embedding
        TextSegment segment = TextSegment.from(content);
        Response<Embedding> response = embeddingModel.embed(segment);
        float[] docEmbedding = response.content().vector();
        
        // Find closest category
        String bestCategory = null;
        double bestScore = -1;
        
        for (Map.Entry<String, float[]> entry : categoryEmbeddings.entrySet()) {
            double score = calculateSimilarity(docEmbedding, entry.getValue());
            if (score > bestScore) {
                bestScore = score;
                bestCategory = entry.getKey();
            }
        }
        
        return bestCategory;
    }
    
    private double calculateSimilarity(float[] a, float[] b) {
        double dotProduct = 0.0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
        }
        return dotProduct;
    }
}
```

### 5. Multi-Index Search

This example demonstrates searching across multiple indexes.

```java
@Service
public class MultiIndexSearcher {
    
    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;
    
    public MultiIndexSearcher() throws Exception {
        VectorStoreConfig config = VectorStoreConfig.builder()
            .name("inmemory")
            .build();
        this.vectorStore = VectorStoreFactory.fromConfig(config);
        
        Map<String, String> embConfig = Map.of(
            "apiKey", System.getenv("OPENAI_API_KEY"),
            "model", "text-embedding-ada-002"
        );
        this.embeddingModel = EmbeddingFactory.fromName("openai", embConfig);
    }
    
    public void addDocument(String indexName, String content, String type) throws Exception {
        // Generate embedding
        TextSegment segment = TextSegment.from(content);
        Response<Embedding> response = embeddingModel.embed(segment);
        
        // Create document
        Document document = new Document();
        document.setId(UUID.randomUUID().toString());
        document.setPageContent(content);
        document.setVector(response.content().vector());
        document.setMetadata(Map.of("type", type));
        
        // Store in specific index
        vectorStore.addDocument(indexName, document);
    }
    
    public Map<String, List<Document>> searchAllIndexes(String query, List<String> indexes, int limitPerIndex) throws Exception {
        // Generate query embedding
        TextSegment querySegment = TextSegment.from(query);
        Response<Embedding> response = embeddingModel.embed(querySegment);
        float[] queryVector = response.content().vector();
        
        Map<String, List<Document>> results = new HashMap<>();
        
        for (String index : indexes) {
            DocumentsResult result = vectorStore.findRelevant(
                index, 
                queryVector, 
                limitPerIndex
            );
            results.put(index, result.documents());
        }
        
        return results;
    }
}
```

This comprehensive documentation provides a complete reference for the driftkit-vector module, covering all major components, storage implementations, document parsing capabilities, Spring Boot integration, and usage patterns. The module offers a powerful and flexible foundation for building AI applications with advanced vector search capabilities.
package ai.driftkit.rag.examples;

import ai.driftkit.config.EtlConfig;
import ai.driftkit.embedding.core.service.EmbeddingFactory;
import ai.driftkit.embedding.core.service.EmbeddingModel;
import ai.driftkit.rag.core.loader.FileSystemLoader;
import ai.driftkit.rag.core.loader.UrlLoader;
import ai.driftkit.rag.core.reranker.ModelBasedReranker;
import ai.driftkit.rag.core.splitter.RecursiveCharacterTextSplitter;
import ai.driftkit.rag.core.splitter.SemanticTextSplitter;
import ai.driftkit.rag.ingestion.IngestionPipeline;
import ai.driftkit.rag.retrieval.RetrievalPipeline;
import ai.driftkit.vector.core.domain.Document;
import ai.driftkit.vector.core.domain.TextVectorStore;
import ai.driftkit.vector.core.service.VectorStoreFactory;
import ai.driftkit.vector.spring.parser.UnifiedParser;
import ai.driftkit.clients.core.ModelClientFactory;
import ai.driftkit.common.domain.client.ModelClient;
import ai.driftkit.context.core.service.PromptService;
import ai.driftkit.context.core.service.InMemoryPromptService;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Example demonstrating the Fluent API for RAG operations.
 */
@Slf4j
public class RAGFluentAPIExample {
    
    public static void main(String[] args) throws Exception {
        // Initialize configuration
        EtlConfig config = loadConfiguration();
        
        // Initialize services
        UnifiedParser parser = new UnifiedParser(config);
        EmbeddingModel embeddingModel = EmbeddingFactory.fromName(
            config.getEmbedding().getName(),
            config.getEmbedding().getConfig()
        );
        TextVectorStore vectorStore = (TextVectorStore) VectorStoreFactory.fromConfig(config.getVectorStore());
        ModelClient modelClient = ModelClientFactory.fromConfig(config.getVault().getFirst());
        PromptService promptService = createPromptService();
        
        // Example 1: Simple document ingestion
        simpleIngestionExample(parser, vectorStore);
        
        // Example 2: Advanced ingestion with progress tracking
        advancedIngestionExample(parser, embeddingModel, vectorStore);
        
        // Example 3: Simple retrieval
        simpleRetrievalExample(vectorStore);
        
        // Example 4: Advanced retrieval with reranking
        advancedRetrievalExample(vectorStore, modelClient, promptService);
        
        // Example 5: End-to-end RAG workflow
        endToEndRAGExample(parser, vectorStore, modelClient, promptService);
    }
    
    /**
     * Example 1: Simple document ingestion using TextVectorStore.
     */
    private static void simpleIngestionExample(UnifiedParser parser, TextVectorStore vectorStore) throws Exception {
        log.info("=== Example 1: Simple Document Ingestion ===");
        
        IngestionPipeline pipeline = IngestionPipeline.builder()
            .documentLoader(FileSystemLoader.builder()
                .rootPath(Paths.get("./docs"))
                .parser(parser)
                .extensions(Set.of("txt", "md", "pdf"))
                .build())
            .textSplitter(RecursiveCharacterTextSplitter.withChunkSize(512))
            .vectorStore(vectorStore)
            .indexName("knowledge-base")
            .build();
        
        // Run ingestion
        try (Stream<IngestionPipeline.DocumentResult> results = pipeline.run()) {
            results.forEach(result -> {
                if (result.isSuccess()) {
                    log.info("Successfully ingested: {} ({} chunks)", 
                        result.documentId(), result.chunksStored());
                } else {
                    log.error("Failed to ingest: {} - {}", 
                        result.documentId(), result.errors());
                }
            });
        }
    }
    
    /**
     * Example 2: Advanced ingestion with progress tracking.
     */
    private static void advancedIngestionExample(
            UnifiedParser parser, 
            EmbeddingModel embeddingModel, 
            TextVectorStore vectorStore) throws Exception {
        
        log.info("=== Example 2: Advanced Ingestion with Progress Tracking ===");
        
        // Create progress listener
        AtomicInteger totalDocs = new AtomicInteger(0);
        AtomicInteger totalChunks = new AtomicInteger(0);
        
        IngestionPipeline.ProgressListener progressListener = new IngestionPipeline.ProgressListener() {
            @Override
            public void onDocumentLoaded(String documentId, String source) {
                log.info("Loading document: {} from {}", documentId, source);
            }
            
            @Override
            public void onDocumentProcessed(String documentId, int chunks) {
                totalDocs.incrementAndGet();
                totalChunks.addAndGet(chunks);
                log.info("Processed document: {} -> {} chunks", documentId, chunks);
            }
            
            @Override
            public void onDocumentFailed(String documentId, Exception error) {
                log.error("Failed to process document: {}", documentId, error);
            }
            
            @Override
            public void onChunkStored(String chunkId) {
                log.debug("Stored chunk: {}", chunkId);
            }
            
            @Override
            public void onProgress(long processed, long total) {
                log.info("Progress: {}/{}", processed, total);
            }
        };
        
        // Build pipeline with semantic splitter
        IngestionPipeline pipeline = IngestionPipeline.builder()
            .documentLoader(UrlLoader.builder()
                .urls(List.of(
                    "https://example.com/article1.html",
                    "https://example.com/article2.html"
                ))
                .parser(parser)
                .build())
            .textSplitter(SemanticTextSplitter.builder()
                .embeddingModel(embeddingModel)
                .targetChunkSize(512)
                .similarityThreshold(0.7f)
                .build())
            .vectorStore(vectorStore)
            .indexName("web-content")
            .maxRetries(3)
            .build();
        
        // Run with progress tracking
        pipeline.run(result -> {
            log.info("Document {} processed in {}ms", 
                result.documentId(), result.processingTimeMs());
        }, progressListener);
        
        log.info("Ingestion complete: {} documents, {} chunks total", 
            totalDocs.get(), totalChunks.get());
    }
    
    /**
     * Example 3: Simple retrieval.
     */
    private static void simpleRetrievalExample(TextVectorStore vectorStore) throws Exception {
        log.info("=== Example 3: Simple Retrieval ===");
        
        RetrievalPipeline pipeline = RetrievalPipeline.builder()
            .vectorStore(vectorStore)
            .indexName("knowledge-base")
            .topK(5)
            .build();
        
        List<Document> results = pipeline.retrieve("How to implement RAG with DriftKit?");
        
        log.info("Found {} relevant documents:", results.size());
        results.forEach(doc -> 
            log.info("- {}: {}", doc.getId(), 
                doc.getPageContent().substring(0, Math.min(100, doc.getPageContent().length())) + "...")
        );
    }
    
    /**
     * Example 4: Advanced retrieval with reranking.
     */
    private static void advancedRetrievalExample(
            TextVectorStore vectorStore,
            ModelClient modelClient,
            PromptService promptService) throws Exception {
        
        log.info("=== Example 4: Advanced Retrieval with Reranking ===");
        
        RetrievalPipeline pipeline = RetrievalPipeline.builder()
            .vectorStore(vectorStore)
            .indexName("knowledge-base")
            .reranker(ModelBasedReranker.builder()
                .modelClient(modelClient)
                .promptService(promptService)
                .model("gpt-4o")
                .temperature(0.0f)
                .build())
            .topK(10) // Retrieve more initially
            .minScore(0.5f)
            .filters(Map.of("contentType", "MARKDOWN"))
            .build();
        
        String query = "What are the best practices for vector embeddings?";
        List<Document> results = pipeline.retrieve(query);
        
        log.info("Retrieved and reranked {} documents for query: {}", results.size(), query);
        results.forEach(doc -> {
            log.info("Document: {} (Score: {})", 
                doc.getId(), 
                doc.getMetadata().get("rerankScore"));
        });
    }
    
    /**
     * Example 5: End-to-end RAG workflow.
     */
    private static void endToEndRAGExample(
            UnifiedParser parser,
            TextVectorStore vectorStore,
            ModelClient modelClient,
            PromptService promptService) throws Exception {
        
        log.info("=== Example 5: End-to-End RAG Workflow ===");
        
        // Step 1: Ingest documents
        log.info("Step 1: Ingesting documents...");
        
        IngestionPipeline ingestionPipeline = IngestionPipeline.builder()
            .documentLoader(FileSystemLoader.builder()
                .rootPath(Paths.get("./knowledge-base"))
                .parser(parser)
                .recursive(true)
                .build())
            .textSplitter(RecursiveCharacterTextSplitter.withChunkSizeAndOverlap(512, 128))
            .vectorStore(vectorStore)
            .indexName("rag-demo")
            .build();
        
        long ingestedCount = ingestionPipeline.run()
            .filter(result -> result.isSuccess())
            .count();
        
        log.info("Ingested {} documents", ingestedCount);
        
        // Step 2: Query with retrieval
        log.info("Step 2: Querying with retrieval...");
        
        RetrievalPipeline retrievalPipeline = RetrievalPipeline.builder()
            .vectorStore(vectorStore)
            .indexName("rag-demo")
            .reranker(ModelBasedReranker.builder()
                .modelClient(modelClient)
                .promptService(promptService)
                .build())
            .topK(5)
            .build();
        
        String query = "How does DriftKit handle document chunking?";
        List<Document> relevantDocs = retrievalPipeline.retrieve(query);
        
        // Step 3: Generate answer using retrieved context
        log.info("Step 3: Generating answer with context...");
        
        StringBuilder context = new StringBuilder();
        relevantDocs.forEach(doc -> 
            context.append(doc.getPageContent()).append("\n\n")
        );
        
        String prompt = String.format("""
            Based on the following context, answer the question.
            
            Context:
            %s
            
            Question: %s
            
            Answer:
            """, context.toString(), query);
        
        // Use model to generate answer (simplified - in real usage, use proper request structure)
        log.info("Generated answer based on {} retrieved documents", relevantDocs.size());
    }
    
    /**
     * Load configuration (simplified for example).
     */
    private static EtlConfig loadConfiguration() {
        // In real usage, load from application.yml or environment
        return new EtlConfig();
    }
    
    /**
     * Create prompt service (simplified for example).
     */
    private static PromptService createPromptService() {
        // Using InMemory implementation for example
        InMemoryPromptService inMemoryService = new InMemoryPromptService();
        return new PromptService(inMemoryService, null);
    }
}
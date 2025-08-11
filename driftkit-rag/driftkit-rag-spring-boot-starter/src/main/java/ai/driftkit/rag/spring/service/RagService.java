package ai.driftkit.rag.spring.service;

import ai.driftkit.embedding.core.service.EmbeddingModel;
import ai.driftkit.rag.core.loader.DocumentLoader;
import ai.driftkit.rag.core.reranker.ModelBasedReranker;
import ai.driftkit.rag.core.retriever.Retriever;
import ai.driftkit.rag.core.retriever.VectorStoreRetriever;
import ai.driftkit.rag.core.splitter.RecursiveCharacterTextSplitter;
import ai.driftkit.rag.core.splitter.TextSplitter;
import ai.driftkit.rag.ingestion.IngestionPipeline;
import ai.driftkit.rag.retrieval.RetrievalPipeline;
import ai.driftkit.rag.spring.autoconfigure.RagProperties;
import ai.driftkit.vector.core.domain.BaseVectorStore;
import ai.driftkit.vector.core.domain.Document;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Main service for RAG operations.
 * Provides convenient methods for document ingestion and retrieval.
 */
@Slf4j
@RequiredArgsConstructor
public class RagService {
    
    private final RagProperties properties;
    private final DocumentLoaderFactory loaderFactory;
    private final BaseVectorStore vectorStore;
    private final TextSplitter textSplitter;
    private final EmbeddingModel embeddingModel;
    private final VectorStoreRetriever retriever;
    private final ModelBasedReranker reranker;
    
    /**
     * Ingest documents from file system.
     */
    public Stream<IngestionPipeline.DocumentResult> ingestFromFileSystem(
            String path, 
            String indexName) {
        
        return ingestFromFileSystem(Path.of(path), indexName);
    }
    
    /**
     * Ingest documents from file system with custom configuration.
     */
    public Stream<IngestionPipeline.DocumentResult> ingestFromFileSystem(
            Path path,
            String indexName) {
        
        if (StringUtils.isEmpty(indexName)) {
            indexName = properties.getDefaultIndex();
        }
        
        DocumentLoader loader = loaderFactory.fileSystemLoader(path);
        return ingest(loader, indexName);
    }
    
    /**
     * Ingest documents from URLs.
     */
    public Stream<IngestionPipeline.DocumentResult> ingestFromUrls(
            List<String> urls,
            String indexName) {
        
        if (StringUtils.isEmpty(indexName)) {
            indexName = properties.getDefaultIndex();
        }
        
        DocumentLoader loader = loaderFactory.urlLoader(urls);
        return ingest(loader, indexName);
    }
    
    /**
     * Ingest documents using a custom loader.
     */
    public Stream<IngestionPipeline.DocumentResult> ingest(
            DocumentLoader loader,
            String indexName) {
        
        log.info("Starting ingestion into index: {}", indexName);
        
        IngestionPipeline pipeline = IngestionPipeline.builder()
            .documentLoader(loader)
            .textSplitter(textSplitter)
            .embeddingClient(embeddingModel)
            .vectorStore(vectorStore)
            .indexName(indexName)
            .maxRetries(properties.getIngestion().getMaxRetries())
            .retryDelayMs(properties.getIngestion().getRetryDelayMs())
            .useVirtualThreads(properties.getIngestion().isUseVirtualThreads())
            .build();
        
        return pipeline.run();
    }
    
    /**
     * Retrieve documents with default configuration.
     */
    public List<Document> retrieve(String query) {
        return retrieve(query, properties.getDefaultIndex());
    }
    
    /**
     * Retrieve documents from specific index.
     */
    public List<Document> retrieve(String query, String indexName) {
        return retrieve(
            query, 
            indexName, 
            properties.getRetriever().getDefaultTopK(),
            properties.getRetriever().getDefaultMinScore()
        );
    }
    
    /**
     * Retrieve documents with custom configuration.
     */
    public List<Document> retrieve(
            String query,
            String indexName,
            int topK,
            float minScore) {
        
        return retrieve(query, indexName, topK, minScore, Map.of());
    }
    
    /**
     * Retrieve documents with full configuration.
     */
    public List<Document> retrieve(
            String query,
            String indexName,
            int topK,
            float minScore,
            Map<String, Object> filters) {
        
        if (StringUtils.isEmpty(query)) {
            log.warn("Empty query provided");
            return List.of();
        }
        
        if (StringUtils.isEmpty(indexName)) {
            indexName = properties.getDefaultIndex();
        }
        
        log.debug("Retrieving documents for query: {} from index: {}", query, indexName);
        
        try {
            RetrievalPipeline.RetrievalPipelineBuilder builder = RetrievalPipeline.builder()
                .vectorStore(vectorStore)
                .embeddingClient(embeddingModel)
                .indexName(indexName)
                .topK(topK)
                .minScore(minScore)
                .filters(filters)
                .queryPrefix(properties.getRetriever().getQueryPrefix())
                .useVirtualThreads(properties.getIngestion().isUseVirtualThreads());
            
            // Add optional components if available
            if (retriever != null) {
                builder.retriever(retriever);
            }
            
            if (reranker != null && properties.getReranker().isEnabled()) {
                builder.reranker(reranker);
            }
            
            RetrievalPipeline pipeline = builder.build();
            return pipeline.retrieve(query);
            
        } catch (Exception e) {
            log.error("Failed to retrieve documents for query: {}", query, e);
            return List.of();
        }
    }
    
    /**
     * Create a custom ingestion pipeline builder.
     */
    public IngestionPipeline.IngestionPipelineBuilder ingestionBuilder() {
        return IngestionPipeline.builder()
            .vectorStore(vectorStore)
            .embeddingClient(embeddingModel)
            .textSplitter(textSplitter)
            .maxRetries(properties.getIngestion().getMaxRetries())
            .retryDelayMs(properties.getIngestion().getRetryDelayMs())
            .useVirtualThreads(properties.getIngestion().isUseVirtualThreads());
    }
    
    /**
     * Create a custom retrieval pipeline builder.
     */
    public RetrievalPipeline.RetrievalPipelineBuilder retrievalBuilder() {
        RetrievalPipeline.RetrievalPipelineBuilder builder = RetrievalPipeline.builder()
            .vectorStore(vectorStore)
            .embeddingClient(embeddingModel)
            .topK(properties.getRetriever().getDefaultTopK())
            .minScore(properties.getRetriever().getDefaultMinScore())
            .queryPrefix(properties.getRetriever().getQueryPrefix())
            .useVirtualThreads(properties.getIngestion().isUseVirtualThreads());
        
        if (retriever != null) {
            builder.retriever(retriever);
        }
        
        if (reranker != null && properties.getReranker().isEnabled()) {
            builder.reranker(reranker);
        }
        
        return builder;
    }
}
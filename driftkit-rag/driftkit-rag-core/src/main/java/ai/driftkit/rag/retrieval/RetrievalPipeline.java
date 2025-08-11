package ai.driftkit.rag.retrieval;

import ai.driftkit.embedding.core.domain.Embedding;
import ai.driftkit.embedding.core.domain.Response;
import ai.driftkit.embedding.core.domain.TextSegment;
import ai.driftkit.embedding.core.service.EmbeddingModel;
import ai.driftkit.rag.core.reranker.Reranker;
import ai.driftkit.rag.core.reranker.Reranker.RerankConfig;
import ai.driftkit.rag.core.retriever.Retriever;
import ai.driftkit.rag.core.retriever.Retriever.RetrievalConfig;
import ai.driftkit.rag.core.retriever.Retriever.RetrievalResult;
import ai.driftkit.vector.core.domain.BaseVectorStore;
import ai.driftkit.vector.core.domain.Document;
import ai.driftkit.vector.core.domain.DocumentsResult;
import ai.driftkit.vector.core.domain.EmbeddingVectorStore;
import ai.driftkit.vector.core.domain.TextVectorStore;
import lombok.Builder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Pipeline for retrieving relevant documents from a vector store.
 * Supports optional custom retrieval strategies and reranking.
 */
@Slf4j
@Builder
@RequiredArgsConstructor
public class RetrievalPipeline {
    
    private final EmbeddingModel embeddingClient; // Optional - only needed for EmbeddingVectorStore
    
    @NonNull
    private final BaseVectorStore vectorStore;
    
    @NonNull
    @Builder.Default
    private final String indexName = "default";
    
    private final Retriever retriever; // Optional - custom retrieval strategy
    
    private final Reranker reranker; // Optional - reranking model
    
    @Builder.Default
    private final int topK = 10;
    
    @Builder.Default
    private final float minScore = 0.0f;
    
    @Builder.Default
    private final Map<String, Object> filters = Map.of();
    
    @Builder.Default
    private final boolean useVirtualThreads = true;
    
    @Builder.Default
    private final String queryPrefix = ""; // Optional prefix for queries (e.g., "Instruct: Retrieve semantically similar text.\nQuery: ")
    
    /**
     * Validate configuration on build.
     */
    private static void validate(RetrievalPipeline pipeline) {
        if (pipeline.vectorStore instanceof EmbeddingVectorStore && 
            pipeline.embeddingClient == null && 
            pipeline.retriever == null) {
            throw new IllegalArgumentException(
                "EmbeddingModel is required when using EmbeddingVectorStore without custom Retriever"
            );
        }
    }
    
    /**
     * Retrieve relevant documents for the given query.
     * 
     * @param query The search query
     * @return List of relevant documents
     * @throws Exception if retrieval fails
     */
    public List<Document> retrieve(String query) throws Exception {
        return retrieve(query, RetrievalConfig.defaultConfig());
    }
    
    /**
     * Retrieve relevant documents with custom configuration.
     * 
     * @param query The search query
     * @param config Custom retrieval configuration
     * @return List of relevant documents
     * @throws Exception if retrieval fails
     */
    public List<Document> retrieve(String query, RetrievalConfig config) throws Exception {
        log.debug("Starting retrieval for query: {}", query);
        
        // Use custom config or fall back to builder defaults
        int effectiveTopK = config.topK() > 0 ? config.topK() : topK;
        float effectiveMinScore = config.minScore() >= 0 ? config.minScore() : minScore;
        Map<String, Object> effectiveFilters = config.filters() != null ? config.filters() : filters;
        
        // Step 1: Retrieve documents
        List<RetrievalResult> retrievalResults;
        
        if (retriever != null) {
            // Use custom retriever
            log.debug("Using custom retriever");
            retrievalResults = retriever.retrieve(
                query, 
                indexName, 
                new RetrievalConfig(effectiveTopK, effectiveMinScore, effectiveFilters)
            );
        } else {
            // Use vector store directly
            retrievalResults = retrieveFromVectorStore(
                query, 
                effectiveTopK, 
                effectiveMinScore, 
                effectiveFilters
            );
        }
        
        log.debug("Retrieved {} documents", retrievalResults.size());
        
        // Step 2: Optional reranking
        if (reranker != null && !retrievalResults.isEmpty()) {
            log.debug("Applying reranking");
            RerankConfig rerankConfig = new RerankConfig(effectiveTopK, Map.of());
            retrievalResults = reranker.rerank(query, retrievalResults, rerankConfig);
            log.debug("Reranking complete, {} documents remaining", retrievalResults.size());
        }
        
        // Convert to Document list
        return retrievalResults.stream()
            .map(RetrievalResult::document)
            .toList();
    }
    
    /**
     * Retrieve documents asynchronously.
     * 
     * @param query The search query
     * @return CompletableFuture with results
     */
    public CompletableFuture<List<Document>> retrieveAsync(String query) {
        return retrieveAsync(query, RetrievalConfig.defaultConfig());
    }
    
    /**
     * Retrieve documents asynchronously with custom configuration.
     * 
     * @param query The search query
     * @param config Custom retrieval configuration
     * @return CompletableFuture with results
     */
    public CompletableFuture<List<Document>> retrieveAsync(String query, RetrievalConfig config) {
        if (useVirtualThreads) {
            return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        return retrieve(query, config);
                    } catch (Exception e) {
                        throw new RuntimeException("Retrieval failed", e);
                    }
                }, 
                command -> Thread.ofVirtual().start(command)
            );
        } else {
            return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        return retrieve(query, config);
                    } catch (Exception e) {
                        throw new RuntimeException("Retrieval failed", e);
                    }
                }
            );
        }
    }
    
    /**
     * Retrieve documents directly from vector store.
     */
    private List<RetrievalResult> retrieveFromVectorStore(
            String query, 
            int k, 
            float minScoreThreshold,
            Map<String, Object> filterMetadata) throws Exception {
        
        String effectiveQuery = queryPrefix.isEmpty() ? query : queryPrefix + query;
        
        DocumentsResult results;
        
        if (vectorStore instanceof TextVectorStore textStore) {
            // TextVectorStore handles embedding internally
            log.trace("Using TextVectorStore for retrieval");
            results = textStore.findRelevant(indexName, effectiveQuery, k);
            
        } else if (vectorStore instanceof EmbeddingVectorStore embeddingStore) {
            // We need to create embedding ourselves
            log.trace("Using EmbeddingVectorStore for retrieval");
            
            // Generate query embedding
            Response<Embedding> response = embeddingClient.embed(TextSegment.from(effectiveQuery));
            float[] queryVector = response.content().vector();
            
            results = embeddingStore.findRelevant(indexName, queryVector, k);
            
        } else {
            throw new IllegalStateException("BaseVectorStore without text/embedding support is not suitable for retrieval");
        }
        
        // Convert to RetrievalResult and apply filters
        List<RetrievalResult> retrievalResults = new ArrayList<>();
        
        for (DocumentsResult.ResultEntry entry : results.getResult()) {
            Document doc = entry.getDocument();
            Float score = entry.getValue();
            
            // Apply score filter
            if (score < minScoreThreshold) {
                continue;
            }
            
            // Apply metadata filters
            if (!matchesFilters(doc, filterMetadata)) {
                continue;
            }
            
            retrievalResults.add(new RetrievalResult(
                doc,
                score,
                Map.of("retrievalScore", score)
            ));
        }
        
        return retrievalResults;
    }
    
    /**
     * Check if document matches all filter criteria.
     */
    private boolean matchesFilters(Document doc, Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        
        Map<String, Object> metadata = doc.getMetadata();
        if (metadata == null) {
            return false;
        }
        
        for (Map.Entry<String, Object> filter : filters.entrySet()) {
            Object docValue = metadata.get(filter.getKey());
            Object filterValue = filter.getValue();
            
            if (docValue == null || !docValue.equals(filterValue)) {
                return false;
            }
        }
        
        return true;
    }
}
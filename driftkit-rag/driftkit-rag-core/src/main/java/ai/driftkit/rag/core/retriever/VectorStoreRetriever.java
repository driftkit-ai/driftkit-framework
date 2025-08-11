package ai.driftkit.rag.core.retriever;

import ai.driftkit.embedding.core.domain.Embedding;
import ai.driftkit.embedding.core.domain.Response;
import ai.driftkit.embedding.core.domain.TextSegment;
import ai.driftkit.embedding.core.service.EmbeddingModel;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Default retriever implementation that uses a vector store directly.
 */
@Slf4j
@Builder
@RequiredArgsConstructor
public class VectorStoreRetriever implements Retriever {
    
    @NonNull
    private final BaseVectorStore vectorStore;
    
    private final EmbeddingModel embeddingModel; // Required only for EmbeddingVectorStore
    
    @Builder.Default
    private final String queryPrefix = "";
    
    /**
     * Retrieve relevant documents from the vector store.
     */
    @Override
    public List<RetrievalResult> retrieve(String query, String index, RetrievalConfig config) throws Exception {
        log.debug("Retrieving documents for query: {} from index: {}", query, index);
        
        String effectiveQuery = queryPrefix.isEmpty() ? query : queryPrefix + query;
        
        DocumentsResult results;
        
        if (vectorStore instanceof TextVectorStore textStore) {
            // TextVectorStore handles embedding internally
            log.trace("Using TextVectorStore for retrieval");
            results = textStore.findRelevant(index, effectiveQuery, config.topK());
            
        } else if (vectorStore instanceof EmbeddingVectorStore embeddingStore) {
            // We need to create embedding ourselves
            if (embeddingModel == null) {
                throw new IllegalStateException("EmbeddingModel is required for EmbeddingVectorStore");
            }
            
            log.trace("Using EmbeddingVectorStore for retrieval");
            
            // Generate query embedding
            Response<Embedding> response = embeddingModel.embed(TextSegment.from(effectiveQuery));
            float[] queryVector = response.content().vector();
            
            results = embeddingStore.findRelevant(index, queryVector, config.topK());
            
        } else {
            throw new IllegalStateException("BaseVectorStore without text/embedding support is not suitable for retrieval");
        }
        
        // Convert to RetrievalResult and apply filters
        List<RetrievalResult> retrievalResults = new ArrayList<>();
        
        for (DocumentsResult.ResultEntry entry : results.getResult()) {
            Document doc = entry.getDocument();
            Float score = entry.getValue();
            
            // Apply score filter
            if (score < config.minScore()) {
                continue;
            }
            
            // Apply metadata filters
            if (!matchesFilters(doc, config.filters())) {
                continue;
            }
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("retrievalScore", score);
            metadata.put("index", index);
            
            retrievalResults.add(new RetrievalResult(doc, score, metadata));
        }
        
        log.debug("Retrieved {} documents after filtering", retrievalResults.size());
        
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
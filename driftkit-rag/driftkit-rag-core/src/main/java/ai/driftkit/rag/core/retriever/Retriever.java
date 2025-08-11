package ai.driftkit.rag.core.retriever;

import ai.driftkit.vector.core.domain.Document;

import java.util.List;
import java.util.Map;

/**
 * Interface for retrieving relevant documents based on a query.
 * Implementations can use various retrieval strategies.
 */
public interface Retriever {
    
    /**
     * Configuration for retrieval operations.
     */
    record RetrievalConfig(
            int topK,
            float minScore,
            Map<String, Object> filters
    ) {
        public static RetrievalConfig defaultConfig() {
            return new RetrievalConfig(10, 0.0f, Map.of());
        }
    }
    
    /**
     * Result of a retrieval operation containing documents and their scores.
     */
    record RetrievalResult(
            Document document,
            float score,
            Map<String, Object> metadata
    ) {}
    
    /**
     * Retrieve relevant documents based on the query.
     * 
     * @param query The search query
     * @param index The index to search in
     * @param config Retrieval configuration
     * @return List of retrieved documents with scores
     * @throws Exception if retrieval fails
     */
    List<RetrievalResult> retrieve(String query, String index, RetrievalConfig config) throws Exception;
    
    /**
     * Retrieve relevant documents with default configuration.
     * 
     * @param query The search query
     * @param index The index to search in
     * @return List of retrieved documents with scores
     * @throws Exception if retrieval fails
     */
    default List<RetrievalResult> retrieve(String query, String index) throws Exception {
        return retrieve(query, index, RetrievalConfig.defaultConfig());
    }
}
package ai.driftkit.rag.core.reranker;

import ai.driftkit.rag.core.retriever.Retriever.RetrievalResult;

import java.util.List;
import java.util.Map;

/**
 * Interface for reranking retrieved documents to improve relevance.
 * Implementations can use various reranking models and strategies.
 */
public interface Reranker {
    
    /**
     * Configuration for reranking operations.
     */
    record RerankConfig(
            int topK,
            Map<String, Object> modelParams
    ) {
        public static RerankConfig defaultConfig() {
            return new RerankConfig(10, Map.of());
        }
    }
    
    /**
     * Rerank a list of retrieved documents based on the original query.
     * 
     * @param query The original search query
     * @param results The retrieved documents to rerank
     * @param config Reranking configuration
     * @return Reranked list of documents
     * @throws Exception if reranking fails
     */
    List<RetrievalResult> rerank(String query, List<RetrievalResult> results, RerankConfig config) throws Exception;
    
    /**
     * Rerank with default configuration.
     * 
     * @param query The original search query
     * @param results The retrieved documents to rerank
     * @return Reranked list of documents
     * @throws Exception if reranking fails
     */
    default List<RetrievalResult> rerank(String query, List<RetrievalResult> results) throws Exception {
        return rerank(query, results, RerankConfig.defaultConfig());
    }
}
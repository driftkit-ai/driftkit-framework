package ai.driftkit.rag.core.reranker;

import ai.driftkit.rag.core.reranker.Reranker.RerankResult;
import ai.driftkit.rag.core.retriever.Retriever.RetrievalResult;
import ai.driftkit.vector.core.domain.Document;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RerankResultTest {

    @Test
    void testRerankResultCreation() {
        // Given
        Document document = new Document("doc1", new float[]{0.1f, 0.2f, 0.3f}, "Test content", Map.of("source", "test"));
        float originalScore = 0.8f;
        float rerankScore = 0.95f;
        Map<String, Object> metadata = Map.of("key", "value");

        // When
        RerankResult result = new RerankResult(document, originalScore, rerankScore, metadata);

        // Then
        assertEquals(document, result.document());
        assertEquals(originalScore, result.originalScore());
        assertEquals(rerankScore, result.rerankScore());
        assertEquals(metadata, result.metadata());
    }

    @Test
    void testCreateFromRetrievalResult() {
        // Given
        Document document = new Document("doc1", new float[]{0.1f, 0.2f, 0.3f}, "Test content", Map.of("source", "test"));
        float originalScore = 0.8f;
        float rerankScore = 0.95f;
        Map<String, Object> metadata = Map.of("key", "value");
        RetrievalResult retrievalResult = new RetrievalResult(document, originalScore, metadata);

        // When
        RerankResult rerankResult = RerankResult.from(retrievalResult, rerankScore);

        // Then
        assertEquals(document, rerankResult.document());
        assertEquals(originalScore, rerankResult.originalScore());
        assertEquals(rerankScore, rerankResult.rerankScore());
        assertEquals(metadata, rerankResult.metadata());
    }

    @Test
    void testConvertToRetrievalResult() {
        // Given
        Document document = new Document("doc1", new float[]{0.1f, 0.2f, 0.3f}, "Test content", Map.of("source", "test"));
        float originalScore = 0.8f;
        float rerankScore = 0.95f;
        Map<String, Object> metadata = Map.of("key", "value");
        RerankResult rerankResult = new RerankResult(document, originalScore, rerankScore, metadata);

        // When
        RetrievalResult retrievalResult = rerankResult.toRetrievalResult();

        // Then
        assertEquals(document, retrievalResult.document());
        assertEquals(rerankScore, retrievalResult.score()); // Uses rerank score
        assertEquals(metadata, retrievalResult.metadata());
    }

    @Test
    void testScoreTracking() {
        // Given
        Document document = new Document("doc1", new float[]{0.1f, 0.2f, 0.3f}, "Test content", Map.of());
        RetrievalResult original = new RetrievalResult(document, 0.5f, Map.of());
        
        // When
        RerankResult reranked = RerankResult.from(original, 0.9f);
        
        // Then
        assertEquals(0.5f, reranked.originalScore());
        assertEquals(0.9f, reranked.rerankScore());
        
        // And when converted back
        RetrievalResult converted = reranked.toRetrievalResult();
        assertEquals(0.9f, converted.score()); // Should use rerank score
    }
}
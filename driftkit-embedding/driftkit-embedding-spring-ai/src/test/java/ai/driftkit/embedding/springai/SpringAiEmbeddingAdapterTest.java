package ai.driftkit.embedding.springai;

import ai.driftkit.config.EtlConfig.EmbeddingServiceConfig;
import ai.driftkit.embedding.core.domain.Embedding;
import ai.driftkit.embedding.core.domain.TextSegment;
import ai.driftkit.embedding.core.domain.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpringAiEmbeddingAdapterTest {

    @Mock
    private org.springframework.ai.embedding.EmbeddingModel springAiEmbeddingModel;

    private SpringAiEmbeddingAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SpringAiEmbeddingAdapter(springAiEmbeddingModel, "test-model");
    }

    @Test
    void testConstructorValidation() {
        // Test null embedding model
        assertThrows(IllegalArgumentException.class, 
            () -> new SpringAiEmbeddingAdapter(null, "test"));
        
        // Test blank model name
        assertThrows(IllegalArgumentException.class, 
            () -> new SpringAiEmbeddingAdapter(springAiEmbeddingModel, ""));
        assertThrows(IllegalArgumentException.class, 
            () -> new SpringAiEmbeddingAdapter(springAiEmbeddingModel, null));
    }

    @Test
    void testSupportsName() {
        assertTrue(adapter.supportsName("test-model"));
        assertTrue(adapter.supportsName("TEST-MODEL")); // Case insensitive
        assertFalse(adapter.supportsName("different-model"));
    }

    @Test
    void testModelThrowsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> adapter.model());
    }

    @Test
    void testConfigure() {
        // Configure should just log, not throw
        assertDoesNotThrow(() -> adapter.configure(null));
    }

    @Test
    void testEmbedAll() {
        // Prepare mock data
        float[] embedding1 = {0.1f, 0.2f, 0.3f};
        float[] embedding2 = {0.4f, 0.5f, 0.6f};
        
        // Test
        List<TextSegment> segments = Arrays.asList(
            TextSegment.from("text1"),
            TextSegment.from("text2")
        );
        
        Response<List<Embedding>> response = adapter.embedAll(segments);
        
        // Verify
        assertNotNull(response);
        assertEquals(2, response.content().size());
        assertArrayEquals(embedding1, response.content().get(0).vector());
        assertArrayEquals(embedding2, response.content().get(1).vector());
        assertEquals(100, response.tokenUsage().inputTokenCount());
        
        verify(springAiEmbeddingModel).embedForResponse(Arrays.asList("text1", "text2"));
    }

    @Test
    void testEmbedAllWithEmptySegments() {
        Response<List<Embedding>> response = adapter.embedAll(Collections.emptyList());
        
        assertNotNull(response);
        assertTrue(response.content().isEmpty());
        
        verifyNoInteractions(springAiEmbeddingModel);
    }

    @Test
    void testEmbedAllWithNullSegments() {
        List<TextSegment> segments = Arrays.asList(
            null,
            TextSegment.from(""),
            TextSegment.from("valid text"),
            null
        );
        
        float[] embedding = {0.1f, 0.2f, 0.3f};
        List<org.springframework.ai.embedding.Embedding> springAiEmbeddings = 
            List.of(new org.springframework.ai.embedding.Embedding(embedding, 0));
        
        EmbeddingResponse springAiResponse = new EmbeddingResponse(springAiEmbeddings);
        when(springAiEmbeddingModel.embedForResponse(anyList())).thenReturn(springAiResponse);
        
        Response<List<Embedding>> response = adapter.embedAll(segments);
        
        assertNotNull(response);
        assertEquals(1, response.content().size());
        
        verify(springAiEmbeddingModel).embedForResponse(List.of("valid text"));
    }

    @Test
    void testEmbedSingle() {
        float[] embedding = {0.1f, 0.2f, 0.3f};
        List<org.springframework.ai.embedding.Embedding> springAiEmbeddings = 
            List.of(new org.springframework.ai.embedding.Embedding(embedding, 0));
        
        EmbeddingResponse springAiResponse = new EmbeddingResponse(springAiEmbeddings);
        when(springAiEmbeddingModel.embedForResponse(anyList())).thenReturn(springAiResponse);
        
        TextSegment segment = TextSegment.from("test text");
        Response<Embedding> response = adapter.embed(segment);
        
        assertNotNull(response);
        assertNotNull(response.content());
        assertArrayEquals(embedding, response.content().vector());
        
        verify(springAiEmbeddingModel).embedForResponse(List.of("test text"));
    }

    @Test
    void testEmbedWithNullSegment() {
        assertThrows(IllegalArgumentException.class, () -> adapter.embed(null));
    }

    @Test
    void testEstimateTokenCount() {
        assertEquals(0, adapter.estimateTokenCount(null));
        assertEquals(0, adapter.estimateTokenCount(""));
        
        // Test actual estimation (4 chars = 1 token approximation)
        assertEquals(2, adapter.estimateTokenCount("Hello"));
        assertEquals(11, adapter.estimateTokenCount("Hello, World!")); // 13 chars / 4 = 3.25 -> 3
    }

    @Test
    void testEmbedAllWithNullResponse() {
        when(springAiEmbeddingModel.embedForResponse(anyList())).thenReturn(null);
        
        List<TextSegment> segments = List.of(TextSegment.from("test"));
        
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> adapter.embedAll(segments));
        assertTrue(exception.getMessage().contains("null response"));
    }

    @Test
    void testEmbedAllWithException() {
        when(springAiEmbeddingModel.embedForResponse(anyList()))
            .thenThrow(new RuntimeException("API error"));
        
        List<TextSegment> segments = List.of(TextSegment.from("test"));
        
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> adapter.embedAll(segments));
        assertTrue(exception.getMessage().contains("Failed to generate embeddings"));
    }
}
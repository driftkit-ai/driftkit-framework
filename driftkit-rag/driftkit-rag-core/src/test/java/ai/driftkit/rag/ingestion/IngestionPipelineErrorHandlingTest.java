package ai.driftkit.rag.ingestion;

import ai.driftkit.embedding.core.domain.Embedding;
import ai.driftkit.embedding.core.domain.Response;
import ai.driftkit.embedding.core.domain.TextSegment;
import ai.driftkit.embedding.core.service.EmbeddingModel;
import ai.driftkit.rag.core.domain.LoadedDocument;
import ai.driftkit.rag.core.loader.DocumentLoader;
import ai.driftkit.rag.core.splitter.TextSplitter;
import ai.driftkit.vector.core.domain.Document;
import ai.driftkit.vector.core.domain.TextVectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IngestionPipelineErrorHandlingTest {

    @Mock
    private DocumentLoader documentLoader;
    
    @Mock
    private TextSplitter textSplitter;
    
    @Mock
    private TextVectorStore vectorStore;
    
    @Mock
    private EmbeddingModel embeddingModel;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }
    
    @Test
    void testErrorInResultHandler() throws Exception {
        // Given
        LoadedDocument doc = LoadedDocument.builder()
            .id("doc1")
            .content("content")
            .source("test.txt")
            .metadata(new HashMap<>())
            .build();
        when(documentLoader.loadStream()).thenReturn(Stream.of(doc));
        when(documentLoader.supportsStreaming()).thenReturn(true);
        
        Document chunk = new Document("chunk1", new float[]{0.1f}, "chunk content", new HashMap<>());
        when(textSplitter.split(any())).thenReturn(List.of(chunk));
        
        IngestionPipeline pipeline = IngestionPipeline.builder()
            .documentLoader(documentLoader)
            .textSplitter(textSplitter)
            .vectorStore(vectorStore)
            .indexName("test")
            .build();
        
        AtomicInteger resultHandlerCalls = new AtomicInteger(0);
        AtomicInteger progressListenerCalls = new AtomicInteger(0);
        
        IngestionPipeline.ProgressListener progressListener = new IngestionPipeline.ProgressListener() {
            @Override
            public void onDocumentLoaded(String documentId, String source) {}
            
            @Override
            public void onDocumentProcessed(String documentId, int chunks) {}
            
            @Override
            public void onDocumentFailed(String documentId, Exception error) {}
            
            @Override
            public void onChunkStored(String chunkId) {}
            
            @Override
            public void onProgress(long processed, long total) {}
            
            @Override
            public void onResultHandlerError(String documentId, Exception error) {
                progressListenerCalls.incrementAndGet();
                assertEquals("doc1", documentId);
                assertEquals("Test error in handler", error.getMessage());
            }
        };
        
        // When & Then
        IngestionException exception = assertThrows(IngestionException.class, () -> {
            pipeline.run(result -> {
                resultHandlerCalls.incrementAndGet();
                throw new RuntimeException("Test error in handler");
            }, progressListener);
        });
        
        // Verify
        assertEquals(1, resultHandlerCalls.get());
        assertEquals(1, progressListenerCalls.get());
        assertFalse(exception.hasCriticalErrors());
        assertEquals(1, exception.getErrors().size());
        
        IngestionException.ErrorDetail error = exception.getErrors().get(0);
        assertEquals("doc1", error.documentId());
        assertEquals("Test error in handler", error.error().getMessage());
        assertEquals(IngestionException.ErrorType.RESULT_HANDLER, error.errorType());
    }
    
    @Test
    void testDocumentProcessingError() throws Exception {
        // Given
        LoadedDocument doc = LoadedDocument.builder()
            .id("doc1")
            .content("content")
            .source("test.txt")
            .metadata(new HashMap<>())
            .build();
        when(documentLoader.loadStream()).thenReturn(Stream.of(doc));
        when(documentLoader.supportsStreaming()).thenReturn(true);
        
        // Simulate error during splitting
        when(textSplitter.split(any())).thenThrow(new RuntimeException("Split error"));
        
        IngestionPipeline pipeline = IngestionPipeline.builder()
            .documentLoader(documentLoader)
            .textSplitter(textSplitter)
            .vectorStore(vectorStore)
            .indexName("test")
            .maxRetries(1) // Only try once
            .build();
        
        AtomicInteger resultHandlerCalls = new AtomicInteger(0);
        
        // When & Then
        IngestionException exception = assertThrows(IngestionException.class, () -> {
            pipeline.run(result -> {
                resultHandlerCalls.incrementAndGet();
                // Result handler should still be called even for failed documents
                assertFalse(result.isSuccess());
                assertEquals(1, result.errors().size());
            }, null);
        });
        
        // Verify
        assertEquals(1, resultHandlerCalls.get());
        assertTrue(exception.hasCriticalErrors());
        assertEquals(1, exception.getCriticalErrors().size());
        
        IngestionException.ErrorDetail error = exception.getCriticalErrors().get(0);
        assertEquals("doc1", error.documentId());
        assertEquals(IngestionException.ErrorType.DOCUMENT_PROCESSING, error.errorType());
    }
    
    @Test
    void testMixedErrors() throws Exception {
        // Given - two documents, one succeeds, one has handler error
        LoadedDocument doc1 = LoadedDocument.builder()
            .id("doc1")
            .content("content1")
            .source("test1.txt")
            .metadata(new HashMap<>())
            .build();
        LoadedDocument doc2 = LoadedDocument.builder()
            .id("doc2")
            .content("content2")
            .source("test2.txt")
            .metadata(new HashMap<>())
            .build();
        when(documentLoader.loadStream()).thenReturn(Stream.of(doc1, doc2));
        when(documentLoader.supportsStreaming()).thenReturn(true);
        
        Document chunk = new Document("chunk", new float[]{0.1f}, "chunk content", new HashMap<>());
        when(textSplitter.split(any())).thenReturn(List.of(chunk));
        
        IngestionPipeline pipeline = IngestionPipeline.builder()
            .documentLoader(documentLoader)
            .textSplitter(textSplitter)
            .vectorStore(vectorStore)
            .indexName("test")
            .build();
        
        AtomicInteger successCount = new AtomicInteger(0);
        
        // When & Then
        IngestionException exception = assertThrows(IngestionException.class, () -> {
            pipeline.run(result -> {
                if (result.documentId().equals("doc2")) {
                    throw new RuntimeException("Handler error for doc2");
                }
                successCount.incrementAndGet();
            }, null);
        });
        
        // Verify
        assertEquals(1, successCount.get()); // Only doc1 succeeded
        assertEquals(1, exception.getErrors().size()); // Only handler error
        assertFalse(exception.hasCriticalErrors()); // Handler errors are not critical
    }
}
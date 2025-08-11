package ai.driftkit.rag.ingestion;

import ai.driftkit.embedding.core.domain.Embedding;
import ai.driftkit.embedding.core.domain.Response;
import ai.driftkit.embedding.core.domain.TextSegment;
import ai.driftkit.embedding.core.service.EmbeddingModel;
import ai.driftkit.rag.core.domain.LoadedDocument;
import ai.driftkit.rag.core.loader.DocumentLoader;
import ai.driftkit.rag.core.splitter.TextSplitter;
import ai.driftkit.vector.core.domain.BaseVectorStore;
import ai.driftkit.vector.core.domain.Document;
import ai.driftkit.vector.core.domain.EmbeddingVectorStore;
import ai.driftkit.vector.core.domain.TextVectorStore;
import lombok.Builder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Pipeline for ingesting documents into a vector store.
 * Uses streaming and virtual threads for efficient processing.
 */
@Slf4j
@Builder
@RequiredArgsConstructor
public class IngestionPipeline {
    
    @NonNull
    private final DocumentLoader documentLoader;
    
    @NonNull
    private final TextSplitter textSplitter;
    
    private final EmbeddingModel embeddingClient; // Optional - only needed for EmbeddingVectorStore
    
    @NonNull
    private final BaseVectorStore vectorStore;
    
    @NonNull
    @Builder.Default
    private final String indexName = "default";
    
    @Builder.Default
    private final int maxRetries = 3;
    
    @Builder.Default
    private final long retryDelayMs = 1000;
    
    @Builder.Default
    private final boolean useVirtualThreads = true;
    
    /**
     * Progress listener for tracking ingestion progress.
     */
    public interface ProgressListener {
        void onDocumentLoaded(String documentId, String source);
        void onDocumentProcessed(String documentId, int chunks);
        void onDocumentFailed(String documentId, Exception error);
        void onChunkStored(String chunkId);
        void onProgress(long processed, long total);
    }
    
    /**
     * Result of processing a single document.
     */
    public record DocumentResult(
            String documentId,
            String source,
            int chunksCreated,
            int chunksStored,
            List<Exception> errors,
            long processingTimeMs
    ) {
        public boolean isSuccess() {
            return errors.isEmpty() && chunksCreated == chunksStored;
        }
    }
    
    /**
     * Validate configuration on build.
     */
    private static void validate(IngestionPipeline pipeline) {
        if (pipeline.vectorStore instanceof EmbeddingVectorStore && pipeline.embeddingClient == null) {
            throw new IllegalArgumentException(
                "EmbeddingModel is required when using EmbeddingVectorStore"
            );
        }
    }
    
    /**
     * Run the ingestion pipeline with streaming processing.
     * Each document is processed independently and can be acknowledged.
     * 
     * @param progressListener Optional listener for progress updates
     * @return Stream of results for each processed document
     */
    public Stream<DocumentResult> run(ProgressListener progressListener) {
        log.info("Starting streaming document ingestion pipeline for index: {}", indexName);
        
        try {
            // Get document stream - either lazy or eager loading
            Stream<LoadedDocument> documentStream = documentLoader.supportsStreaming() 
                ? documentLoader.loadStream() 
                : documentLoader.load().stream();
            
            // Process each document independently
            return documentStream
                .map(doc -> processDocumentWithRetry(doc, progressListener))
                .onClose(() -> log.info("Ingestion pipeline completed"));
                
        } catch (Exception e) {
            log.error("Failed to start ingestion pipeline", e);
            throw new RuntimeException("Failed to start ingestion pipeline", e);
        }
    }
    
    /**
     * Run the ingestion pipeline without progress tracking.
     */
    public Stream<DocumentResult> run() {
        return run(null);
    }
    
    /**
     * Run the ingestion pipeline with a consumer for handling results.
     * This allows for acknowledgment pattern.
     */
    public void run(Consumer<DocumentResult> resultHandler, ProgressListener progressListener) {
        try (Stream<DocumentResult> results = run(progressListener)) {
            results.forEach(result -> {
                try {
                    resultHandler.accept(result);
                } catch (Exception e) {
                    log.error("Error in result handler for document: {}", result.documentId(), e);
                }
            });
        }
    }
    
    /**
     * Process a single document with retry logic.
     */
    private DocumentResult processDocumentWithRetry(LoadedDocument document, ProgressListener listener) {
        long startTime = System.currentTimeMillis();
        String docId = document.getId() != null ? document.getId() : UUID.randomUUID().toString();
        
        if (listener != null) {
            listener.onDocumentLoaded(docId, document.getSource());
        }
        
        int attempts = 0;
        Exception lastError = null;
        
        while (attempts < maxRetries) {
            try {
                DocumentResult result = processDocument(document, docId, listener);
                
                if (result.isSuccess()) {
                    return result;
                }
                
                // If there were errors but we want to retry
                if (!result.errors().isEmpty()) {
                    lastError = result.errors().get(0);
                    attempts++;
                    
                    if (attempts < maxRetries) {
                        log.warn("Document {} processing had errors (attempt {}/{}), retrying...", 
                            docId, attempts, maxRetries);
                        Thread.sleep(retryDelayMs * attempts); // Exponential backoff
                    }
                } else {
                    return result; // Partial success, don't retry
                }
                
            } catch (Exception e) {
                lastError = e;
                attempts++;
                
                if (attempts < maxRetries) {
                    log.warn("Error processing document {} (attempt {}/{}), retrying...", 
                        docId, attempts, maxRetries, e);
                    
                    try {
                        Thread.sleep(retryDelayMs * attempts);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        // All retries failed
        if (listener != null) {
            listener.onDocumentFailed(docId, lastError);
        }
        
        return new DocumentResult(
            docId,
            document.getSource(),
            0,
            0,
            List.of(lastError != null ? lastError : new RuntimeException("Processing failed")),
            System.currentTimeMillis() - startTime
        );
    }
    
    /**
     * Process a single document.
     */
    private DocumentResult processDocument(LoadedDocument document, String docId, ProgressListener listener) {
        long startTime = System.currentTimeMillis();
        List<Exception> errors = new ArrayList<>();
        AtomicInteger chunksCreated = new AtomicInteger(0);
        AtomicInteger chunksStored = new AtomicInteger(0);
        
        try {
            // Split document into chunks
            List<Document> chunks = textSplitter.split(document);
            chunksCreated.set(chunks.size());
            
            log.debug("Document {} split into {} chunks", docId, chunks.size());
            
            // Process chunks based on vector store type
            if (vectorStore instanceof TextVectorStore textStore) {
                // TextVectorStore handles embedding internally
                processChunksForTextStore(chunks, docId, document, textStore, chunksStored, errors, listener);
            } else if (vectorStore instanceof EmbeddingVectorStore embeddingStore) {
                // We need to create embeddings ourselves
                processChunksForEmbeddingStore(chunks, docId, document, embeddingStore, chunksStored, errors, listener);
            } else {
                // BaseVectorStore - store without embeddings (shouldn't happen in RAG context)
                throw new IllegalStateException("BaseVectorStore without text/embedding support is not suitable for RAG");
            }
            
            if (listener != null) {
                listener.onDocumentProcessed(docId, chunksCreated.get());
            }
            
        } catch (Exception e) {
            log.error("Error processing document: {}", docId, e);
            errors.add(e);
        }
        
        return new DocumentResult(
            docId,
            document.getSource(),
            chunksCreated.get(),
            chunksStored.get(),
            errors,
            System.currentTimeMillis() - startTime
        );
    }
    
    /**
     * Process chunks for TextVectorStore (no embedding needed).
     */
    private void processChunksForTextStore(
            List<Document> chunks,
            String docId,
            LoadedDocument sourceDoc,
            TextVectorStore textStore,
            AtomicInteger chunksStored,
            List<Exception> errors,
            ProgressListener listener) {
        
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            String chunkId = docId + "-" + i;
            
            Runnable task = () -> {
                try {
                    // Create document without embedding (TextVectorStore will handle it)
                    Document doc = new Document(
                        chunkId,
                        null, // No vector needed
                        chunk.getPageContent(),
                        chunk.getMetadata()
                    );
                    
                    // Add source metadata
                    doc.getMetadata().put("source", sourceDoc.getSource());
                    doc.getMetadata().put("sourceDocId", docId);
                    doc.getMetadata().put("chunkIndex", chunkId.substring(chunkId.lastIndexOf('-') + 1));
                    
                    // Store in vector store
                    textStore.addDocument(indexName, doc);
                    
                    chunksStored.incrementAndGet();
                    
                    if (listener != null) {
                        listener.onChunkStored(chunkId);
                    }
                    
                    log.trace("Successfully stored chunk: {}", chunkId);
                    
                } catch (Exception e) {
                    log.error("Failed to store chunk: {}", chunkId, e);
                    synchronized (errors) {
                        errors.add(new RuntimeException("Failed to store chunk " + chunkId, e));
                    }
                }
            };
            
            CompletableFuture<Void> future = useVirtualThreads 
                ? CompletableFuture.runAsync(task, command -> Thread.ofVirtual().start(command))
                : CompletableFuture.runAsync(task);
                
            futures.add(future);
        }
        
        // Wait for all chunks
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }
    
    /**
     * Process chunks for EmbeddingVectorStore (we create embeddings).
     */
    private void processChunksForEmbeddingStore(
            List<Document> chunks,
            String docId,
            LoadedDocument sourceDoc,
            EmbeddingVectorStore embeddingStore,
            AtomicInteger chunksStored,
            List<Exception> errors,
            ProgressListener listener) {
        
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            String chunkId = docId + "-" + i;
            final int chunkIndex = i;
            
            Runnable task = () -> {
                try {
                    // Generate embedding
                    Response<Embedding> response = embeddingClient.embed(
                        TextSegment.from(chunk.getPageContent())
                    );
                    float[] vector = response.content().vector();
                    
                    // Create document with embedding
                    Document embeddedDoc = new Document(
                        chunkId,
                        vector,
                        chunk.getPageContent(),
                        chunk.getMetadata()
                    );
                    
                    // Add source metadata
                    embeddedDoc.getMetadata().put("source", sourceDoc.getSource());
                    embeddedDoc.getMetadata().put("sourceDocId", docId);
                    embeddedDoc.getMetadata().put("chunkIndex", chunkIndex);
                    
                    // Store in vector store
                    embeddingStore.addDocument(indexName, embeddedDoc);
                    
                    chunksStored.incrementAndGet();
                    
                    if (listener != null) {
                        listener.onChunkStored(chunkId);
                    }
                    
                    log.trace("Successfully stored embedded chunk: {}", chunkId);
                    
                } catch (Exception e) {
                    log.error("Failed to process chunk: {}", chunkId, e);
                    synchronized (errors) {
                        errors.add(new RuntimeException("Failed to process chunk " + chunkId, e));
                    }
                }
            };
            
            CompletableFuture<Void> future = useVirtualThreads 
                ? CompletableFuture.runAsync(task, command -> Thread.ofVirtual().start(command))
                : CompletableFuture.runAsync(task);
                
            futures.add(future);
        }
        
        // Wait for all chunks
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }
}
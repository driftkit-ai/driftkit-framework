package ai.driftkit.rag.spring.example;

import ai.driftkit.rag.ingestion.IngestionPipeline;
import ai.driftkit.rag.spring.service.DocumentLoaderFactory;
import ai.driftkit.rag.spring.service.RagService;
import ai.driftkit.vector.core.domain.Document;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Example Spring Boot application demonstrating DriftKit RAG usage.
 */
@Slf4j
@SpringBootApplication
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagSpringBootExample {
    
    private final RagService ragService;
    private final DocumentLoaderFactory loaderFactory;
    
    public static void main(String[] args) {
        SpringApplication.run(RagSpringBootExample.class, args);
    }
    
    /**
     * REST endpoint to ingest documents from URLs.
     */
    @PostMapping("/ingest/urls")
    public IngestionResponse ingestUrls(@RequestBody IngestionRequest request) {
        if (CollectionUtils.isEmpty(request.getUrls())) {
            return new IngestionResponse(false, 0, 0, "No URLs provided");
        }
        
        log.info("Ingesting {} URLs into index: {}", request.getUrls().size(), request.getIndexName());
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        
        try (Stream<IngestionPipeline.DocumentResult> results = 
                ragService.ingestFromUrls(request.getUrls(), request.getIndexName())) {
            
            results.forEach(result -> {
                if (result.isSuccess()) {
                    successCount.incrementAndGet();
                    log.info("Successfully ingested: {} ({} chunks)", 
                        result.documentId(), result.chunksStored());
                } else {
                    errorCount.incrementAndGet();
                    log.error("Failed to ingest: {} - {}", 
                        result.documentId(), result.errors());
                }
            });
        }
        
        return new IngestionResponse(
            true, 
            successCount.get(), 
            errorCount.get(),
            String.format("Ingested %d documents, %d errors", successCount.get(), errorCount.get())
        );
    }
    
    /**
     * REST endpoint to search documents.
     */
    @PostMapping("/search")
    public SearchResponse search(@RequestBody SearchRequest request) {
        log.info("Searching for: {} in index: {}", request.getQuery(), request.getIndexName());
        
        List<Document> results = ragService.retrieve(
            request.getQuery(),
            request.getIndexName(),
            request.getTopK(),
            request.getMinScore()
        );
        
        if (CollectionUtils.isEmpty(results)) {
            return new SearchResponse(request.getQuery(), List.of(), "No results found");
        }
        
        List<SearchResult> searchResults = results.stream()
            .map(doc -> new SearchResult(
                doc.getId(),
                doc.getPageContent(),
                doc.getMetadata()
            ))
            .toList();
        
        return new SearchResponse(
            request.getQuery(), 
            searchResults,
            String.format("Found %d relevant documents", results.size())
        );
    }
    
    /**
     * Command line runner for demo purposes.
     */
    @Bean
    public CommandLineRunner demo() {
        return args -> {
            log.info("RAG Spring Boot Example Started");
            log.info("Try the following endpoints:");
            log.info("POST /api/rag/ingest/urls - Ingest documents from URLs");
            log.info("POST /api/rag/search - Search documents");
        };
    }
    
    // Request/Response DTOs
    
    @lombok.Data
    public static class IngestionRequest {
        private List<String> urls;
        private String indexName = "default";
    }
    
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class IngestionResponse {
        private boolean success;
        private int documentsIngested;
        private int errors;
        private String message;
    }
    
    @lombok.Data
    public static class SearchRequest {
        private String query;
        private String indexName = "default";
        private int topK = 10;
        private float minScore = 0.0f;
    }
    
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class SearchResponse {
        private String query;
        private List<SearchResult> results;
        private String message;
    }
    
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class SearchResult {
        private String id;
        private String content;
        private Map<String, Object> metadata;
    }
}
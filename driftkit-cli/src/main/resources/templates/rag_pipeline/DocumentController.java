package /*PACKAGE_NAME*/.controller;

import /*PACKAGE_NAME*/.service.DocumentIngestionService;
import /*PACKAGE_NAME*/.service.DocumentSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {
    
    private final DocumentIngestionService ingestionService;
    private final DocumentSearchService searchService;
    
    @PostMapping("/upload")
    public ResponseEntity<DocumentUploadResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "metadata", required = false) Map<String, String> metadata) {
        
        log.info("Uploading document: {} (size: {} bytes)", file.getOriginalFilename(), file.getSize());
        
        try {
            String documentId = ingestionService.ingestDocument(file, metadata);
            return ResponseEntity.ok(new DocumentUploadResponse(
                documentId,
                file.getOriginalFilename(),
                "Document uploaded and processed successfully"
            ));
        } catch (Exception e) {
            log.error("Error uploading document", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new DocumentUploadResponse(null, file.getOriginalFilename(), 
                    "Error processing document: " + e.getMessage()));
        }
    }
    
    @PostMapping("/upload-batch")
    public ResponseEntity<BatchUploadResponse> uploadDocuments(
            @RequestParam("files") List<MultipartFile> files) {
        
        log.info("Uploading {} documents", files.size());
        
        BatchUploadResponse response = new BatchUploadResponse();
        for (MultipartFile file : files) {
            try {
                String documentId = ingestionService.ingestDocument(file, null);
                response.addSuccess(file.getOriginalFilename(), documentId);
            } catch (Exception e) {
                log.error("Error uploading document: {}", file.getOriginalFilename(), e);
                response.addError(file.getOriginalFilename(), e.getMessage());
            }
        }
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/search")
    public ResponseEntity<SearchResponse> searchDocuments(
            @RequestParam("query") String query,
            @RequestParam(value = "limit", defaultValue = "5") int limit,
            @RequestParam(value = "minScore", defaultValue = "0.7") double minScore) {
        
        log.info("Searching documents with query: {}", query);
        
        try {
            var results = searchService.search(query, limit, minScore);
            return ResponseEntity.ok(new SearchResponse(query, results));
        } catch (Exception e) {
            log.error("Error searching documents", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new SearchResponse(query, List.of()));
        }
    }
    
    @PostMapping("/ask")
    public ResponseEntity<AnswerResponse> askQuestion(@RequestBody QuestionRequest request) {
        log.info("Processing question: {}", request.getQuestion());
        
        try {
            var answer = searchService.answerQuestion(
                request.getQuestion(),
                request.getMaxSources(),
                request.isIncludeSources()
            );
            return ResponseEntity.ok(answer);
        } catch (Exception e) {
            log.error("Error answering question", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new AnswerResponse(
                    request.getQuestion(),
                    "I encountered an error while processing your question.",
                    List.of(),
                    0.0
                ));
        }
    }
    
    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> deleteDocument(@PathVariable String documentId) {
        log.info("Deleting document: {}", documentId);
        
        try {
            ingestionService.deleteDocument(documentId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error deleting document", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/stats")
    public ResponseEntity<DocumentStats> getDocumentStats() {
        try {
            var stats = ingestionService.getDocumentStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error getting document stats", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    // Response DTOs
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class DocumentUploadResponse {
        private String documentId;
        private String filename;
        private String message;
    }
    
    @lombok.Data
    public static class BatchUploadResponse {
        private int totalFiles;
        private int successCount;
        private int errorCount;
        private List<UploadResult> results = new java.util.ArrayList<>();
        
        public void addSuccess(String filename, String documentId) {
            results.add(new UploadResult(filename, documentId, true, null));
            successCount++;
            totalFiles++;
        }
        
        public void addError(String filename, String error) {
            results.add(new UploadResult(filename, null, false, error));
            errorCount++;
            totalFiles++;
        }
        
        @lombok.Data
        @lombok.AllArgsConstructor
        public static class UploadResult {
            private String filename;
            private String documentId;
            private boolean success;
            private String error;
        }
    }
    
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class SearchResponse {
        private String query;
        private List<SearchResult> results;
    }
    
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class SearchResult {
        private String documentId;
        private String content;
        private double score;
        private Map<String, Object> metadata;
    }
    
    @lombok.Data
    public static class QuestionRequest {
        private String question;
        private int maxSources = 3;
        private boolean includeSources = true;
    }
    
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class AnswerResponse {
        private String question;
        private String answer;
        private List<Source> sources;
        private double confidence;
        
        @lombok.Data
        @lombok.AllArgsConstructor
        public static class Source {
            private String documentId;
            private String excerpt;
            private double relevanceScore;
        }
    }
    
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class DocumentStats {
        private long totalDocuments;
        private long totalChunks;
        private Map<String, Long> documentsByType;
        private long totalSizeMB;
    }
}
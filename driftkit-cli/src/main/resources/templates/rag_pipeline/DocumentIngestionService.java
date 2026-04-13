package /*PACKAGE_NAME*/.service;

import ai.driftkit.common.domain.Document;
import ai.driftkit.common.service.DocumentSplitter;
import ai.driftkit.embedding.EmbeddingModel;
import ai.driftkit.vector.VectorStore;
import /*PACKAGE_NAME*/.controller.DocumentController.DocumentStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {
    
    private final DocumentSplitter documentSplitter;
    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;
    private final Path documentStoragePath;
    
    @Value("${driftkit.rag.chunk-size:1000}")
    private int chunkSize;
    
    @Value("${driftkit.rag.chunk-overlap:200}")
    private int chunkOverlap;
    
    private final Tika tika = new Tika();
    private final Map<String, DocumentMetadata> documentRegistry = new ConcurrentHashMap<>();
    private final AtomicLong totalSize = new AtomicLong(0);
    
    public String ingestDocument(MultipartFile file, Map<String, String> additionalMetadata) throws IOException {
        String documentId = UUID.randomUUID().toString();
        String filename = file.getOriginalFilename();
        
        // Save the original file
        Path filePath = saveFile(file, documentId);
        
        // Extract content and metadata
        String content = extractContent(file.getInputStream());
        Map<String, Object> metadata = extractMetadata(file, additionalMetadata);
        metadata.put("documentId", documentId);
        metadata.put("filename", filename);
        metadata.put("ingestionTime", System.currentTimeMillis());
        
        // Split into chunks
        List<Document> chunks = documentSplitter.split(content, chunkSize, chunkOverlap);
        log.info("Document {} split into {} chunks", filename, chunks.size());
        
        // Process and store chunks
        List<Document> processedChunks = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            Map<String, Object> chunkMetadata = new HashMap<>(metadata);
            chunkMetadata.put("chunkIndex", i);
            chunkMetadata.put("totalChunks", chunks.size());
            
            Document processedChunk = new Document(
                documentId + "_chunk_" + i,
                chunk.getContent(),
                chunkMetadata
            );
            processedChunks.add(processedChunk);
        }
        
        // Generate embeddings and store
        vectorStore.addDocuments(processedChunks);
        
        // Update registry
        documentRegistry.put(documentId, new DocumentMetadata(
            documentId,
            filename,
            file.getSize(),
            chunks.size(),
            detectFileType(file),
            metadata
        ));
        totalSize.addAndGet(file.getSize());
        
        log.info("Successfully ingested document: {} (ID: {})", filename, documentId);
        return documentId;
    }
    
    public void deleteDocument(String documentId) {
        DocumentMetadata metadata = documentRegistry.remove(documentId);
        if (metadata != null) {
            // Remove from vector store
            for (int i = 0; i < metadata.getChunkCount(); i++) {
                vectorStore.removeDocument(documentId + "_chunk_" + i);
            }
            
            // Delete file
            try {
                Path filePath = documentStoragePath.resolve(documentId);
                Files.deleteIfExists(filePath);
            } catch (IOException e) {
                log.error("Error deleting file for document: {}", documentId, e);
            }
            
            totalSize.addAndGet(-metadata.getSize());
            log.info("Deleted document: {} (ID: {})", metadata.getFilename(), documentId);
        }
    }
    
    public DocumentStats getDocumentStats() {
        Map<String, Long> typeCount = new HashMap<>();
        for (DocumentMetadata metadata : documentRegistry.values()) {
            typeCount.merge(metadata.getFileType(), 1L, Long::sum);
        }
        
        long totalChunks = documentRegistry.values().stream()
            .mapToLong(DocumentMetadata::getChunkCount)
            .sum();
        
        return new DocumentStats(
            documentRegistry.size(),
            totalChunks,
            typeCount,
            totalSize.get() / (1024 * 1024) // Convert to MB
        );
    }
    
    private Path saveFile(MultipartFile file, String documentId) throws IOException {
        Path filePath = documentStoragePath.resolve(documentId);
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
        }
        return filePath;
    }
    
    private String extractContent(InputStream inputStream) throws IOException {
        try {
            return tika.parseToString(inputStream);
        } catch (Exception e) {
            throw new IOException("Failed to extract content from document", e);
        }
    }
    
    private Map<String, Object> extractMetadata(MultipartFile file, Map<String, String> additionalMetadata) {
        Map<String, Object> metadata = new HashMap<>();
        
        // Extract metadata using Tika
        try (InputStream inputStream = file.getInputStream()) {
            Metadata tikaMetadata = new Metadata();
            tika.parse(inputStream, tikaMetadata);
            
            for (String name : tikaMetadata.names()) {
                metadata.put(name, tikaMetadata.get(name));
            }
        } catch (Exception e) {
            log.warn("Failed to extract metadata from document", e);
        }
        
        // Add additional metadata
        if (additionalMetadata != null) {
            metadata.putAll(additionalMetadata);
        }
        
        // Add basic metadata
        metadata.put("contentType", file.getContentType());
        metadata.put("size", file.getSize());
        
        return metadata;
    }
    
    private String detectFileType(MultipartFile file) {
        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();
        
        if (filename != null) {
            if (filename.endsWith(".pdf")) return "pdf";
            if (filename.endsWith(".docx")) return "docx";
            if (filename.endsWith(".txt")) return "txt";
            if (filename.endsWith(".html")) return "html";
            if (filename.endsWith(".md")) return "markdown";
            if (filename.endsWith(".json")) return "json";
            if (filename.endsWith(".csv")) return "csv";
        }
        
        if (contentType != null) {
            if (contentType.contains("pdf")) return "pdf";
            if (contentType.contains("word")) return "docx";
            if (contentType.contains("text")) return "txt";
            if (contentType.contains("html")) return "html";
        }
        
        return "unknown";
    }
    
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class DocumentMetadata {
        private String documentId;
        private String filename;
        private long size;
        private int chunkCount;
        private String fileType;
        private Map<String, Object> metadata;
    }
}
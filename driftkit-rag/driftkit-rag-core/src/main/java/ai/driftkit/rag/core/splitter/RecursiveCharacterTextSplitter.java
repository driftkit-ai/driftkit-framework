package ai.driftkit.rag.core.splitter;

import ai.driftkit.common.utils.DocumentSplitter;
import ai.driftkit.rag.core.domain.LoadedDocument;
import ai.driftkit.vector.core.domain.Document;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Text splitter that uses recursive character-based splitting with overlap.
 * Wraps the existing DocumentSplitter from driftkit-common.
 */
@Slf4j
@Builder
@RequiredArgsConstructor
public class RecursiveCharacterTextSplitter implements TextSplitter {
    
    @Builder.Default
    private final int chunkSize = 512;
    
    @Builder.Default
    private final int chunkOverlap = 128;
    
    @Builder.Default
    private final boolean preserveMetadata = true;
    
    @Builder.Default
    private final boolean addChunkMetadata = true;
    
    /**
     * Split a loaded document into chunks.
     */
    @Override
    public List<Document> split(LoadedDocument document) {
        if (document.getContent() == null || document.getContent().isEmpty()) {
            log.warn("Document {} has no content to split", document.getId());
            return List.of();
        }
        
        // Use existing DocumentSplitter
        List<String> chunks = DocumentSplitter.splitDocumentIntoShingles(
            document.getContent(), 
            chunkSize, 
            chunkOverlap
        );
        
        log.debug("Split document {} into {} chunks", document.getId(), chunks.size());
        
        List<Document> documents = new ArrayList<>();
        
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            String chunkId = document.getId() + "-chunk-" + i;
            
            // Create metadata for chunk
            Map<String, Object> metadata = new HashMap<>();
            
            // Preserve original document metadata if requested
            if (preserveMetadata && document.getMetadata() != null) {
                metadata.putAll(document.getMetadata());
            }
            
            // Add chunk-specific metadata
            if (addChunkMetadata) {
                metadata.put("sourceDocumentId", document.getId());
                metadata.put("sourceDocumentSource", document.getSource());
                metadata.put("chunkIndex", i);
                metadata.put("totalChunks", chunks.size());
                metadata.put("chunkSize", chunk.length());
                metadata.put("splitterType", "recursive_character");
                metadata.put("splitterChunkSize", chunkSize);
                metadata.put("splitterOverlap", chunkOverlap);
            }
            
            Document doc = new Document(
                chunkId,
                null, // No embedding yet
                chunk,
                metadata
            );
            
            documents.add(doc);
        }
        
        return documents;
    }
    
    /**
     * Create a splitter with default settings.
     */
    public static RecursiveCharacterTextSplitter withDefaults() {
        return RecursiveCharacterTextSplitter.builder().build();
    }
    
    /**
     * Create a splitter with custom chunk size.
     */
    public static RecursiveCharacterTextSplitter withChunkSize(int chunkSize) {
        return RecursiveCharacterTextSplitter.builder()
            .chunkSize(chunkSize)
            .build();
    }
    
    /**
     * Create a splitter with custom chunk size and overlap.
     */
    public static RecursiveCharacterTextSplitter withChunkSizeAndOverlap(int chunkSize, int overlap) {
        return RecursiveCharacterTextSplitter.builder()
            .chunkSize(chunkSize)
            .chunkOverlap(overlap)
            .build();
    }
}
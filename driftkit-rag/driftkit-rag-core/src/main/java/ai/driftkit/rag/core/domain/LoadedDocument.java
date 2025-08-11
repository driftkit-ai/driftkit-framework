package ai.driftkit.rag.core.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a document loaded from a source before processing.
 * Contains raw content and metadata about the document.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoadedDocument {
    
    /**
     * Unique identifier for the document
     */
    private String id;
    
    /**
     * Raw content of the document
     */
    private String content;
    
    /**
     * Source of the document (e.g., file path, URL)
     */
    private String source;
    
    /**
     * MIME type of the document
     */
    private String mimeType;
    
    /**
     * Additional metadata about the document
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
    
    /**
     * Add metadata entry
     */
    public LoadedDocument withMetadata(String key, Object value) {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        metadata.put(key, value);
        return this;
    }
}
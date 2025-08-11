package ai.driftkit.rag.core.loader;

import ai.driftkit.rag.core.domain.LoadedDocument;

import java.util.List;
import java.util.stream.Stream;

/**
 * Interface for loading documents from various sources.
 * Implementations should handle specific document sources like file systems, URLs, databases, etc.
 */
public interface DocumentLoader {
    
    /**
     * Load all documents from the configured source.
     * 
     * @return List of loaded documents
     * @throws Exception if loading fails
     */
    List<LoadedDocument> load() throws Exception;
    
    /**
     * Load documents as a stream for memory-efficient processing.
     * 
     * @return Stream of loaded documents
     * @throws Exception if loading fails
     */
    default Stream<LoadedDocument> loadStream() throws Exception {
        return load().stream();
    }
    
    /**
     * Check if the loader supports lazy loading via streams.
     * 
     * @return true if streaming is supported
     */
    default boolean supportsStreaming() {
        return false;
    }
}
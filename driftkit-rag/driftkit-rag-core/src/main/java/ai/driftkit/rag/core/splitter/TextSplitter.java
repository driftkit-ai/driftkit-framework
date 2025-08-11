package ai.driftkit.rag.core.splitter;

import ai.driftkit.rag.core.domain.LoadedDocument;
import ai.driftkit.vector.core.domain.Document;

import java.util.List;

/**
 * Interface for splitting text documents into smaller chunks.
 * Implementations should handle different splitting strategies.
 */
public interface TextSplitter {
    
    /**
     * Split a loaded document into multiple chunks.
     * Each chunk will become a separate document in the vector store.
     * 
     * @param document The document to split
     * @return List of document chunks ready for embedding
     */
    List<Document> split(LoadedDocument document);
    
    /**
     * Split multiple documents into chunks.
     * 
     * @param documents The documents to split
     * @return List of all document chunks
     */
    default List<Document> splitAll(List<LoadedDocument> documents) {
        return documents.stream()
                .flatMap(doc -> split(doc).stream())
                .toList();
    }
}
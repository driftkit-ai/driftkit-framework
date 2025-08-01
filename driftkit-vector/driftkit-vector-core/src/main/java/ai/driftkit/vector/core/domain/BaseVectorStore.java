package ai.driftkit.vector.core.domain;

import ai.driftkit.config.EtlConfig.VectorStoreConfig;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

/**
 * Base Vector Store interface containing all methods except findRelevant.
 * This interface defines the core operations for document management in a vector store.
 */
public interface BaseVectorStore {
    void configure(VectorStoreConfig config) throws Exception;

    boolean supportsStoreName(String storeName);

    /**
     * Add documents with embeddings. Returns list of IDs of the stored documents.
     */
    default String addDocument(String index, Document document) throws Exception {
        List<String> result = addDocuments(index, List.of(document));

        if (CollectionUtils.isEmpty(result)) {
            return null;
        }

        return result.getFirst();
    }

    /**
     * Add documents with embeddings. Returns list of IDs of the stored documents.
     */
    List<String> addDocuments(String index, List<Document> documents) throws Exception;

    /**
     * Update a document (including its vector) for a given ID.
     */
    void updateDocument(String id, String index, Document document) throws Exception;

    /**
     * Delete a document by ID.
     */
    void deleteDocument(String id, String index) throws Exception;

    /**
     * Read a document by ID.
     */
    Document readDocument(String id, String index) throws Exception;
}
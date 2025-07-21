package ai.driftkit.vector.core.domain;

import ai.driftkit.config.EtlConfig.VectorStoreConfig;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.Map;

/**
 * Vector Store interface inspired by LangChain vector stores
 *
 * Methods include:
 * - addDocuments: add documents with embeddings included
 * - similaritySearchByVectorWithScore: given a vector, return documents with scores using a Map
 * - updateDocument, deleteDocument, readDocument
 *
 * Note: Vectors are assumed to be included within the Document.
 */
public interface VectorStore {
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
     * Perform similarity search given a vector, returning documents with scores in a Map,
     * where the key is the Document and the value is the score.
     */
    DocumentsResult findRelevant(String index, float[] embedding, int k) throws Exception;

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
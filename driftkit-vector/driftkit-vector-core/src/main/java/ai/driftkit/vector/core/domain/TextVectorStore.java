package ai.driftkit.vector.core.domain;

/**
 * Vector Store interface that supports similarity search using text queries.
 * This interface extends BaseVectorStore and adds the ability to search using
 * text strings, which are typically converted to embeddings internally.
 */
public interface TextVectorStore extends BaseVectorStore {
    
    /**
     * Perform similarity search given a text query, returning documents with scores,
     * where the key is the Document and the value is the similarity score.
     * The text query will be converted to an embedding internally.
     * 
     * @param index the index to search in
     * @param query the text query to search with
     * @param k the number of top results to return
     * @return DocumentsResult containing documents and their similarity scores
     * @throws Exception if an error occurs during search
     */
    DocumentsResult findRelevant(String index, String query, int k) throws Exception;
}
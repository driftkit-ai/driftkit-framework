package ai.driftkit.vector.core.domain;

/**
 * Vector Store interface that supports similarity search using embedding vectors.
 * This interface extends BaseVectorStore and adds the ability to search using
 * float array embeddings.
 */
public interface EmbeddingVectorStore extends BaseVectorStore {
    
    /**
     * Perform similarity search given a vector embedding, returning documents with scores,
     * where the key is the Document and the value is the similarity score.
     * 
     * @param index the index to search in
     * @param embedding the embedding vector to search with
     * @param k the number of top results to return
     * @return DocumentsResult containing documents and their similarity scores
     * @throws Exception if an error occurs during search
     */
    DocumentsResult findRelevant(String index, float[] embedding, int k) throws Exception;
}
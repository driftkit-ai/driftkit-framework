package ai.driftkit.embedding.core.domain;

/**
 * Pooling mode for BERT embeddings.
 * Determines how to aggregate token embeddings into a single vector.
 */
public enum PoolingMode {
    /**
     * Use the CLS token embedding as the sentence embedding.
     * The CLS token is the first token and is trained to represent the entire sequence.
     */
    CLS,
    
    /**
     * Average all token embeddings to create the sentence embedding.
     * This often provides better results than CLS for similarity tasks.
     */
    MEAN,
    
    /**
     * Take the maximum value for each dimension across all token embeddings.
     */
    MAX
}
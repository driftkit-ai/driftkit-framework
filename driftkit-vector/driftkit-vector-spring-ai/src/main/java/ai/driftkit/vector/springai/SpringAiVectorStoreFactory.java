package ai.driftkit.vector.springai;

import ai.driftkit.vector.core.domain.TextVectorStore;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Factory for creating Spring AI vector store adapters.
 */
@Slf4j
public class SpringAiVectorStoreFactory {
    
    /**
     * Creates a DriftKit TextVectorStore adapter for the given Spring AI VectorStore.
     * 
     * @param springAiVectorStore The Spring AI vector store implementation
     * @param storeName The name to identify this store (e.g., "pinecone", "qdrant", "weaviate")
     * @return A DriftKit TextVectorStore adapter
     */
    public static TextVectorStore create(org.springframework.ai.vectorstore.VectorStore springAiVectorStore, String storeName) {
        if (springAiVectorStore == null) {
            throw new IllegalArgumentException("Spring AI VectorStore cannot be null");
        }
        
        if (StringUtils.isEmpty(storeName)) {
            throw new IllegalArgumentException("Store name cannot be empty");
        }
        
        log.info("Creating Spring AI VectorStore adapter for store: {}", storeName);
        return new SpringAiVectorStoreAdapter(springAiVectorStore, storeName);
    }
}
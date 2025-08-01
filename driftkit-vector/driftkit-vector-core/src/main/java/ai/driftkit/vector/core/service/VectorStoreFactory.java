package ai.driftkit.vector.core.service;

import ai.driftkit.config.EtlConfig.VectorStoreConfig;
import ai.driftkit.vector.core.domain.BaseVectorStore;
import ai.driftkit.vector.core.domain.EmbeddingVectorStore;
import ai.driftkit.vector.core.domain.TextVectorStore;

/**
 * This factory creates instances of vector stores from config.
 */

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This factory creates instances of vector stores from config.
 */
public class VectorStoreFactory {
    private static final Map<String, BaseVectorStore> stores = new ConcurrentHashMap<>();

    public static BaseVectorStore fromConfig(VectorStoreConfig config) throws Exception {
        if (config == null || config.getName() == null) {
            throw new IllegalArgumentException("Configuration and storeName must not be null");
        }

        return stores.computeIfAbsent(config.getName(), name -> {
            String storeName = config.getName();
            
            // Try to load EmbeddingVectorStore implementations
            ServiceLoader<EmbeddingVectorStore> embeddingLoader = ServiceLoader.load(EmbeddingVectorStore.class);
            for (EmbeddingVectorStore store : embeddingLoader) {
                if (store.supportsStoreName(storeName)) {
                    try {
                        store.configure(config);
                        return store;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            
            // Try to load TextVectorStore implementations
            ServiceLoader<TextVectorStore> textLoader = ServiceLoader.load(TextVectorStore.class);
            for (TextVectorStore store : textLoader) {
                if (store.supportsStoreName(storeName)) {
                    try {
                        store.configure(config);
                        return store;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }

            throw new IllegalArgumentException("Unknown or unavailable vector store: " + storeName);
        });
    }
}
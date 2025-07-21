package ai.driftkit.vector.core.service;

import ai.driftkit.config.EtlConfig.VectorStoreConfig;
import ai.driftkit.vector.core.domain.VectorStore;

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
    private static final Map<String, VectorStore> stores = new ConcurrentHashMap<>();

    public static VectorStore fromConfig(VectorStoreConfig config) throws Exception {
        if (config == null || config.getName() == null) {
            throw new IllegalArgumentException("Configuration and storeName must not be null");
        }

        return stores.computeIfAbsent(config.getName(), name -> {
            String storeName = config.getName();
            ServiceLoader<VectorStore> loader = ServiceLoader.load(VectorStore.class);

            for (VectorStore store : loader) {
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
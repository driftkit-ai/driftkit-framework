package ai.driftkit.clients.core;

import ai.driftkit.config.EtlConfig.VaultConfig;
import ai.driftkit.common.domain.client.ModelClient;
import ai.driftkit.common.utils.Tokenizer;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This factory creates instances of model clients from config.
 * Uses ServiceLoader to dynamically discover available model client implementations.
 */
public class ModelClientFactory {
    private static final Map<String, ModelClient<?>> clients = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public static <T> ModelClient<T> fromConfig(VaultConfig config) {
        if (config == null || config.getName() == null) {
            throw new IllegalArgumentException("Configuration and client name must not be null");
        }

        return (ModelClient<T>) clients.computeIfAbsent(config.getName(), name -> {
            String clientName = config.getName();
            ServiceLoader<ModelClient> loader = ServiceLoader.load(ModelClient.class);

            for (ModelClient<?> client : loader) {
                if (!(client instanceof ModelClient.ModelClientInit)) {
                    continue;
                }
                
                if (!supportsClientName(client, clientName)) {
                    continue;
                }

                try {
                    ModelClient.ModelClientInit initClient = (ModelClient.ModelClientInit) client;
                    ModelClient<?> configuredClient = initClient.init(config);
                    
                    if (config.isTracing()) {
                        return new TraceableModelClient<>(configuredClient);
                    }
                    
                    return configuredClient;
                } catch (Exception e) {
                    throw new RuntimeException("Failed to initialize client: " + clientName, e);
                }
            }

            throw new IllegalArgumentException("Unknown or unavailable model client: " + clientName);
        });
    }

    @SuppressWarnings("unchecked")
    public static <T> ModelClient<T> fromConfig(VaultConfig config, Tokenizer tokenizer) {
        ModelClient<T> baseClient = fromConfig(config);
        
        if (config.isTracing() && !(baseClient instanceof TraceableModelClient)) {
            return new TraceableModelClient<>(baseClient, tokenizer);
        }
        
        return baseClient;
    }

    public static <T> TraceableModelClient<T> createTraceable(ModelClient<T> delegate, Tokenizer tokenizer) {
        return new TraceableModelClient<>(delegate, tokenizer);
    }

    /**
     * Checks if the client supports the given client name.
     */
    private static boolean supportsClientName(ModelClient<?> client, String clientName) {
        return client.getClass().getSimpleName().toLowerCase().contains(clientName.toLowerCase());
    }
}
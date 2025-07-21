package ai.driftkit.embedding.core.service;

import ai.driftkit.config.EtlConfig.EmbeddingServiceConfig;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.ServiceLoader;

public class EmbeddingFactory {
    public static EmbeddingModel fromName(String name, Map<String, String> config) throws Exception {
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("Name must not be null");
        }

        ServiceLoader<EmbeddingModel> loader = ServiceLoader.load(EmbeddingModel.class);

        for (EmbeddingModel store : loader) {
            if (store.supportsName(name)) {
                store.configure(new EmbeddingServiceConfig(name, config));
                return store;
            }
        }

        throw new IllegalArgumentException("Unknown or unavailable prompt service: " + name);
    }
}

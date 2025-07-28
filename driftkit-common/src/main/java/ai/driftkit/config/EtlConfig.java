package ai.driftkit.config;

import lombok.*;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Data
public class EtlConfig {
    public static final String DIMENSION = "dimension";
    public static final String API_KEY = "apiKey";
    public static final String MODEL_NAME = "modelName";
    public static final String MODEL_PATH = "modelPath";
    public static final String TOKENIZER_PATH = "tokenizerPath";
    public static final String ENDPOINT = "endpoint";
    public static final String HOST = "host";
    public static final String BASE_QUERY = "baseQuery";
    public static final String VECTOR_STORE_STORING_THREADS = "threadsVectorStore";

    private VectorStoreConfig vectorStore;
    private EmbeddingServiceConfig embedding;
    private PromptServiceConfig promptService;
    private YoutubeProxyConfig youtubeProxy;
    private List<VaultConfig> vault;

    public Optional<VaultConfig> getModelConfig(String name) {
        return vault.stream().filter(e -> e.getName().equals(name)).findAny();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class YoutubeProxyConfig {
        String host;
        int port;
        String username;
        String password;
    }

    public static class VectorStoreConfig extends GenericConfig {
        @Builder
        public VectorStoreConfig(String name, Map<String, String> config) {
            super(name, config);
        }
    }

    public static class PromptServiceConfig extends GenericConfig {
        @Builder
        public PromptServiceConfig(String name, Map<String, String> config) {
            super(name, config);
        }
    }

    public static class EmbeddingServiceConfig extends GenericConfig {

        @Builder
        public EmbeddingServiceConfig(String name, Map<String, String> config) {
            super(name, config);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GenericConfig {
        private String name;
        private Map<String, String> config;

        public String get(String name) {
            return config.get(name);
        }

        public String get(String name, String def) {
            return config.getOrDefault(name, def);
        }

        public Integer getInt(String name) {
            return getInt(name, null);
        }

        public Integer getInt(String name, Integer def) {
            String val = get(name);

            if (StringUtils.isBlank(val)) {
                return def;
            }

            return Integer.parseInt(val);
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VaultConfig {
        private String apiKey;
        private String name;
        private String model;
        private String modelMini;
        private String imageModel;
        private String imageQuality;
        private String imageSize;
        private String baseUrl;
        private List<String> stop;
        private int maxTokens;
        private double temperature;
        private boolean jsonObject;
        private boolean tracing;
    }
}
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

    /**
     * Finds a vault entry by its logical {@code name}; when no entry has that name, falls back to the
     * first entry whose provider {@code type} matches, so lookups by provider id (e.g. "openai") keep
     * working for configs that use logical names with an explicit type.
     */
    public Optional<VaultConfig> getModelConfig(String name) {
        if (vault == null || name == null) {
            return Optional.empty();
        }
        Optional<VaultConfig> byName = vault.stream().filter(e -> name.equals(e.getName())).findFirst();
        if (byName.isPresent()) {
            return byName;
        }
        return vault.stream().filter(e -> name.equalsIgnoreCase(e.getType())).findFirst();
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
        @ToString.Exclude
        private String apiKey;
        private String name;
        private String type;
        private String model;
        private String modelMini;
        private String imageModel;
        private String imageQuality;
        private String imageSize;
        private String baseUrl;
        private List<String> stop;
        private Integer maxTokens;
        private double temperature;
        private boolean jsonObject;
        private boolean tracing;
        /**
         * Connection timeout in seconds. Default: 30
         */
        private Integer connectTimeout;
        /**
         * Read timeout in seconds. Default: 300 (5 minutes).
         * Increase for long-running requests like large content generation.
         */
        private Integer readTimeout;
        /**
         * Vertex AI project ID. When set, uses Vertex AI (OAuth2 ADC) instead of API key auth.
         */
        private String vertexProject;
        /**
         * Vertex AI location. "global" uses generativelanguage.googleapis.com with Bearer auth.
         * Regional (e.g. "us-central1") uses {location}-aiplatform.googleapis.com.
         */
        private String vertexLocation;
        /**
         * Vertex express: authenticate to Vertex with an API key instead of a service account.
         *
         * <p>A third access type, not a variation of the other two. Express keys are issued in
         * the Vertex console and are accepted ONLY on {@code aiplatform.googleapis.com} under
         * {@code /v1/publishers/google/models/...}; the same key on
         * {@code generativelanguage.googleapis.com} is rejected, and {@code /v1beta/models/...}
         * on the Vertex host answers 404. Verified against the live API on 2026-08-31.
         *
         * <p>Set together with {@code apiKey} and WITHOUT {@code vertexProject}: a project makes
         * the client take the service-account path, where the key is not used at all.
         */
        private boolean vertexExpress;
    }
}
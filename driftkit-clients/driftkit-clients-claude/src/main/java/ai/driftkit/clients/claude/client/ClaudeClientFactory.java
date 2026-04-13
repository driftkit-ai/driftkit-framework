package ai.driftkit.clients.claude.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import feign.*;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.slf4j.Slf4jLogger;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class ClaudeClientFactory {

    private static final String DEFAULT_BASE_URL = "https://api.anthropic.com";
    private static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 30;
    private static final int DEFAULT_READ_TIMEOUT_SECONDS = 300; // 5 minutes for long-running requests

    public static ClaudeApiClient createClient(String apiKey) {
        return createClient(apiKey, null, null, null);
    }

    public static ClaudeApiClient createClient(String apiKey, String baseUrl) {
        return createClient(apiKey, baseUrl, null, null);
    }

    /**
     * Creates a Claude API client with configurable timeouts.
     *
     * @param apiKey API key for authentication
     * @param baseUrl Base URL (default: https://api.anthropic.com)
     * @param connectTimeoutSeconds Connection timeout in seconds (default: 30)
     * @param readTimeoutSeconds Read timeout in seconds (default: 300 / 5 minutes)
     * @return Configured ClaudeApiClient
     */
    public static ClaudeApiClient createClient(String apiKey, String baseUrl,
                                                Integer connectTimeoutSeconds,
                                                Integer readTimeoutSeconds) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = DEFAULT_BASE_URL;
        }

        int connectTimeout = (connectTimeoutSeconds != null && connectTimeoutSeconds > 0)
                ? connectTimeoutSeconds : DEFAULT_CONNECT_TIMEOUT_SECONDS;
        int readTimeout = (readTimeoutSeconds != null && readTimeoutSeconds > 0)
                ? readTimeoutSeconds : DEFAULT_READ_TIMEOUT_SECONDS;

        log.debug("Creating Claude client with connectTimeout={}s, readTimeout={}s", connectTimeout, readTimeout);

        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

        return Feign.builder()
                .encoder(new JacksonEncoder(objectMapper))
                .decoder(new JacksonDecoder(objectMapper))
                .logger(new Slf4jLogger(ClaudeApiClient.class))
                .logLevel(Logger.Level.BASIC)
                .requestInterceptor(new ClaudeRequestInterceptor(apiKey))
                .retryer(new Retryer.Default(100, 1000, 3))
                .options(new Request.Options(connectTimeout * 1000, readTimeout * 1000))
                .target(ClaudeApiClient.class, baseUrl);
    }
    
    private static class ClaudeRequestInterceptor implements RequestInterceptor {
        private final String apiKey;
        
        public ClaudeRequestInterceptor(String apiKey) {
            this.apiKey = apiKey;
        }
        
        @Override
        public void apply(RequestTemplate template) {
            template.header("x-api-key", apiKey);
            // Always include beta header for structured outputs support
            // It has no effect on non-structured requests
            template.header("anthropic-beta", "structured-outputs-2025-11-13");
        }
    }
}
package ai.driftkit.clients.gemini.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import feign.Feign;
import feign.Request;
import feign.RequestInterceptor;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.slf4j.Slf4jLogger;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GeminiClientFactory {

    private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";
    private static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 30;
    private static final int DEFAULT_READ_TIMEOUT_SECONDS = 300; // 5 minutes for long-running requests

    public static GeminiApiClient createClient(String apiKey, String baseUrl) {
        return createClient(apiKey, baseUrl, null, null);
    }

    public static GeminiApiClient createClient(String apiKey) {
        return createClient(apiKey, DEFAULT_BASE_URL, null, null);
    }

    /**
     * Creates a Gemini API client with configurable timeouts.
     *
     * @param apiKey API key for authentication
     * @param baseUrl Base URL (default: https://generativelanguage.googleapis.com)
     * @param connectTimeoutSeconds Connection timeout in seconds (default: 30)
     * @param readTimeoutSeconds Read timeout in seconds (default: 300 / 5 minutes)
     * @return Configured GeminiApiClient
     */
    public static GeminiApiClient createClient(String apiKey, String baseUrl,
                                                Integer connectTimeoutSeconds,
                                                Integer readTimeoutSeconds) {
        RequestInterceptor apiKeyInterceptor = requestTemplate ->
                requestTemplate.header("x-goog-api-key", apiKey);
        return createClient(apiKeyInterceptor, baseUrl, connectTimeoutSeconds, readTimeoutSeconds);
    }

    /**
     * Creates a Gemini API client with a custom auth interceptor (e.g. Vertex AI Bearer token).
     */
    public static GeminiApiClient createClient(RequestInterceptor authInterceptor, String baseUrl,
                                                Integer connectTimeoutSeconds,
                                                Integer readTimeoutSeconds) {
        int connectTimeout = (connectTimeoutSeconds != null && connectTimeoutSeconds > 0)
                ? connectTimeoutSeconds : DEFAULT_CONNECT_TIMEOUT_SECONDS;
        int readTimeout = (readTimeoutSeconds != null && readTimeoutSeconds > 0)
                ? readTimeoutSeconds : DEFAULT_READ_TIMEOUT_SECONDS;

        log.debug("Creating Gemini client with connectTimeout={}s, readTimeout={}s", connectTimeout, readTimeout);

        ObjectMapper objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE);

        return Feign.builder()
                .encoder(new JacksonEncoder(objectMapper))
                .decoder(new JacksonDecoder(objectMapper))
                .logger(new Slf4jLogger(GeminiApiClient.class))
                .logLevel(feign.Logger.Level.BASIC)
                .requestInterceptor(authInterceptor)
                .options(new Request.Options(connectTimeout * 1000, readTimeout * 1000))
                .target(GeminiApiClient.class, baseUrl != null ? baseUrl : DEFAULT_BASE_URL);
    }
}

package ai.driftkit.clients.gemini.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import feign.Feign;
import feign.RequestInterceptor;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.slf4j.Slf4jLogger;

public class GeminiClientFactory {
    
    private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";
    
    public static GeminiApiClient createClient(String apiKey, String baseUrl) {
        ObjectMapper objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE);
        
        RequestInterceptor apiKeyInterceptor = requestTemplate -> {
            // Gemini uses API key in header
            requestTemplate.header("x-goog-api-key", apiKey);
        };
        
        return Feign.builder()
                .encoder(new JacksonEncoder(objectMapper))
                .decoder(new JacksonDecoder(objectMapper))
                .logger(new Slf4jLogger(GeminiApiClient.class))
                .logLevel(feign.Logger.Level.BASIC)
                .requestInterceptor(apiKeyInterceptor)
                .target(GeminiApiClient.class, baseUrl != null ? baseUrl : DEFAULT_BASE_URL);
    }
    
    public static GeminiApiClient createClient(String apiKey) {
        return createClient(apiKey, DEFAULT_BASE_URL);
    }
}
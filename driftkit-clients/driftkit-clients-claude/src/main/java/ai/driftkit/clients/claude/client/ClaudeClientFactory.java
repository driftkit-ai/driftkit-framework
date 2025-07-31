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
    
    public static ClaudeApiClient createClient(String apiKey) {
        return createClient(apiKey, null);
    }
    
    public static ClaudeApiClient createClient(String apiKey, String baseUrl) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = DEFAULT_BASE_URL;
        }
        
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
                .options(new Request.Options(30000, 60000)) // 30s connect, 60s read timeout
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
        }
    }
}
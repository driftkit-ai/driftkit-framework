package ai.driftkit.workflow.engine.spring.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import feign.Logger;
import feign.RequestInterceptor;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

/**
 * Feign configuration for workflow service clients.
 * Provides custom encoding, decoding, and error handling.
 */
@Slf4j
@Configuration
public class WorkflowFeignConfiguration {
    
    @Value("${driftkit.workflow.client.log-level:BASIC}")
    private String logLevel;
    
    /**
     * Configure Feign logging level.
     */
    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.valueOf(logLevel);
    }
    
    /**
     * Configure Jackson encoder for Feign.
     */
    @Bean
    public Encoder feignEncoder() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return new JacksonEncoder(objectMapper);
    }
    
    /**
     * Configure Jackson decoder for Feign.
     */
    @Bean
    public Decoder feignDecoder() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return new JacksonDecoder(objectMapper);
    }
    
    /**
     * Configure custom error decoder.
     */
    @Bean
    public ErrorDecoder errorDecoder() {
        return new WorkflowErrorDecoder();
    }
    
    /**
     * Add authentication interceptor if configured.
     */
    @Bean
    @ConditionalOnProperty(name = "driftkit.workflow.client.auth.enabled", havingValue = "true")
    public RequestInterceptor authenticationInterceptor(
            @Value("${driftkit.workflow.client.auth.token:}") String authToken,
            @Value("${driftkit.workflow.client.auth.header:Authorization}") String authHeader
    ) {
        return requestTemplate -> {
            if (authToken != null && !authToken.isEmpty()) {
                requestTemplate.header(authHeader, "Bearer " + authToken);
            }
        };
    }
    
    /**
     * Add custom headers interceptor.
     */
    @Bean
    public RequestInterceptor customHeadersInterceptor(
            @Value("${driftkit.workflow.client.service-name:workflow-client}") String serviceName
    ) {
        return requestTemplate -> {
            requestTemplate.header("X-Client-Service", serviceName);
            requestTemplate.header("X-Client-Version", "1.0.0");
        };
    }
    
    /**
     * Custom error decoder for workflow service responses.
     */
    public static class WorkflowErrorDecoder implements ErrorDecoder {
        
        private final ErrorDecoder defaultErrorDecoder = new Default();
        
        @Override
        public Exception decode(String methodKey, feign.Response response) {
            HttpStatus status = HttpStatus.valueOf(response.status());
            
            if (status.is4xxClientError()) {
                return new WorkflowClientException(
                    String.format("Client error calling %s: %s", methodKey, response.reason()),
                    response.status()
                );
            }
            
            if (status.is5xxServerError()) {
                return new WorkflowServerException(
                    String.format("Server error calling %s: %s", methodKey, response.reason()),
                    response.status()
                );
            }
            
            return defaultErrorDecoder.decode(methodKey, response);
        }
    }
    
    /**
     * Exception for client errors (4xx).
     */
    public static class WorkflowClientException extends RuntimeException {
        private final int status;
        
        public WorkflowClientException(String message, int status) {
            super(message);
            this.status = status;
        }
        
        public int getStatus() {
            return status;
        }
    }
    
    /**
     * Exception for server errors (5xx).
     */
    public static class WorkflowServerException extends RuntimeException {
        private final int status;
        
        public WorkflowServerException(String message, int status) {
            super(message);
            this.status = status;
        }
        
        public int getStatus() {
            return status;
        }
    }
}
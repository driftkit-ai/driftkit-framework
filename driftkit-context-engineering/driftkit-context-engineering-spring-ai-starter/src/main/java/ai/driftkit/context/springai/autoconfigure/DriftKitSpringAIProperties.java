package ai.driftkit.context.springai.autoconfigure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for DriftKit Spring AI integration
 */
@Data
@ConfigurationProperties(prefix = "driftkit.spring-ai")
public class DriftKitSpringAIProperties {
    
    /**
     * Application name for tracing
     */
    private String applicationName = "spring-ai-app";
    
    /**
     * Default system message for ChatClient
     */
    private String defaultSystemMessage;
    
    /**
     * Tracing configuration
     */
    private TracingProperties tracing = new TracingProperties();
    
    /**
     * Memory configuration
     */
    private MemoryProperties memory = new MemoryProperties();
    
    /**
     * Logging configuration
     */
    private LoggingProperties logging = new LoggingProperties();
    
    /**
     * Chat client configuration
     */
    private ChatClientProperties chatClient = new ChatClientProperties();
    
    /**
     * Enhanced chat client configuration
     */
    private EnhancedChatClientProperties enhancedChatClient = new EnhancedChatClientProperties();
    
    @Data
    public static class TracingProperties {
        /**
         * Enable tracing for Spring AI calls
         */
        private boolean enabled = true;
    }
    
    @Data
    public static class MemoryProperties {
        /**
         * Enable chat memory advisor
         */
        private boolean enabled = false;
        
        /**
         * Maximum messages to keep in memory
         */
        private int maxMessages = 20;
    }
    
    @Data
    public static class LoggingProperties {
        /**
         * Enable logging advisor
         */
        private boolean enabled = false;
    }
    
    @Data
    public static class ChatClientProperties {
        /**
         * Enable DriftKitChatClient bean creation
         */
        private boolean enabled = true;
    }
    
    @Data
    public static class EnhancedChatClientProperties {
        /**
         * Enable enhanced ChatClient bean creation
         */
        private boolean enabled = false;
    }
}
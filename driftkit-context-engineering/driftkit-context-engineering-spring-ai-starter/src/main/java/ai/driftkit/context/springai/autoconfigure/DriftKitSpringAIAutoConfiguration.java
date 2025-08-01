package ai.driftkit.context.springai.autoconfigure;

import ai.driftkit.context.core.service.PromptService;
import ai.driftkit.context.springai.*;
import ai.driftkit.workflows.core.agent.RequestTracingProvider;
import ai.driftkit.workflows.core.agent.RequestTracingRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for DriftKit Spring AI integration.
 * Automatically configures:
 * - DriftKitPromptProvider for using DriftKit prompts with Spring AI
 * - DriftKitTracingAdvisor for tracing Spring AI calls
 * - SpringAIChatClientFactory for creating enhanced ChatClient instances
 * - DriftKitChatClient for convenient prompt-based interactions
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass({ChatModel.class, PromptService.class})
@ConditionalOnBean(PromptService.class)
@EnableConfigurationProperties(DriftKitSpringAIProperties.class)
public class DriftKitSpringAIAutoConfiguration {
    
    /**
     * Create DriftKitPromptProvider for accessing DriftKit prompts
     */
    @Bean
    @ConditionalOnMissingBean
    public DriftKitPromptProvider driftKitPromptProvider(PromptService promptService) {
        log.info("Creating DriftKitPromptProvider");
        return new DriftKitPromptProvider(promptService);
    }
    
    /**
     * Create DriftKitTracingAdvisor for tracing Spring AI calls
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "driftkit.spring-ai",
        name = "tracing.enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    public DriftKitTracingAdvisor driftKitTracingAdvisor(DriftKitSpringAIProperties properties) {
        log.info("Creating DriftKitTracingAdvisor");
        
        RequestTracingProvider tracingProvider = RequestTracingRegistry.getInstance();
        if (tracingProvider == null) {
            log.warn("No RequestTracingProvider found in registry, tracing will be disabled");
            return new DriftKitTracingAdvisor(null, properties.getApplicationName());
        }
        
        return new DriftKitTracingAdvisor(tracingProvider, properties.getApplicationName());
    }
    
    /**
     * Create SpringAIChatClientFactory for building enhanced ChatClient instances
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ChatModel.class)
    public SpringAIChatClientFactory springAIChatClientFactory(
            ChatModel chatModel,
            PromptService promptService,
            DriftKitSpringAIProperties properties) {
        
        log.info("Creating SpringAIChatClientFactory");
        
        var builder = SpringAIChatClientFactory.builder()
            .chatModel(chatModel)
            .promptService(promptService)
            .applicationName(properties.getApplicationName())
            .enableTracing(properties.getTracing().isEnabled())
            .enableMemory(properties.getMemory().isEnabled())
            .enableLogging(properties.getLogging().isEnabled());
        
        // Set tracing provider if available
        RequestTracingProvider tracingProvider = RequestTracingRegistry.getInstance();
        if (tracingProvider != null) {
            builder.tracingProvider(tracingProvider);
        }
        
        return builder.build();
    }
    
    /**
     * Create default DriftKitChatClient if ChatModel is available
     */
    @Bean
    @ConditionalOnMissingBean(DriftKitChatClient.class)
    @ConditionalOnBean({ChatModel.class, SpringAIChatClientFactory.class})
    @ConditionalOnProperty(
        prefix = "driftkit.spring-ai",
        name = "chat-client.enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    public DriftKitChatClient driftKitChatClient(SpringAIChatClientFactory factory) {
        log.info("Creating DriftKitChatClient");
        return factory.createChatClient();
    }
    
    /**
     * Configuration for enhanced ChatClient with advisors
     */
    @Configuration
    @ConditionalOnBean(ChatModel.class)
    @ConditionalOnProperty(
        prefix = "driftkit.spring-ai",
        name = "enhanced-chat-client.enabled",
        havingValue = "true",
        matchIfMissing = false
    )
    public static class EnhancedChatClientConfiguration {
        
        @Bean
        @ConditionalOnMissingBean(name = "driftKitEnhancedChatClient")
        public ChatClient driftKitEnhancedChatClient(
                SpringAIChatClientFactory factory,
                ChatModel chatModel,
                DriftKitSpringAIProperties properties) {
            
            log.info("Creating enhanced ChatClient with DriftKit integration");
            
            // Create ChatClient builder with ChatModel
            ChatClient.Builder builder = ChatClient.builder(chatModel);
            
            // Add default system message if configured
            if (properties.getDefaultSystemMessage() != null) {
                builder.defaultSystem(properties.getDefaultSystemMessage());
            }
            
            // Use factory to apply advisors and configuration
            return factory.createSpringAIChatClient(builder);
        }
    }
}
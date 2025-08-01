package ai.driftkit.clients.springai.autoconfigure;

import ai.driftkit.clients.springai.SpringAIModelClient;
import ai.driftkit.common.domain.client.ModelClient;
// Removed TraceService import - not needed
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * Auto-configuration for DriftKit Clients Spring AI integration.
 * 
 * This configuration creates a SpringAIModelClient that wraps a Spring AI ChatModel,
 * allowing Spring AI models to be used within the DriftKit framework.
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass({ChatModel.class, ModelClient.class})
@ConditionalOnProperty(prefix = "driftkit.clients.spring-ai", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(DriftKitClientsSpringAIProperties.class)
public class DriftKitClientsSpringAIAutoConfiguration {
    
    private final DriftKitClientsSpringAIProperties properties;
    
    public DriftKitClientsSpringAIAutoConfiguration(DriftKitClientsSpringAIProperties properties) {
        this.properties = properties;
        log.info("Initializing DriftKit Clients Spring AI integration with properties: {}", properties);
    }
    
    /**
     * Creates a SpringAIModelClient that wraps the Spring AI ChatModel.
     * 
     * @param applicationContext Spring application context for bean lookup
     * @param chatModelProvider Provider for ChatModel beans
     * @param traceServiceProvider Provider for TraceService (optional)
     * @return SpringAIModelClient instance
     */
    @Bean
    @ConditionalOnBean(ChatModel.class)
    @ConditionalOnMissingBean(name = "springAIModelClient")
    @ConditionalOnProperty(prefix = "driftkit.clients.spring-ai.model", name = "primary", havingValue = "false", matchIfMissing = true)
    public SpringAIModelClient springAIModelClient(
            ApplicationContext applicationContext,
            ObjectProvider<ChatModel> chatModelProvider,
            BeanFactory beanFactory) {
        
        ChatModel chatModel = getChatModel(applicationContext, chatModelProvider);
        log.info("Creating SpringAIModelClient with ChatModel: {}", 
            chatModel.getClass().getSimpleName());
        
        // Validate configuration
        validateConfiguration();
        
        SpringAIModelClient client = new SpringAIModelClient(chatModel);
        
        // Apply default configurations
        applyDefaultConfigurations(client);
        
        if (properties.isLoggingEnabled()) {
            log.info("Created SpringAIModelClient with configuration: model={}, temperature={}, maxTokens={}, topP={}", 
                properties.getDefaultModel(), 
                properties.getDefaultTemperature(),
                properties.getDefaultMaxTokens(),
                properties.getDefaultTopP());
        }
        
        return client;
    }
    
    /**
     * Creates a primary SpringAIModelClient when configured.
     * 
     * @param applicationContext Spring application context for bean lookup
     * @param chatModelProvider Provider for ChatModel beans
     * @param traceServiceProvider Provider for TraceService (optional)
     * @return Primary SpringAIModelClient instance
     */
    @Bean
    @Primary
    @ConditionalOnBean(ChatModel.class)
    @ConditionalOnMissingBean(name = "primarySpringAIModelClient")
    @ConditionalOnProperty(prefix = "driftkit.clients.spring-ai.model", name = "primary", havingValue = "true")
    public ModelClient primarySpringAIModelClient(
            ApplicationContext applicationContext,
            ObjectProvider<ChatModel> chatModelProvider,
            BeanFactory beanFactory) {
        
        ChatModel chatModel = getChatModel(applicationContext, chatModelProvider);
        log.info("Creating primary SpringAIModelClient with ChatModel: {}", 
            chatModel.getClass().getSimpleName());
        
        // Validate configuration
        validateConfiguration();
        
        SpringAIModelClient client = new SpringAIModelClient(chatModel);
        
        // Apply default configurations
        applyDefaultConfigurations(client);
        
        if (properties.isLoggingEnabled()) {
            log.info("Created primary SpringAIModelClient with configuration: model={}, temperature={}, maxTokens={}, topP={}", 
                properties.getDefaultModel(), 
                properties.getDefaultTemperature(),
                properties.getDefaultMaxTokens(),
                properties.getDefaultTopP());
        }
        
        return client;
    }
    
    /**
     * Gets the ChatModel to use, either by bean name or the primary/unique bean.
     * 
     * @param applicationContext Spring application context for bean lookup
     * @param chatModelProvider Provider for ChatModel beans
     * @return ChatModel instance
     * @throws IllegalStateException if no suitable ChatModel is found
     */
    private ChatModel getChatModel(ApplicationContext applicationContext, ObjectProvider<ChatModel> chatModelProvider) {
        String beanName = properties.getModel().getBeanName();
        
        if (StringUtils.hasText(beanName)) {
            // Get specific named bean
            try {
                ChatModel namedModel = applicationContext.getBean(beanName, ChatModel.class);
                log.debug("Using named ChatModel bean: {} of type: {}", beanName, namedModel.getClass().getName());
                return namedModel;
            } catch (NoSuchBeanDefinitionException e) {
                // List available beans for better error message
                Map<String, ChatModel> availableBeans = applicationContext.getBeansOfType(ChatModel.class);
                String availableNames = String.join(", ", availableBeans.keySet());
                throw new IllegalStateException(
                    String.format("No ChatModel bean found with name '%s'. Available beans: %s", 
                        beanName, availableNames), e);
            }
        } else {
            // Get primary or unique bean
            ChatModel chatModel = chatModelProvider.getIfUnique();
            if (chatModel == null) {
                // Try to get any available bean
                chatModel = chatModelProvider.getIfAvailable();
                if (chatModel == null) {
                    throw new IllegalStateException(
                        "No ChatModel bean available. Please configure a Spring AI ChatModel bean.");
                }
                
                // If multiple beans exist, warn about ambiguity
                Map<String, ChatModel> availableBeans = applicationContext.getBeansOfType(ChatModel.class);
                if (availableBeans.size() > 1) {
                    log.warn("Multiple ChatModel beans found: {}. Using: {}. " +
                        "Consider specifying 'driftkit.clients.spring-ai.model.bean-name' property.",
                        availableBeans.keySet(), chatModel.getClass().getSimpleName());
                }
            }
            
            log.debug("Using ChatModel bean of type: {}", chatModel.getClass().getName());
            return chatModel;
        }
    }
    
    /**
     * Validates the configuration properties.
     * 
     * @throws IllegalArgumentException if configuration is invalid
     */
    private void validateConfiguration() {
        // Validate temperature range
        if (properties.getDefaultTemperature() != null) {
            double temp = properties.getDefaultTemperature();
            if (temp < 0.0 || temp > 2.0) {
                throw new IllegalArgumentException(
                    String.format("Invalid temperature value: %f. Must be between 0.0 and 2.0", temp));
            }
        }
        
        // Validate top-p range
        if (properties.getDefaultTopP() != null) {
            double topP = properties.getDefaultTopP();
            if (topP < 0.0 || topP > 1.0) {
                throw new IllegalArgumentException(
                    String.format("Invalid top-p value: %f. Must be between 0.0 and 1.0", topP));
            }
        }
        
        // Validate max tokens
        if (properties.getDefaultMaxTokens() != null) {
            int maxTokens = properties.getDefaultMaxTokens();
            if (maxTokens <= 0) {
                throw new IllegalArgumentException(
                    String.format("Invalid max tokens value: %d. Must be positive", maxTokens));
            }
        }
    }
    
    /**
     * Applies default configurations to the builder.
     * 
     * @param builder SpringAIModelClient builder
     */
    private void applyDefaultConfigurations(SpringAIModelClient client) {
        if (properties.getDefaultModel() != null) {
            client.withModel(properties.getDefaultModel());
            log.debug("Applied default model: {}", properties.getDefaultModel());
        }
        
        if (properties.getDefaultTemperature() != null) {
            client.withTemperature(properties.getDefaultTemperature());
            log.debug("Applied default temperature: {}", properties.getDefaultTemperature());
        }
        
        if (properties.getDefaultMaxTokens() != null) {
            client.withMaxTokens(properties.getDefaultMaxTokens());
            log.debug("Applied default max tokens: {}", properties.getDefaultMaxTokens());
        }
        
        if (properties.getDefaultTopP() != null) {
            client.withTopP(properties.getDefaultTopP());
            log.debug("Applied default top-p: {}", properties.getDefaultTopP());
        }
    }
}
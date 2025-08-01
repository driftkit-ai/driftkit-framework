package ai.driftkit.context.springai;

import ai.driftkit.context.core.service.PromptService;
import ai.driftkit.workflows.core.agent.RequestTracingProvider;
import ai.driftkit.workflows.core.agent.RequestTracingRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import reactor.core.scheduler.Schedulers;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.model.ChatModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory for creating Spring AI ChatClient instances with DriftKit integration.
 * This factory automatically configures ChatClient with:
 * - DriftKit tracing advisor for monitoring
 * - DriftKit prompt provider for prompt management
 * - Optional chat memory support
 * 
 * Usage example:
 * <pre>
 * @Configuration
 * public class ChatClientConfig {
 *     
 *     @Bean
 *     public SpringAIChatClientFactory chatClientFactory(ChatModel chatModel, 
 *                                                       PromptService promptService) {
 *         return SpringAIChatClientFactory.builder()
 *             .chatModel(chatModel)
 *             .promptService(promptService)
 *             .enableTracing(true)
 *             .enableMemory(true)
 *             .build();
 *     }
 *     
 *     @Bean
 *     public ChatClient chatClient(SpringAIChatClientFactory factory) {
 *         return factory.createChatClient();
 *     }
 * }
 * </pre>
 */
@Slf4j
public class SpringAIChatClientFactory {
    
    private final ChatModel chatModel;
    private final PromptService promptService;
    private final RequestTracingProvider tracingProvider;
    private final boolean enableTracing;
    private final boolean enableMemory;
    private final boolean enableLogging;
    private final ChatMemory chatMemory;
    private final String applicationName;
    
    private SpringAIChatClientFactory(Builder builder) {
        this.chatModel = builder.chatModel;
        this.promptService = builder.promptService;
        this.tracingProvider = builder.tracingProvider != null 
            ? builder.tracingProvider 
            : RequestTracingRegistry.getInstance();
        this.enableTracing = builder.enableTracing;
        this.enableMemory = builder.enableMemory;
        this.enableLogging = builder.enableLogging;
        this.chatMemory = builder.chatMemory != null 
            ? builder.chatMemory 
            : MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(10)
                .build();
        this.applicationName = builder.applicationName;
    }
    
    /**
     * Create a new Spring AI ChatClient with DriftKit integration
     */
    public ChatClient createSpringAIChatClient() {
        return createSpringAIChatClient(ChatClient.builder(chatModel));
    }
    
    /**
     * Create a new Spring AI ChatClient with custom builder
     */
    public ChatClient createSpringAIChatClient(ChatClient.Builder clientBuilder) {
        List<Advisor> advisors = new ArrayList<>();
        
        // Add tracing advisor if enabled
        if (enableTracing && tracingProvider != null) {
            DriftKitTracingAdvisor tracingAdvisor = new DriftKitTracingAdvisor(
                tracingProvider, applicationName);
            advisors.add(tracingAdvisor);
            log.info("Added DriftKit tracing advisor to ChatClient");
        }
        
        // Add memory advisor if enabled
        if (enableMemory) {
            // Create MessageChatMemoryAdvisor using builder
            MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory)
                .conversationId("default")
                .scheduler(Schedulers.boundedElastic())
                .build();
            advisors.add(memoryAdvisor);
            log.info("Added memory advisor to ChatClient");
        }
        
        // Add logging advisor if enabled
        if (enableLogging) {
            SimpleLoggerAdvisor loggingAdvisor = new SimpleLoggerAdvisor();
            advisors.add(loggingAdvisor);
            log.info("Added logging advisor to ChatClient");
        }
        
        // Apply advisors with proper typing
        if (!advisors.isEmpty()) {
            // Pass the list of advisors directly
            clientBuilder.defaultAdvisors(advisors);
        }
        
        return clientBuilder.build();
    }
    
    /**
     * Create a DriftKitChatClient with prompt provider integration
     */
    public DriftKitChatClient createChatClient() {
        ChatClient chatClient = createSpringAIChatClient();
        DriftKitPromptProvider promptProvider = new DriftKitPromptProvider(promptService);
        
        return new DriftKitChatClient(chatClient, promptProvider);
    }
    
    /**
     * Get the prompt provider for standalone use
     */
    public DriftKitPromptProvider getPromptProvider() {
        return new DriftKitPromptProvider(promptService);
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private ChatModel chatModel;
        private PromptService promptService;
        private RequestTracingProvider tracingProvider;
        private boolean enableTracing = true;
        private boolean enableMemory = false;
        private boolean enableLogging = false;
        private ChatMemory chatMemory;
        private String applicationName = "spring-ai-app";
        
        public Builder chatModel(ChatModel chatModel) {
            this.chatModel = chatModel;
            return this;
        }
        
        public Builder promptService(PromptService promptService) {
            this.promptService = promptService;
            return this;
        }
        
        public Builder tracingProvider(RequestTracingProvider tracingProvider) {
            this.tracingProvider = tracingProvider;
            return this;
        }
        
        public Builder enableTracing(boolean enableTracing) {
            this.enableTracing = enableTracing;
            return this;
        }
        
        public Builder enableMemory(boolean enableMemory) {
            this.enableMemory = enableMemory;
            return this;
        }
        
        public Builder enableLogging(boolean enableLogging) {
            this.enableLogging = enableLogging;
            return this;
        }
        
        public Builder chatMemory(ChatMemory chatMemory) {
            this.chatMemory = chatMemory;
            return this;
        }
        
        public Builder applicationName(String applicationName) {
            this.applicationName = applicationName;
            return this;
        }
        
        public SpringAIChatClientFactory build() {
            if (chatModel == null) {
                throw new IllegalStateException("ChatModel is required");
            }
            if (promptService == null) {
                throw new IllegalStateException("PromptService is required");
            }
            return new SpringAIChatClientFactory(this);
        }
    }
}
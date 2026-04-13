package /*PACKAGE_NAME*/.config;

import ai.driftkit.spring.ai.DriftKitChatClient;
import ai.driftkit.spring.ai.DriftKitPromptProvider;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;

@Configuration
public class SpringAIConfig {
    
    @Bean
    public ChatMemory chatMemory() {
        return new InMemoryChatMemory();
    }
    
    @Bean
    public VectorStore vectorStore(EmbeddingClient embeddingClient) {
        return new SimpleVectorStore(embeddingClient);
    }
    
    @Bean
    public ChatClient.Builder chatClientBuilder(
            DriftKitChatClient driftKitChatClient,
            DriftKitPromptProvider promptProvider) {
        
        // Spring AI ChatClient builder enhanced with DriftKit capabilities
        return ChatClient.builder()
            .defaultAdvisors(
                // DriftKit tracing advisor is automatically configured
            );
    }
}
package ai.driftkit.workflow.engine.memory;

import ai.driftkit.common.service.ChatMemory;
import ai.driftkit.common.service.ChatMemoryStore;
import ai.driftkit.common.service.TokenWindowChatMemory;
import ai.driftkit.common.utils.Tokenizer;
import ai.driftkit.workflow.engine.persistence.ChatHistoryRepository;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Configuration for workflow chat memory management.
 * Integrates driftkit-common ChatMemory with workflow engine.
 */
@Data
@Builder
public class WorkflowMemoryConfiguration {
    
    /**
     * Maximum tokens per chat session.
     * Default is 4096 (suitable for most LLMs).
     */
    @Builder.Default
    private int maxTokensPerChat = 4096;
    
    /**
     * The tokenizer to use for counting tokens.
     * Required for token-based memory management.
     */
    private Tokenizer tokenizer;
    
    /**
     * The chat history repository.
     * Required for persistence.
     */
    private ChatHistoryRepository chatHistoryRepository;
    
    /**
     * Whether to use token-based memory management.
     * If false, simple message count limits will be used.
     */
    @Builder.Default
    private boolean useTokenWindowMemory = true;
    
    /**
     * Maximum messages per chat when not using token window.
     * Default is 100.
     */
    @Builder.Default
    private int maxMessagesPerChat = 100;
    
    /**
     * Create a ChatMemory instance for a specific chat.
     */
    public ChatMemory createChatMemory(String chatId) {
        if (!useTokenWindowMemory) {
            throw new UnsupportedOperationException("Non-token memory not implemented yet");
        }
        
        if (tokenizer == null) {
            throw new IllegalStateException("Tokenizer is required for token-based memory");
        }
        
        ChatMemoryStore memoryStore = new ChatHistoryMemoryStoreAdapter(chatHistoryRepository);
        
        return TokenWindowChatMemory.withMaxTokens(
            chatId,
            maxTokensPerChat,
            tokenizer,
            memoryStore
        );
    }
    
    /**
     * Create a default configuration with simple tokenizer.
     */
    public static WorkflowMemoryConfiguration createDefault(ChatHistoryRepository repository) {
        return WorkflowMemoryConfiguration.builder()
            .chatHistoryRepository(repository)
            .tokenizer(new SimpleTokenizer())
            .build();
    }
    
    /**
     * Simple tokenizer implementation for basic token counting.
     * In production, use a proper tokenizer like GPT2Tokenizer.
     */
    private static class SimpleTokenizer implements Tokenizer {
        private static final int AVERAGE_CHARS_PER_TOKEN = 4;
        
        @Override
        public int estimateTokenCountInMessages(List<ai.driftkit.common.domain.Message> messages) {
            if (messages == null || messages.isEmpty()) {
                return 0;
            }
            return messages.stream()
                    .mapToInt(this::estimateTokenCountInMessage)
                    .sum();
        }
        
        @Override
        public int estimateTokenCountInMessage(ai.driftkit.common.domain.Message message) {
            if (message == null || message.getMessage() == null) {
                return 0;
            }
            // Simple estimation: 1 token per 4 characters
            return message.getMessage().length() / AVERAGE_CHARS_PER_TOKEN;
        }
    }
}
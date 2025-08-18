package ai.driftkit.workflow.engine.examples;

import ai.driftkit.workflow.engine.builder.WorkflowBuilder;
import ai.driftkit.workflow.engine.core.StepResult;
import ai.driftkit.workflow.engine.core.WorkflowEngine;
import ai.driftkit.workflow.engine.core.WorkflowEngine.WorkflowExecution;
import ai.driftkit.workflow.engine.domain.WorkflowEngineConfig;
import ai.driftkit.common.service.ChatStore;
import ai.driftkit.common.service.impl.InMemoryChatStore;
import ai.driftkit.common.service.impl.SimpleTextTokenizer;
import ai.driftkit.common.domain.chat.ChatMessage;
import ai.driftkit.common.domain.chat.ChatMessage.MessageType;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Example showing how to use ChatStore directly in workflows for agent-like behavior.
 * 
 * Key benefits:
 * 1. Direct ChatStore usage - no adapters
 * 2. Token management built-in
 * 3. Clean separation of concerns
 * 4. Works with auto-tracking
 */
@Slf4j
public class SimplifiedAgentWorkflow {
    
    @Data
    public static class UserQuery {
        private final String question;
    }
    
    @Data
    public static class AgentResponse {
        private final String answer;
        private final Map<String, String> metadata;
    }
    
    @Data
    public static class Clarification {
        private final String question;
    }
    
    @Data
    public static class ClarificationResponse {
        private final String answer;
    }
    
    // Mock LLM service
    static class MockLLMService {
        private final ChatStore chatStore;
        
        MockLLMService(ChatStore chatStore) {
            this.chatStore = chatStore;
        }
        
        public String generateResponse(String chatId, String input) {
            // Get conversation history with token management
            var history = chatStore.getRecentWithinTokens(chatId, 2048);
            
            // In real implementation, this would call the LLM API with history
            log.info("Generating response for: {} (with {} messages in history)", 
                input, history.size());
            
            // Mock response
            if (input.contains("quantum")) {
                return "Quantum computing uses quantum mechanics principles...";
            }
            return "I can help you with that. " + input;
        }
    }
    
    @Test
    @DisplayName("Should demonstrate ChatStore auto-tracking without suspension")
    public void testSimpleChatStoreAutoTracking() throws Exception {
        // Setup
        ChatStore chatStore = new InMemoryChatStore(new SimpleTextTokenizer());
        MockLLMService llmService = new MockLLMService(chatStore);
        
        WorkflowEngineConfig config = WorkflowEngineConfig.builder()
            .chatStore(chatStore)
            .build();
            
        WorkflowEngine engine = new WorkflowEngine(config);
        
        // Simple workflow without suspension
        var workflow = WorkflowBuilder.define("simple-agent", UserQuery.class, AgentResponse.class)
            .then("process", (UserQuery query) -> {
                String chatId = "agent-chat-123";
                
                // Generate response
                String response = llmService.generateResponse(chatId, query.getQuestion());
                
                // Create response
                AgentResponse agentResp = new AgentResponse(
                    response,
                    Map.of("confidence", "high", "tokens_used", "100")
                );
                
                return StepResult.finish(agentResp);
            }, UserQuery.class, AgentResponse.class)
            .build();
            
        engine.register(workflow);
        
        // Execute
        UserQuery query = new UserQuery("Tell me about quantum computing");
        WorkflowExecution<AgentResponse> execution = engine.execute("simple-agent", query, "agent-chat-123");
        
        // Get result
        AgentResponse result = execution.get(5, TimeUnit.SECONDS);
        assertNotNull(result);
        assertTrue(execution.isCompleted());
        
        // Check auto-tracked messages
        var messages = chatStore.getRecent("agent-chat-123");
        log.info("Auto-tracked messages: {}", messages.size());
        for (ChatMessage msg : messages) {
            log.info("- {}: {}", msg.getType(), msg.getPropertiesMap());
        }
        
        // Should have at least the auto-tracked messages
        assertTrue(messages.size() >= 2, "Should have auto-tracked user input and AI response");
    }
    
    @Test
    @DisplayName("Should use ChatStore directly in agent workflow with manual and auto-tracking")
    public void testAgentWorkflowWithChatStore() throws Exception {
        // Setup
        ChatStore chatStore = new InMemoryChatStore(new SimpleTextTokenizer());
        MockLLMService llmService = new MockLLMService(chatStore);
        
        WorkflowEngineConfig config = WorkflowEngineConfig.builder()
            .chatStore(chatStore)
            .suspensionDataRepository(new ai.driftkit.workflow.engine.persistence.inmemory.InMemorySuspensionDataRepository())
            .build();
            
        WorkflowEngine engine = new WorkflowEngine(config);
        
        // Build agent workflow - simplified approach for testing
        var workflow = WorkflowBuilder.define("agent-workflow", UserQuery.class, AgentResponse.class)
            .then("process", (UserQuery query) -> {
                String chatId = "agent-chat-123";
                
                // Manually add user message (in addition to auto-tracking)
                chatStore.add(chatId, query.getQuestion(), MessageType.USER);
                
                // Generate response using ChatStore history
                String response = llmService.generateResponse(chatId, query.getQuestion());
                
                // Manually add agent response
                chatStore.add(chatId, response, MessageType.AI);
                
                // For this test, let's always provide a direct response
                // This demonstrates auto-tracking without the complexity of suspension
                AgentResponse agentResp = new AgentResponse(
                    response,
                    Map.of(
                        "confidence", "high",
                        "tokens_used", String.valueOf(response.length() / 4)
                    )
                );
                
                return StepResult.finish(agentResp);
            }, UserQuery.class, AgentResponse.class)
            .build();
            
        engine.register(workflow);
        
        // Execute
        UserQuery query = new UserQuery("Quantum?");
        WorkflowExecution<AgentResponse> execution = engine.execute("agent-workflow", query, "agent-chat-123");
        
        // Wait for workflow to complete
        AgentResponse result = execution.get(5, TimeUnit.SECONDS);
        
        // Final history
        log.info("\nFinal chat history:");
        var finalMessages = chatStore.getRecent("agent-chat-123");
        log.info("Total messages: {}", finalMessages.size());
        assertTrue(finalMessages.size() >= 4, "Should have manual + auto-tracked messages");
        for (ChatMessage msg : finalMessages) {
            log.info("- {}: {}", msg.getType(), 
                msg.getPropertiesMap().getOrDefault("message", msg.getPropertiesMap().toString()));
        }
        
        assertTrue(execution.isCompleted(), "Workflow should be completed");
        log.info("\nAgent response: {}", result);
        assertNotNull(result, "Result should not be null");
        assertNotNull(result.getAnswer(), "Answer should not be null");
        assertNotNull(result.getMetadata(), "Metadata should not be null");
        assertEquals("high", result.getMetadata().get("confidence"), "Confidence should be high");
        
        // Demonstrate token management
        int totalTokens = chatStore.getTotalTokens("agent-chat-123");
        log.info("\nToken usage: {} tokens", totalTokens);
        assertTrue(totalTokens > 0, "Should have used some tokens");
    }
}
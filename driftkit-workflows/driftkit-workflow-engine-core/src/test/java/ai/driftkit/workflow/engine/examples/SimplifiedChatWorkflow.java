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
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Simplified chat workflow example showing how ChatStore auto-tracking works.
 * 
 * Key points:
 * 1. No manual chat saving needed
 * 2. All Suspend/Finish messages auto-tracked
 * 3. User inputs auto-saved on resume
 * 4. ChatStore handles token management
 */
@Slf4j
public class SimplifiedChatWorkflow {
    
    @Data
    public static class ChatRequest {
        private final String message;
        private final String topic;
    }
    
    @Data
    public static class Analysis {
        private final String topic;
        private final String sentiment;
        private final boolean needsMoreInfo;
    }
    
    @Data
    public static class FollowUp {
        private final String question;
        private final String context;
    }
    
    @Data
    public static class UserResponse {
        private final String answer;
    }
    
    @Data
    public static class FinalResult {
        private final String summary;
        private final String recommendation;
    }
    
    @Test
    @DisplayName("Should auto-track messages in ChatStore during workflow execution")
    public void testChatStoreAutoTracking() throws Exception {
        // 1. Create ChatStore
        ChatStore chatStore = new InMemoryChatStore(new SimpleTextTokenizer());
        
        // 2. Create WorkflowEngine with ChatStore
        WorkflowEngineConfig config = WorkflowEngineConfig.builder()
            .chatStore(chatStore)
            .build();
            
        WorkflowEngine engine = new WorkflowEngine(config);
        
        // 3. Build workflow - NO chat saving code needed!
        var workflow = WorkflowBuilder.define("chat-workflow", ChatRequest.class, FinalResult.class)
            .then("analyze", (ChatRequest req) -> {
                log.info("Analyzing request: {}", req.getMessage());
                
                Analysis analysis = new Analysis(
                    req.getTopic(),
                    "positive",
                    req.getMessage().contains("?")
                );
                
                return StepResult.continueWith(analysis);
            }, ChatRequest.class, Analysis.class)
            
            .then("check", (Analysis analysis) -> {
                if (analysis.isNeedsMoreInfo()) {
                    // This will be auto-saved to ChatStore!
                    FollowUp followUp = new FollowUp(
                        "Can you provide more details about " + analysis.getTopic() + "?",
                        "We need this to give you better recommendations"
                    );
                    
                    return StepResult.suspend(followUp, UserResponse.class);
                } else {
                    return StepResult.continueWith(analysis);
                }
            }, Analysis.class, Object.class)
            
            .then("respond", (UserResponse resp) -> {
                log.info("User provided: {}", resp.getAnswer());
                
                // Process user response
                return StepResult.continueWith(new Analysis(
                    "enhanced topic",
                    "positive", 
                    false
                ));
            }, UserResponse.class, Analysis.class)
            
            .then("finalize", (Analysis analysis) -> {
                FinalResult result = new FinalResult(
                    "Summary: Analyzed " + analysis.getTopic() + " with " + analysis.getSentiment() + " sentiment",
                    "Recommendation: Proceed with implementation"
                );
                
                // This will be auto-saved to ChatStore!
                return StepResult.finish(result);
            }, Analysis.class, FinalResult.class)
            
            .build();
            
        engine.register(workflow);
        
        // 4. Execute workflow
        String chatId = "chat-123";
        ChatRequest request = new ChatRequest("Tell me about quantum computing?", "quantum");
        
        // Execute with chatId - all messages will be auto-tracked!
        WorkflowExecution<FinalResult> execution = engine.execute("chat-workflow", request, chatId);
        
        // Wait a bit for async execution
        Thread.sleep(100);
        
        // Check initial messages
        var messages = chatStore.getRecent(chatId);
        log.info("Initial messages count: {}", messages.size());
        
        if (execution.isSuspended()) {
            log.info("Workflow suspended, waiting for user input...");
            
            // Check what was saved to ChatStore
            log.info("Chat history has {} messages", messages.size());
            assertFalse(messages.isEmpty(), "Chat history should have messages");
            
            // Last message should be our FollowUp
            ChatMessage lastMsg = messages.get(messages.size() - 1);
            log.info("Last message type: {}, properties: {}", 
                lastMsg.getType(), lastMsg.getPropertiesMap());
            assertEquals(ChatMessage.MessageType.AI, lastMsg.getType(), "Last message should be AI type");
            assertTrue(lastMsg.getPropertiesMap().containsKey("question"), "Last message should contain question property");
            
            // Resume with user response - this will also be auto-saved!
            UserResponse userResp = new UserResponse("I'm interested in quantum entanglement specifically");
            execution = engine.resume(execution.getRunId(), userResp);
        }
        
        // 5. Check final chat history
        var finalMessages = chatStore.getRecent(chatId);
        log.info("\nFinal chat history ({} messages):", finalMessages.size());
        assertTrue(finalMessages.size() > messages.size(), "Should have more messages after resume");
        
        for (ChatMessage msg : finalMessages) {
            log.info("- {} [{}]: {}", 
                msg.getType(),
                msg.getTimestamp(), 
                msg.getPropertiesMap());
        }
        
        // Result
        if (execution.isCompleted()) {
            FinalResult result = execution.getResult();
            log.info("\nWorkflow completed with result: {}", result);
            assertNotNull(result, "Result should not be null");
            assertNotNull(result.getSummary(), "Summary should not be null");
            assertNotNull(result.getRecommendation(), "Recommendation should not be null");
        }
    }
}
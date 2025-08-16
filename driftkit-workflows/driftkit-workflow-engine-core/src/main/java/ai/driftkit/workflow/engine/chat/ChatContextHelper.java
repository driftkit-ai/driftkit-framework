package ai.driftkit.workflow.engine.chat;

import ai.driftkit.workflow.engine.core.WorkflowContext;
import ai.driftkit.workflow.engine.core.WorkflowContext.Keys;
import ai.driftkit.workflow.engine.schema.AIFunctionSchema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

/**
 * Helper class to work with chat-specific data in WorkflowContext.
 * Provides convenient methods to store and retrieve chat-related information.
 */
@Slf4j
public class ChatContextHelper {
    
    private ChatContextHelper() {} // prevent instantiation
    
    // ========== Chat Session Management ==========
    
    /**
     * Set the chat ID in the context.
     */
    public static void setChatId(WorkflowContext context, String chatId) {
        context.setStepOutput(Keys.CHAT_ID, chatId);
    }
    
    /**
     * Get the chat ID from the context.
     */
    public static String getChatId(WorkflowContext context) {
        return context.getStepResultOrDefault(Keys.CHAT_ID, String.class, null);
    }
    
    /**
     * Set the user ID in the context.
     */
    public static void setUserId(WorkflowContext context, String userId) {
        context.setStepOutput(Keys.USER_ID, userId);
    }
    
    /**
     * Get the user ID from the context.
     */
    public static String getUserId(WorkflowContext context) {
        return context.getStepResultOrDefault(Keys.USER_ID, String.class, null);
    }
    
    // ========== Conversation History ==========
    
    /**
     * Add a user message to the conversation.
     */
    public static void addUserMessage(WorkflowContext context, String content) {
        if (StringUtils.isEmpty(content)) {
            return;
        }
        
        // Store user message in context for workflow processing
        context.setStepOutput("lastUserMessage", content);
    }
    
    // ========== Step Invocation Tracking ==========
    
    /**
     * Track a step invocation.
     */
    public static void trackStepInvocation(WorkflowContext context, String stepName) {
        if (StringUtils.isEmpty(stepName)) {
            return;
        }
        
        Map<String, Integer> counts = context.getMap(
            Keys.STEP_INVOCATION_COUNTS, String.class, Integer.class);
        
        if (counts == null) {
            counts = new HashMap<>();
        }
        
        Map<String, Integer> newCounts = new HashMap<>(counts);
        newCounts.merge(stepName, 1, Integer::sum);
        
        context.setStepOutput(Keys.STEP_INVOCATION_COUNTS, newCounts);
    }
    
    /**
     * Get the invocation count for a specific step.
     */
    public static int getStepInvocationCount(WorkflowContext context, String stepName) {
        if (StringUtils.isEmpty(stepName)) {
            return 0;
        }
        
        Map<String, Integer> counts = context.getMap(
            Keys.STEP_INVOCATION_COUNTS, String.class, Integer.class);
        return counts != null ? counts.getOrDefault(stepName, 0) : 0;
    }
    
    // ========== Async Message Tracking ==========

    /**
     * Initialize a context for chat workflow.
     */
    public static WorkflowContext initChatContext(String chatId, String userId, Object triggerData) {
        WorkflowContext context = WorkflowContext.newRun(triggerData);
        setChatId(context, chatId);
        setUserId(context, userId);
        context.setStepOutput(Keys.STEP_INVOCATION_COUNTS, new HashMap<>());
        return context;
    }

}
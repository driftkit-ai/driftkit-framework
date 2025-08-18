package ai.driftkit.workflow.engine.core;

import ai.driftkit.common.domain.chat.ChatMessage;
import ai.driftkit.common.domain.chat.ChatMessage.MessageType;
import ai.driftkit.common.service.ChatStore;
import ai.driftkit.workflow.engine.graph.StepNode;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import ai.driftkit.workflow.engine.schema.SchemaUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * Interceptor that automatically tracks chat messages to ChatStore
 * for Suspend, Async, and Finish step results.
 */
@Slf4j
@RequiredArgsConstructor
public class ChatTrackingInterceptor implements ExecutionInterceptor {
    
    private final ChatStore chatStore;
    
    @Override
    public void beforeStep(WorkflowInstance instance, StepNode step, Object input) {
        // No action needed before step
    }
    
    @Override
    public void afterStep(WorkflowInstance instance, StepNode step, StepResult<?> result) {
        String chatId = instance.getInstanceId();
        
        switch (result) {
            case StepResult.Suspend<?> suspend -> {
                // For simple types like String, create a simple properties map
                if (suspend.promptToUser() instanceof String prompt) {
                    chatStore.add(chatId, prompt, MessageType.AI);
                } else {
                    Map<String, String> properties = SchemaUtils.extractProperties(suspend.promptToUser());
                    chatStore.add(chatId, properties, MessageType.AI);
                }
                log.debug("Tracked suspend message for chat: {}", chatId);
            }
            
            case StepResult.Async<?> async -> {
                if (async.immediateData() instanceof String message) {
                    chatStore.add(chatId, message, MessageType.AI);
                } else if (async.immediateData() != null) {
                    Map<String, String> properties = SchemaUtils.extractProperties(async.immediateData());
                    chatStore.add(chatId, properties, MessageType.AI);
                }
                log.debug("Tracked async start message for chat: {}", chatId);
            }
            
            case StepResult.Finish<?> finish -> {
                if (finish.result() instanceof String message) {
                    chatStore.add(chatId, message, MessageType.AI);
                } else if (finish.result() != null) {
                    Map<String, String> properties = SchemaUtils.extractProperties(finish.result());
                    chatStore.add(chatId, properties, MessageType.AI);
                }
                log.debug("Tracked finish message for chat: {}", chatId);
            }
            
            default -> {
                // Continue, Branch, and Fail results are not tracked automatically
            }
        }
    }
    
    @Override
    public void onStepError(WorkflowInstance instance, StepNode step, Exception error) {
        String chatId = instance.getInstanceId();
        
        Map<String, String> properties = new HashMap<>();
        properties.put(ChatMessage.PROPERTY_MESSAGE, "Error in step " + step.id() + ": " + error.getMessage());
        properties.put("stepId", step.id());
        properties.put("status", "error");
        properties.put("error", error.getMessage());
        
        chatStore.add(chatId, properties, MessageType.SYSTEM);
        log.debug("Tracked error message for chat: {}", chatId);
    }
}
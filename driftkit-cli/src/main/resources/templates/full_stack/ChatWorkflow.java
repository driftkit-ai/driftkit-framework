package /*PACKAGE_NAME*/.workflow;

import ai.driftkit.common.domain.*;
import ai.driftkit.common.domain.client.ModelTextResponse;
import ai.driftkit.common.service.ChatMemory;
import ai.driftkit.config.EtlConfig;
import ai.driftkit.context.core.service.PromptService;
import ai.driftkit.workflows.core.domain.*;
import ai.driftkit.workflows.spring.ModelWorkflow;
import ai.driftkit.workflows.spring.ModelRequestParams;
import ai.driftkit.workflows.spring.service.ModelRequestService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class ChatWorkflow extends ModelWorkflow<LLMRequestEvent, ChatWorkflow.ChatResult> {

    public static final String CHAT_WITH_CONTEXT = "chat_with_context";
    
    public ChatWorkflow(EtlConfig config, 
                       PromptService promptService,
                       ModelRequestService modelRequestService) throws IOException {
        super(config.getVault().get(0).toModelClient(), modelRequestService, promptService);
    }

    @Override
    public ChatResult execute(LLMRequestEvent event, WorkflowContext context) throws Exception {
        log.info("Executing chat workflow for message: {}", event.getChatItem());
        
        // Initialize memory if not exists
        if (!context.hasVariable("chatMemory")) {
            ChatMemory memory = new ChatMemory();
            context.setVariable("chatMemory", memory);
        }
        
        ChatMemory memory = (ChatMemory) context.getVariable("chatMemory");
        
        // Add user message to memory
        memory.addUserMessage(event.getChatItem());
        
        // Prepare request parameters
        ModelRequestParams params = ModelRequestParams.builder()
                .promptId(CHAT_WITH_CONTEXT)
                .model(modelClient.getDefaultModel())
                .temperature(0.7)
                .maxTokens(2000)
                .build();
        
        // Set variables for the prompt
        Map<String, Object> variables = new HashMap<>();
        variables.put("chat_history", memory.getFormattedHistory());
        variables.put("user_message", event.getChatItem());
        params.setVariables(variables);
        
        // Execute the request
        ModelTextResponse response = sendTextToText(params, context);
        
        // Add assistant response to memory
        memory.addAssistantMessage(response.getContent());
        
        // Return result
        return ChatResult.builder()
                .message(response.getContent())
                .tokensUsed(response.getTokensUsed())
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatResult {
        private String message;
        private Integer tokensUsed;
    }
}
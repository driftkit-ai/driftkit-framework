package /*PACKAGE_NAME*/.controller;

import ai.driftkit.common.domain.ChatRequest;
import ai.driftkit.workflows.core.domain.LLMRequestEvent;
import ai.driftkit.workflows.core.domain.WorkflowContext;
import ai.driftkit.workflows.examples.workflows.ChatWorkflow;
import ai.driftkit.workflows.examples.workflows.ChatWorkflow.ChatResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatWorkflow chatWorkflow;

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        try {
            // Create workflow context
            WorkflowContext context = new WorkflowContext();
            context.setContextId(UUID.randomUUID().toString());
            
            // Create start event
            LLMRequestEvent startEvent = LLMRequestEvent.builder()
                    .chatItem(request.getMessage())
                    .build();
            
            // Execute workflow
            ChatResult result = chatWorkflow.execute(startEvent, context);
            
            return ResponseEntity.ok(ChatResponse.builder()
                    .message(result.getMessage())
                    .contextId(context.getContextId())
                    .build());
                    
        } catch (Exception e) {
            log.error("Error in chat workflow", e);
            return ResponseEntity.internalServerError()
                    .body(ChatResponse.builder()
                            .message("Sorry, I encountered an error processing your request.")
                            .error(e.getMessage())
                            .build());
        }
    }
    
    @lombok.Data
    @lombok.Builder
    public static class ChatResponse {
        private String message;
        private String contextId;
        private String error;
    }
}
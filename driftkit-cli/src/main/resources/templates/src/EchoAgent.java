package /*PACKAGE_NAME*/;

import ai.driftkit.common.domain.client.ModelClient;
import ai.driftkit.workflows.core.agent.Agent;
import ai.driftkit.workflows.core.agent.LLMAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EchoAgent {
    
    private final ModelClient modelClient;
    
    public String process(String input) {
        log.info("Processing input: {}", input);
        
        Agent agent = LLMAgent.builder()
                .modelClient(modelClient)
                .systemMessage("You are a helpful assistant. Echo the user's message in a creative way.")
                .build();
        
        String response = agent.execute(input);
        log.info("Generated response: {}", response);
        
        return response;
    }
}
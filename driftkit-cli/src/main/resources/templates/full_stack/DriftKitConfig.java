package /*PACKAGE_NAME*/.config;

import ai.driftkit.common.domain.client.ModelClient;
import ai.driftkit.config.EtlConfig;
import ai.driftkit.context.core.service.PromptService;
import ai.driftkit.workflows.spring.service.ModelRequestService;
import ai.driftkit.workflows.examples.workflows.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ComponentScan;

import java.io.IOException;

@Configuration
@ComponentScan(basePackages = {
    "ai.driftkit.workflows.examples",
    "ai.driftkit.context.engineering"
})
public class DriftKitConfig {

    @Bean
    public ChatWorkflow chatWorkflow(EtlConfig config, 
                                   PromptService promptService,
                                   ModelRequestService modelRequestService) throws IOException {
        return new ChatWorkflow(config, promptService, modelRequestService);
    }
    
    @Bean
    public ReasoningWorkflow reasoningWorkflow(EtlConfig config,
                                             PromptService promptService,
                                             ModelRequestService modelRequestService) throws IOException {
        return new ReasoningWorkflow(config, promptService, modelRequestService);
    }
    
    @Bean
    public RouterWorkflow routerWorkflow(ModelClient modelClient,
                                       ModelRequestService modelRequestService,
                                       PromptService promptService) {
        return new RouterWorkflow(modelClient, modelRequestService, promptService);
    }
    
    @Bean
    public RAGSearchWorkflow ragSearchWorkflow(EtlConfig config,
                                             PromptService promptService,
                                             ModelRequestService modelRequestService) throws IOException {
        return new RAGSearchWorkflow(config, promptService, modelRequestService);
    }
}
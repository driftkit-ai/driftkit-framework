package ai.driftkit.workflows.spring.config;

import ai.driftkit.workflows.core.service.WorkflowRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for workflow-related beans.
 */
@Configuration
public class WorkflowConfig {

    @Bean
    public WorkflowRegistry workflowRegistry() {
        return new WorkflowRegistry();
    }
}
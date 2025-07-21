package ai.driftkit.workflows.autoconfigure;

import ai.driftkit.workflows.spring.config.AsyncConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * Auto-configuration for workflow services.
 * 
 * This configuration automatically sets up:
 * - Component scanning for workflow services
 * - MongoDB repositories for workflow persistence
 * - Async configuration for workflow execution
 */
@Slf4j
@AutoConfiguration(after = ai.driftkit.config.autoconfigure.EtlConfigAutoConfiguration.class)
@ComponentScan(basePackages = {
    "ai.driftkit.workflows.spring.service",
    "ai.driftkit.workflows.spring.config",
    "ai.driftkit.workflows.spring.controller"
})
@EnableMongoRepositories(basePackages = "ai.driftkit.workflows.spring.repository")
@Import(AsyncConfig.class)
public class WorkflowAutoConfiguration {
    
    public WorkflowAutoConfiguration() {
        log.info("Initializing DriftKit Workflow Auto-Configuration");
    }
}
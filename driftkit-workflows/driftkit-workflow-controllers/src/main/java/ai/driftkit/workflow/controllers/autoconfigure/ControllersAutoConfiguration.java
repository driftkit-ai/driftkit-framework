package ai.driftkit.workflow.controllers.autoconfigure;

import ai.driftkit.workflow.engine.spring.autoconfigure.WorkflowMongoRepositoriesAutoConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Auto-configuration for workflow controllers.
 * Automatically enables MongoDB repositories and services when MongoDB is available.
 */
@Slf4j
@AutoConfiguration(after = WorkflowMongoRepositoriesAutoConfiguration.class)
@ConditionalOnClass(WebMvcConfigurer.class)
@ConditionalOnProperty(
    prefix = "driftkit.workflow.controllers",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
@ComponentScan(basePackages = {
    "ai.driftkit.workflow.controllers.controller",
    "ai.driftkit.workflow.controllers.service"
})
public class ControllersAutoConfiguration {
    
    public ControllersAutoConfiguration() {
        log.info("Enabling workflow controllers auto-configuration");
    }
}
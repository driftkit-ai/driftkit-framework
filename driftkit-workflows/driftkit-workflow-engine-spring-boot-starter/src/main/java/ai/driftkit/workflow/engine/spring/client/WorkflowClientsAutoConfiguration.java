package ai.driftkit.workflow.engine.spring.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

/**
 * Auto-configuration for workflow Feign clients.
 * Enables remote access to workflow services via Feign.
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.cloud.openfeign.FeignClient")
@ConditionalOnProperty(
    prefix = "driftkit.workflow.client",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false
)
@EnableFeignClients(basePackageClasses = WorkflowClientsAutoConfiguration.class)
@Import(WorkflowFeignConfiguration.class)
public class WorkflowClientsAutoConfiguration {
    
    public WorkflowClientsAutoConfiguration() {
        log.info("Initializing Workflow Feign Clients");
    }
}
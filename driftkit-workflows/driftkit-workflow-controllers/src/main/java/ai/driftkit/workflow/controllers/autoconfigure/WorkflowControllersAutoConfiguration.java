package ai.driftkit.workflow.controllers.autoconfigure;

import ai.driftkit.workflow.controllers.controller.*;
import ai.driftkit.workflow.controllers.service.*;
import ai.driftkit.workflow.engine.spring.service.WorkflowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for workflow REST controllers.
 * This module provides web endpoints for workflow operations.
 */
@Slf4j
@AutoConfiguration
@ConditionalOnWebApplication
@ConditionalOnClass({WorkflowService.class})
@ComponentScan(basePackages = {
    "ai.driftkit.workflow.controllers.controller",
    "ai.driftkit.workflow.controllers.service"
})
public class WorkflowControllersAutoConfiguration {
    
    public WorkflowControllersAutoConfiguration() {
        log.info("Initializing Workflow Controllers module");
    }
}
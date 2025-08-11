package ai.driftkit.workflow.engine.spring.autoconfigure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for DriftKit Workflow Engine.
 * 
 * <p>These properties can be configured in application.yml/properties
 * under the 'driftkit.workflow.engine' prefix.</p>
 */
@Data
@ConfigurationProperties(prefix = "driftkit.workflow.engine")
public class WorkflowEngineProperties {
    
    /**
     * Whether the workflow engine is enabled.
     */
    private boolean enabled = true;
    
    /**
     * Core number of threads in the workflow executor pool.
     */
    private int coreThreads = 10;
    
    /**
     * Maximum number of threads in the workflow executor pool.
     */
    private int maxThreads = 50;
    
    /**
     * Number of threads for scheduled tasks.
     */
    private int scheduledThreads = 5;
    
    /**
     * Keep-alive time for idle threads.
     */
    private Duration keepAliveTime = Duration.ofMinutes(1);
    
    /**
     * Queue capacity for workflow tasks.
     */
    private int queueCapacity = 1000;
    
    /**
     * Default timeout for workflow steps in milliseconds.
     */
    private long defaultStepTimeoutMs = 300_000; // 5 minutes
    
    /**
     * Controller configuration.
     */
    private ControllerProperties controller = new ControllerProperties();
    
    @Data
    public static class ControllerProperties {
        /**
         * Whether the REST controller is enabled.
         */
        private boolean enabled = true;
        
        /**
         * Base path for workflow endpoints.
         */
        private String basePath = "/api/workflows";
    }
}
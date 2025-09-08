package ai.driftkit.workflow.engine.spring.autoconfigure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for workflow tracing.
 */
@Data
@ConfigurationProperties(prefix = "driftkit.workflow.tracing")
public class WorkflowTracingProperties {
    
    /**
     * Enable or disable workflow tracing
     */
    private boolean enabled = true;
    
    /**
     * Number of threads for asynchronous trace saving
     */
    private int traceThreads = 2;
    
    /**
     * MongoDB collection name for traces
     */
    private String collection = "model_request_traces";
    
    /**
     * Enable trace logging
     */
    private boolean logTraces = false;
    
    /**
     * Maximum trace age in days (for cleanup)
     */
    private int maxTraceAgeDays = 30;
}
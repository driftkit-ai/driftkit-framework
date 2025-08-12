package ai.driftkit.workflow.engine.domain;

import ai.driftkit.workflow.engine.async.ProgressTracker;
import ai.driftkit.workflow.engine.persistence.AsyncResponseRepository;
import ai.driftkit.workflow.engine.persistence.AsyncStepStateRepository;
import ai.driftkit.workflow.engine.persistence.ChatHistoryRepository;
import ai.driftkit.workflow.engine.persistence.ChatSessionRepository;
import ai.driftkit.workflow.engine.persistence.WorkflowStateRepository;
import ai.driftkit.workflow.engine.schema.DefaultSchemaProvider;
import ai.driftkit.workflow.engine.schema.SchemaProvider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration for the WorkflowEngine.
 * This is the base configuration that can be extended by Spring properties or used directly.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowEngineConfig {
    
    /**
     * Number of core threads in the executor service.
     */
    @Builder.Default
    private int coreThreads = 10;
    
    /**
     * Maximum number of threads in the executor service.
     */
    @Builder.Default
    private int maxThreads = 50;
    
    /**
     * Queue capacity for the executor service.
     */
    @Builder.Default
    private int queueCapacity = 1000;
    
    /**
     * Number of threads for scheduled executor.
     */
    @Builder.Default
    private int scheduledThreads = 5;
    
    /**
     * Default timeout for step execution in milliseconds.
     * -1 means no timeout.
     */
    @Builder.Default
    private long defaultStepTimeoutMs = 300_000; // 5 minutes
    
    /**
     * Workflow state repository implementation.
     * If null, an in-memory implementation will be used.
     */
    private WorkflowStateRepository stateRepository;
    
    /**
     * Progress tracker implementation.
     * If null, an in-memory implementation will be used.
     */
    private ProgressTracker progressTracker;
    
    /**
     * Schema provider implementation.
     * If null, the default schema provider will be used.
     */
    @Builder.Default
    private SchemaProvider schemaProvider = new DefaultSchemaProvider();
    
    /**
     * Chat session repository implementation.
     * If null, an in-memory implementation will be used.
     */
    private ChatSessionRepository chatSessionRepository;
    
    /**
     * Chat history repository implementation.
     * If null, an in-memory implementation will be used.
     */
    private ChatHistoryRepository chatHistoryRepository;
    
    /**
     * Async response repository implementation.
     * If null, an in-memory implementation will be used.
     */
    private AsyncResponseRepository asyncResponseRepository;
    
    /**
     * Async step state repository implementation.
     * If null, an in-memory implementation will be used.
     */
    private AsyncStepStateRepository asyncStepStateRepository;
    
    /**
     * Creates a default configuration.
     */
    public static WorkflowEngineConfig defaultConfig() {
        return WorkflowEngineConfig.builder().build();
    }
    
    /**
     * Creates a configuration from Spring properties.
     */
    public static WorkflowEngineConfig fromProperties(
            int coreThreads,
            int maxThreads,
            int queueCapacity,
            int scheduledThreads,
            long defaultStepTimeoutMs) {
        return WorkflowEngineConfig.builder()
            .coreThreads(coreThreads)
            .maxThreads(maxThreads)
            .queueCapacity(queueCapacity)
            .scheduledThreads(scheduledThreads)
            .defaultStepTimeoutMs(defaultStepTimeoutMs)
            .build();
    }
}
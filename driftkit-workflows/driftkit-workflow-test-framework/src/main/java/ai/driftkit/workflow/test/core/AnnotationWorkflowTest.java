package ai.driftkit.workflow.test.core;

import ai.driftkit.workflow.engine.core.WorkflowEngine;
import ai.driftkit.workflow.engine.domain.WorkflowEngineConfig;
import ai.driftkit.workflow.engine.persistence.inmemory.InMemoryAsyncStepStateRepository;
import ai.driftkit.workflow.engine.persistence.inmemory.InMemoryChatSessionRepository;
import ai.driftkit.workflow.engine.persistence.inmemory.InMemorySuspensionDataRepository;
import ai.driftkit.workflow.engine.persistence.inmemory.InMemoryWorkflowStateRepository;
import ai.driftkit.workflow.engine.async.InMemoryProgressTracker;
import ai.driftkit.common.service.impl.InMemoryChatStore;
import ai.driftkit.common.service.impl.SimpleTextTokenizer;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * Base class for testing annotation-based workflows.
 * Provides engine configuration optimized for annotation scanning.
 */
@Slf4j
public abstract class AnnotationWorkflowTest extends WorkflowTestBase {
    
    @Override
    protected WorkflowEngine createEngine() {
        log.debug("Creating workflow engine for annotation-based workflows");
        
        // Create the state repository
        var stateRepository = new InMemoryWorkflowStateRepository();
        
        WorkflowEngineConfig config = WorkflowEngineConfig.builder()
            .coreThreads(1)
            .maxThreads(2)
            .queueCapacity(1000)
            .scheduledThreads(2)
            .defaultStepTimeoutMs(300000) // 5 minutes
            .stateRepository(stateRepository)
            .progressTracker(new InMemoryProgressTracker())
            .chatSessionRepository(new InMemoryChatSessionRepository())
            .chatStore(new InMemoryChatStore(new SimpleTextTokenizer()))
            .asyncStepStateRepository(new InMemoryAsyncStepStateRepository())
            .suspensionDataRepository(new InMemorySuspensionDataRepository())
            .build();
            
        return new WorkflowEngine(config);
    }
    
    /**
     * Registers annotation-based workflows.
     * Subclasses should override this to register their workflow instances.
     * 
     * Example:
     * <pre>{@code
     * @Override
     * protected void registerWorkflows() {
     *     engine.register(new MyAnnotatedWorkflow());
     *     engine.register(new AnotherAnnotatedWorkflow());
     * }
     * }</pre>
     */
    
    /**
     * Convenience method to register an annotation-based workflow instance.
     * 
     * @param workflowInstance the workflow instance to register
     */
    protected void registerWorkflow(Object workflowInstance) {
        Objects.requireNonNull(workflowInstance, "workflowInstance cannot be null");
        
        log.debug("Registering annotation-based workflow: {}", workflowInstance.getClass().getSimpleName());
        engine.register(workflowInstance);
    }
}
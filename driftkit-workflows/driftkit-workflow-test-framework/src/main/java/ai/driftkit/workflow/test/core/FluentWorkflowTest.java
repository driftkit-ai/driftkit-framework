package ai.driftkit.workflow.test.core;

import ai.driftkit.workflow.engine.builder.WorkflowBuilder;
import ai.driftkit.workflow.engine.graph.WorkflowGraph;
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
 * Base class for testing fluent API workflows.
 * Provides engine configuration optimized for programmatic workflow definitions.
 */
@Slf4j
public abstract class FluentWorkflowTest extends WorkflowTestBase {
    
    @Override
    protected WorkflowEngine createEngine() {
        log.debug("Creating workflow engine for fluent API workflows");
        
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
     * Registers fluent API workflows.
     * Subclasses should override this to register their workflow builders.
     * 
     * Example:
     * <pre>{@code
     * @Override
     * protected void registerWorkflows() {
     *     registerWorkflow(
     *         WorkflowBuilder.define("order-workflow", OrderRequest.class, OrderResult.class)
     *             .then("validate", this::validateOrder)
     *             .then("process", this::processOrder)
     *             .build()
     *     );
     * }
     * }</pre>
     */
    
    /**
     * Convenience method to register a fluent API workflow builder.
     * 
     * @param builder the workflow builder
     * @param <T> input type
     * @param <R> result type
     */
    protected <T, R> void registerWorkflow(WorkflowBuilder<T, R> builder) {
        Objects.requireNonNull(builder, "builder cannot be null");
        
        WorkflowGraph<T, R> graph = builder.build();
        log.debug("Registering fluent API workflow: {}", graph.id());
        engine.register(graph);
    }
    
    /**
     * Convenience method to register a workflow graph directly.
     * 
     * @param graph the workflow graph
     */
    protected void registerWorkflow(WorkflowGraph<?, ?> graph) {
        Objects.requireNonNull(graph, "graph cannot be null");
        
        log.debug("Registering workflow graph: {}", graph.id());
        engine.register(graph);
    }
}
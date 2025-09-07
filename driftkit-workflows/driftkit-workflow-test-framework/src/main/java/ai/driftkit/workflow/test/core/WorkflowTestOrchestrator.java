package ai.driftkit.workflow.test.core;

import ai.driftkit.workflow.test.assertions.AssertionEngine;
import ai.driftkit.workflow.engine.core.WorkflowEngine;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * Coordinates all test framework components.
 * Provides a unified interface for workflow testing.
 */
@Slf4j
@Getter
public class WorkflowTestOrchestrator {
    
    private final MockRegistry mockRegistry;
    private final ExecutionTracker executionTracker;
    private final AssertionEngine assertionEngine;
    private final WorkflowTestInterceptor interceptor;
    private final WorkflowEngine engine;
    
    /**
     * Creates an orchestrator with existing components.
     */
    public WorkflowTestOrchestrator(MockRegistry mockRegistry, 
                                  ExecutionTracker executionTracker,
                                  WorkflowTestInterceptor interceptor,
                                  WorkflowEngine engine) {
        this.mockRegistry = Objects.requireNonNull(mockRegistry, "mockRegistry cannot be null");
        this.executionTracker = Objects.requireNonNull(executionTracker, "executionTracker cannot be null");
        this.interceptor = Objects.requireNonNull(interceptor, "interceptor cannot be null");
        this.engine = Objects.requireNonNull(engine, "engine cannot be null");
        
        // Create assertion engine
        this.assertionEngine = new AssertionEngine(executionTracker);
        
        log.debug("WorkflowTestOrchestrator initialized");
    }
    
    /**
     * Gets a mock builder for configuration.
     * 
     * @return mock builder
     */
    public MockBuilder mock() {
        return new MockBuilder(mockRegistry);
    }
    
    /**
     * Gets the assertion engine for verifications.
     * 
     * @return assertion engine
     */
    public AssertionEngine assertions() {
        return assertionEngine;
    }
    
    /**
     * Resets all test state.
     */
    public void reset() {
        log.debug("Resetting test orchestrator");
        mockRegistry.clear();
        executionTracker.clear();
    }
    
    /**
     * Prepares for a new test scenario.
     * Clears previous state but keeps configuration.
     */
    public void prepare() {
        log.debug("Preparing for new test scenario");
        executionTracker.clear();
    }
}
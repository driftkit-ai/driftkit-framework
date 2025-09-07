package ai.driftkit.workflow.engine.builder;

import ai.driftkit.workflow.engine.builder.StepDefinition;
import ai.driftkit.workflow.engine.core.*;
import ai.driftkit.workflow.engine.domain.WorkflowEngineConfig;
import ai.driftkit.workflow.engine.graph.WorkflowGraph;
import ai.driftkit.workflow.engine.persistence.inmemory.*;
import ai.driftkit.common.service.ChatStore;
import ai.driftkit.common.service.TextTokenizer;
import ai.driftkit.common.service.impl.InMemoryChatStore;
import ai.driftkit.common.service.impl.SimpleTextTokenizer;
import ai.driftkit.workflow.engine.async.InMemoryProgressTracker;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify that type information flows correctly through branches.
 */
@Slf4j
public class TypeFlowTest {
    
    private WorkflowEngine engine;
    
    @BeforeEach
    public void setUp() {
        WorkflowEngineConfig config = WorkflowEngineConfig.builder()
            .stateRepository(new InMemoryWorkflowStateRepository())
            .progressTracker(new InMemoryProgressTracker())
            .chatSessionRepository(new InMemoryChatSessionRepository())
            .chatStore(new InMemoryChatStore(new SimpleTextTokenizer()))
            .asyncStepStateRepository(new InMemoryAsyncStepStateRepository())
            .suspensionDataRepository(new InMemorySuspensionDataRepository())
            .build();
        
        engine = new WorkflowEngine(config);
    }
    
    @Test
    @DisplayName("Type flow through branches - branches should receive previous step output")
    public void testBranchTypeFlow() throws Exception {
        // Create workflow with typed steps using StepDefinition.of()
        WorkflowGraph<Input, Output> workflow = WorkflowBuilder
            .define("type-flow-test", Input.class, Output.class)
            .then(StepDefinition.of("step1", this::step1))  // Input -> StepResult<Middle>
            .branch(
                ctx -> ctx.step("step1").output(Middle.class)
                    .map(m -> m.getValue() > 5)
                    .orElse(false),
                
                // Branch should receive Middle, not Input
                trueBranch -> trueBranch.then(StepDefinition.of("step2High", this::step2High)),  // Middle -> StepResult<Output>
                falseBranch -> falseBranch.then(StepDefinition.of("step2Low", this::step2Low))  // Middle -> StepResult<Output>
            )
            .build();
        
        engine.register(workflow);
        
        // Test with high value
        Input input1 = new Input(10);
        WorkflowEngine.WorkflowExecution<Output> exec1 = engine.execute("type-flow-test", input1);
        Output result1 = exec1.get(5, TimeUnit.SECONDS);
        
        assertNotNull(result1);
        assertEquals("High: 10", result1.getResult());
        
        // Test with low value
        Input input2 = new Input(3);
        WorkflowEngine.WorkflowExecution<Output> exec2 = engine.execute("type-flow-test", input2);
        Output result2 = exec2.get(5, TimeUnit.SECONDS);
        
        assertNotNull(result2);
        assertEquals("Low: 3", result2.getResult());
    }
    
    // Step methods with proper types
    public StepResult<Middle> step1(Input input) {
        log.info("step1: Input value = {}", input.getValue());
        return new StepResult.Continue<>(new Middle(input.getValue()));
    }
    
    public StepResult<Output> step2High(Middle middle) {
        log.info("step2High: Middle value = {}", middle.getValue());
        return new StepResult.Finish<>(new Output("High: " + middle.getValue()));
    }
    
    public StepResult<Output> step2Low(Middle middle) {
        log.info("step2Low: Middle value = {}", middle.getValue());
        return new StepResult.Finish<>(new Output("Low: " + middle.getValue()));
    }
    
    // Domain types
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Input {
        private int value;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Middle {
        private int value;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Output {
        private String result;
    }
}
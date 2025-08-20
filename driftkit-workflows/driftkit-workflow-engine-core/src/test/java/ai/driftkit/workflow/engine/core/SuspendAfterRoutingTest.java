package ai.driftkit.workflow.engine.core;

import ai.driftkit.workflow.engine.annotations.*;
import ai.driftkit.workflow.engine.domain.WorkflowEngineConfig;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import ai.driftkit.workflow.engine.persistence.inmemory.InMemoryWorkflowStateRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@DisplayName("Test suspension after routing")
class SuspendAfterRoutingTest {

    private WorkflowEngine engine;
    
    @BeforeEach
    void setUp() {
        WorkflowEngineConfig config = WorkflowEngineConfig.builder()
            .stateRepository(new InMemoryWorkflowStateRepository())
            .coreThreads(2)
            .maxThreads(10)
            .build();
            
        engine = new WorkflowEngine(config);
        
        TestWorkflow workflow = new TestWorkflow();
        engine.register(workflow);
    }
    
    @Test
    @DisplayName("Should handle suspension after routing correctly")
    void testSuspendAfterRouting() throws Exception {
        // Start workflow
        var execution = engine.execute("test-workflow", "start");
        String runId = execution.getRunId();
        
        // Wait for suspension
        Thread.sleep(500);
        
        // Check suspended at initial step
        Optional<WorkflowInstance> instanceOpt = engine.getWorkflowInstance(runId);
        assertTrue(instanceOpt.isPresent());
        assertEquals(WorkflowInstance.WorkflowStatus.SUSPENDED, instanceOpt.get().getStatus());
        
        // Resume with first input
        FirstInput firstInput = new FirstInput("data1");
        execution = engine.resume(runId, firstInput);
        
        // Wait for suspension at second step
        Thread.sleep(500);
        
        // Check suspended at second step
        instanceOpt = engine.getWorkflowInstance(runId);
        assertTrue(instanceOpt.isPresent());
        assertEquals(WorkflowInstance.WorkflowStatus.SUSPENDED, instanceOpt.get().getStatus());
        
        // Resume with second input
        SecondInput secondInput = new SecondInput("data2");
        execution = engine.resume(runId, secondInput);
        
        // Get final result
        String result = (String) execution.get(5, TimeUnit.SECONDS);
        assertEquals("Result: data1 + data2", result);
    }
    
    @Workflow(
        id = "test-workflow",
        version = "1.0",
        description = "Test workflow that suspends after routing"
    )
    public static class TestWorkflow {
        
        @InitialStep
        public StepResult<String> start(WorkflowContext context, String input) {
            log.info("Initial step executing");
            return StepResult.suspend("Enter first data", FirstInput.class);
        }
        
        @Step
        public StepResult<String> processFirst(WorkflowContext context, FirstInput input) {
            log.info("Processing first input: {}", input.getData());
            context.setContextValue("firstData", input.getData());
            // This step suspends after being reached via routing
            return StepResult.suspend("Enter second data", SecondInput.class);
        }
        
        @Step
        public StepResult<String> processSecond(WorkflowContext context, SecondInput input) {
            log.info("Processing second input: {}", input.getData());
            String firstData = context.getString("firstData");
            return StepResult.finish("Result: " + firstData + " + " + input.getData());
        }
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FirstInput {
        private String data;
    }
    
    @Data
    @NoArgsConstructor  
    @AllArgsConstructor
    public static class SecondInput {
        private String data;
    }
}
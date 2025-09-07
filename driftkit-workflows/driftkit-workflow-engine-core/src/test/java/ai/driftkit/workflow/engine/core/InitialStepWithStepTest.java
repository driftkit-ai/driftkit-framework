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
@DisplayName("Test @InitialStep with @Step combination")
class InitialStepWithStepTest {

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
    @DisplayName("Should allow @InitialStep with @Step annotation")
    void testInitialStepWithStep() throws Exception {
        // Start workflow
        var execution = engine.execute("test-workflow", "start");
        String runId = execution.getRunId();
        
        // Wait for suspension
        Thread.sleep(500);
        
        // Check suspended
        Optional<WorkflowInstance> instanceOpt = engine.getWorkflowInstance(runId);
        assertTrue(instanceOpt.isPresent());
        assertEquals(WorkflowInstance.WorkflowStatus.SUSPENDED, instanceOpt.get().getStatus());
        
        // Resume with user input
        UserInput input = new UserInput("test user");
        execution = engine.resume(runId, input);
        
        // Get final result
        String result = (String) execution.get(5, TimeUnit.SECONDS);
        assertEquals("Hello test user", result);
    }
    
    @Workflow(
        id = "test-workflow",
        version = "1.0",
        description = "Test workflow for @InitialStep with @Step"
    )
    public static class TestWorkflow {
        
        @InitialStep(description = "Initial step that also has @Step annotation")
        @Step(nextClasses = { UserInput.class })
        public StepResult<String> start(WorkflowContext context, String input) {
            log.info("Starting workflow with input: {}", input);
            return StepResult.suspend("Please enter your name", UserInput.class);
        }
        
        @Step
        public StepResult<String> processUserInput(WorkflowContext context, UserInput input) {
            log.info("Processing user input: {}", input.getName());
            return StepResult.finish("Hello " + input.getName());
        }
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInput {
        private String name;
    }
}
package ai.driftkit.workflow.engine.agent.integration;

import ai.driftkit.workflow.engine.agent.*;
import ai.driftkit.workflow.engine.core.StepResult;
import ai.driftkit.workflow.engine.core.WorkflowContext;
import ai.driftkit.workflow.engine.builder.StepDefinition;
import ai.driftkit.workflow.engine.builder.WorkflowBuilder;
import ai.driftkit.common.domain.client.ModelClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test class demonstrating integration of LLMAgent with DriftKit Workflow Engine.
 * Shows how to convert agents into workflow steps and use them in workflows.
 */
@Slf4j
public class AgentWorkflowIntegrationTest {
    
    @Mock
    private ModelClient modelClient;
    
    @Mock
    private LLMAgent mockAgent;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }
    
    /**
     * Helper method to convert a simple text agent execution into a workflow step.
     */
    public static StepDefinition textStep(String stepId, LLMAgent agent) {
        return StepDefinition.of(stepId, (input, ctx) -> {
            try {
                AgentResponse<String> response = agent.executeText((String) input);
                return StepResult.continueWith(response.getText());
            } catch (Exception e) {
                log.error("Error executing text step", e);
                return StepResult.fail(e.getMessage());
            }
        });
    }
    
    /**
     * Helper method to convert a structured output agent execution into a workflow step.
     */
    public static <T> StepDefinition structuredStep(String stepId, LLMAgent agent, Class<T> outputType) {
        return StepDefinition.of(stepId, (input, ctx) -> {
            try {
                AgentResponse<T> response = agent.executeStructured((String) input, outputType);
                return StepResult.continueWith(response.getStructuredData());
            } catch (Exception e) {
                log.error("Error executing structured step", e);
                return StepResult.fail(e.getMessage());
            }
        });
    }
    
    /**
     * Helper method to convert an agent with template execution into a workflow step.
     */
    public static <I> StepDefinition templateStep(String stepId, LLMAgent agent,
                                                             String promptId,
                                                             Function<I, Map<String, Object>> variableExtractor) {
        return StepDefinition.of(stepId, (input, ctx) -> {
            try {
                Map<String, Object> variables = variableExtractor.apply((I) input);
                AgentResponse<String> response = agent.executeWithPrompt(promptId, variables);
                return StepResult.continueWith(response.getText());
            } catch (Exception e) {
                log.error("Error executing template step", e);
                return StepResult.fail(e.getMessage());
            }
        });
    }
    
    /**
     * Helper method to convert a LoopAgent into a workflow step.
     */
    public static StepDefinition loopStep(String stepId, LoopAgent loopAgent) {
        return StepDefinition.of(stepId, (input, ctx) -> {
            try {
                String result = loopAgent.execute((String) input);
                return StepResult.continueWith(result);
            } catch (Exception e) {
                log.error("Error executing loop step", e);
                return StepResult.fail(e.getMessage());
            }
        });
    }
    
    /**
     * Helper method to convert a SequentialAgent into a workflow step.
     */
    public static StepDefinition sequentialStep(String stepId, SequentialAgent sequentialAgent) {
        return StepDefinition.of(stepId, (input, ctx) -> {
            try {
                String result = sequentialAgent.execute((String) input);
                return StepResult.continueWith(result);
            } catch (Exception e) {
                log.error("Error executing sequential step", e);
                return StepResult.fail(e.getMessage());
            }
        });
    }
    
    /**
     * Helper method to create a step that uses agent for decision making in branching.
     */
    public static <I> Function<WorkflowContext, Boolean> agentPredicate(LLMAgent agent,
                                                                        String decisionPrompt,
                                                                        Function<WorkflowContext, I> inputExtractor) {
        return ctx -> {
            try {
                I input = inputExtractor.apply(ctx);
                String prompt = String.format(decisionPrompt, input);
                AgentResponse<Boolean> response = agent.executeStructured(prompt, Boolean.class);
                return response.getStructuredData();
            } catch (Exception e) {
                log.error("Error in agent predicate", e);
                return false; // Default to false on error
            }
        };
    }
    
    @Test
    void testTextStepIntegration() throws Exception {
        // Setup
        when(mockAgent.executeText("test input"))
            .thenReturn(AgentResponse.text("test output"));
        
        // Create step
        StepDefinition step = textStep("test-step", mockAgent);
        
        // Execute
        StepResult<?> result = step.getExecutor().execute("test input", mock(WorkflowContext.class));
        
        // Verify
        assertInstanceOf(StepResult.Continue.class, result);
        StepResult.Continue<?> continueResult = (StepResult.Continue<?>) result;
        assertEquals("test output", continueResult.data());
    }
    
    @Test
    void testStructuredStepIntegration() throws Exception {
        // Setup
        TestData testData = new TestData("test", 42);
        when(mockAgent.executeStructured("test input", TestData.class))
            .thenReturn(AgentResponse.structured(testData));
        
        // Create step
        StepDefinition step = structuredStep("test-step", mockAgent, TestData.class);
        
        // Execute
        StepResult<?> result = step.getExecutor().execute("test input", mock(WorkflowContext.class));
        
        // Verify
        assertInstanceOf(StepResult.Continue.class, result);
        StepResult.Continue<?> continueResult = (StepResult.Continue<?>) result;
        assertEquals(testData, continueResult.data());
    }
    
    /**
     * Example workflow builder demonstrating agent integration.
     */
    public static class ExampleWorkflow {
        
        public static WorkflowBuilder<String, String> buildAnalysisWorkflow(ModelClient modelClient) {
            // Create specialized agents
            LLMAgent analyzer = LLMAgent.builder()
                .modelClient(modelClient)
                .systemMessage("You are a text analysis expert")
                .temperature(0.3)
                .build();
            
            LLMAgent summarizer = LLMAgent.builder()
                .modelClient(modelClient)
                .systemMessage("You are a concise summarizer")
                .temperature(0.5)
                .build();
            
            // Create sequential processor
            SequentialAgent processor = SequentialAgent.builder()
                .agent(analyzer)
                .agent(summarizer)
                .build();
            
            // Build workflow
            return WorkflowBuilder.define("text-analysis", String.class, String.class)
                .withDescription("Analyzes and summarizes text using AI agents")
                
                // Validate input
                .then("validate", (text, ctx) -> {
                    String textInput = (String) text;
                    if (StringUtils.isEmpty(textInput)) {
                        return StepResult.fail("Input text cannot be empty");
                    }
                    return StepResult.continueWith(textInput);
                })
                
                // Process with sequential agent
                .then(sequentialStep("analyze-and-summarize", processor))
                
                // Add metadata
                .then("add-metadata", (summary, ctx) -> {
                    String result = String.format(
                        "Summary generated on %s:\n%s",
                        java.time.LocalDateTime.now(),
                        summary
                    );
                    return StepResult.continueWith(result);
                });
        }
    }
    
    // Test data class
    record TestData(String name, int value) {}
}
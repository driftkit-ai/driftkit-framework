package ai.driftkit.workflow.engine.agent;

import ai.driftkit.common.domain.client.*;
import ai.driftkit.common.utils.JsonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LoopAgentStructuredOutputTest {
    
    @Mock
    private ModelClient modelClient;
    
    @Mock
    private Agent workerAgent;
    
    private LLMAgent evaluatorAgent;
    private LoopAgent loopAgent;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Create evaluator as LLMAgent to test structured output
        evaluatorAgent = LLMAgent.builder()
            .modelClient(modelClient)
            .systemMessage("Evaluate the result")
            .build();
            
        loopAgent = LoopAgent.builder()
            .worker(workerAgent)
            .evaluator(evaluatorAgent)
            .stopCondition(LoopStatus.COMPLETE)
            .maxIterations(3)
            .build();
    }
    
    @Test
    void testStructuredOutputIsUsedForEvaluation() throws Exception {
        // Given
        String input = "Create a travel plan";
        String workerResult = "Day 1: Visit Louvre, Day 2: Eiffel Tower, Day 3: Seine boat trip";
        
        when(workerAgent.execute(anyString())).thenReturn(workerResult);
        
        // Mock model response for structured output
        EvaluationResult evalResult = EvaluationResult.builder()
            .status(LoopStatus.COMPLETE)
            .reason("All requirements met")
            .build();
            
        ModelTextResponse mockResponse = ModelTextResponse.builder()
            .choices(java.util.List.of(
                ModelTextResponse.ResponseMessage.builder()
                    .message(ModelMessage.builder()
                        .content(JsonUtils.toJson(evalResult))
                        .build())
                    .build()
            ))
            .build();
            
        when(modelClient.textToText(any(ModelTextRequest.class))).thenReturn(mockResponse);
        
        // When
        String result = loopAgent.execute(input);
        
        // Then
        assertEquals(workerResult, result);
        
        // Verify structured output was requested
        ArgumentCaptor<ModelTextRequest> requestCaptor = ArgumentCaptor.forClass(ModelTextRequest.class);
        verify(modelClient, atLeastOnce()).textToText(requestCaptor.capture());
        
        ModelTextRequest capturedRequest = requestCaptor.getValue();
        assertNotNull(capturedRequest.getResponseFormat());
        assertEquals(ResponseFormat.ResponseType.JSON_SCHEMA, capturedRequest.getResponseFormat().getType());
    }
    
    @Test
    void testFallbackToTraditionalJsonParsing() {
        // Given
        String input = "Create a travel plan";
        String workerResult = "Day 1: Visit museums";
        
        // Use a non-LLMAgent evaluator
        Agent simpleEvaluator = mock(Agent.class);
        when(simpleEvaluator.execute(anyString())).thenReturn(
            "{\"status\": \"REVISE\", \"feedback\": \"Missing Eiffel Tower\"}"
        );
        
        LoopAgent loopWithSimpleEvaluator = LoopAgent.builder()
            .worker(workerAgent)
            .evaluator(simpleEvaluator)
            .stopCondition(LoopStatus.COMPLETE)
            .maxIterations(2)
            .build();
            
        when(workerAgent.execute(anyString()))
            .thenReturn(workerResult)
            .thenReturn("Day 1: Museums, Day 2: Eiffel Tower");
            
        when(simpleEvaluator.execute(anyString()))
            .thenReturn("{\"status\": \"REVISE\", \"feedback\": \"Missing Eiffel Tower\"}")
            .thenReturn("{\"status\": \"COMPLETE\", \"reason\": \"All good\"}");
        
        // When
        String result = loopWithSimpleEvaluator.execute(input);
        
        // Then
        assertEquals("Day 1: Museums, Day 2: Eiffel Tower", result);
        verify(simpleEvaluator, times(2)).execute(anyString());
    }
}
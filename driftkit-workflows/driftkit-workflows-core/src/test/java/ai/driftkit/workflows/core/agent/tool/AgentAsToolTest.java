package ai.driftkit.workflows.core.agent.tool;

import ai.driftkit.common.domain.client.ModelClient;
import ai.driftkit.common.tools.ToolInfo;
import ai.driftkit.workflows.core.agent.Agent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class AgentAsToolTest {
    
    @Mock
    private Agent mockAgent;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }
    
    @Test
    void testCreateReturnsToolInfo() {
        // Given
        String toolName = "searchTool";
        String toolDescription = "Searches for information";
        when(mockAgent.execute(anyString())).thenReturn("search result");
        
        // When
        ToolInfo toolInfo = AgentAsTool.create(toolName, toolDescription, mockAgent);
        
        // Then
        assertNotNull(toolInfo);
        assertEquals(toolName, toolInfo.getFunctionName());
        assertEquals(toolDescription, toolInfo.getDescription());
        assertNotNull(toolInfo.getInstance());
        assertTrue(toolInfo.getInstance() instanceof AgentAsTool);
        assertFalse(toolInfo.isStatic());
        assertNull(toolInfo.getMethod());
        assertNotNull(toolInfo.getParameterTypes());
        assertEquals(1, toolInfo.getParameterTypes().size());
        assertEquals(SimpleToolArguments.class, toolInfo.getParameterTypes().get(0));
        assertEquals(String.class, toolInfo.getReturnType());
        assertNotNull(toolInfo.getToolDefinition());
    }
    
    @Test
    void testToolDefinitionIsCorrect() {
        // Given
        String toolName = "flightSearch";
        String toolDescription = "Searches for flights";
        
        // When
        ToolInfo toolInfo = AgentAsTool.create(toolName, toolDescription, mockAgent);
        ModelClient.Tool toolDef = toolInfo.getToolDefinition();
        
        // Then
        assertNotNull(toolDef);
        assertEquals(ModelClient.ResponseFormatType.function, toolDef.getType());
        assertNotNull(toolDef.getFunction());
        assertEquals(toolName, toolDef.getFunction().getName());
        assertEquals(toolDescription, toolDef.getFunction().getDescription());
        assertNotNull(toolDef.getFunction().getParameters());
    }
    
    @Test
    void testAgentAsToolExecution() throws Exception {
        // Given
        String toolName = "processTool";
        String toolDescription = "Processes input";
        String testInput = "test input";
        String expectedOutput = "processed: " + testInput;
        when(mockAgent.execute(testInput)).thenReturn(expectedOutput);
        
        // When
        ToolInfo toolInfo = AgentAsTool.create(toolName, toolDescription, mockAgent);
        AgentAsTool agentAsTool = (AgentAsTool) toolInfo.getInstance();
        SimpleToolArguments args = new SimpleToolArguments();
        args.setInput(testInput);
        String result = agentAsTool.execute(args);
        
        // Then
        assertEquals(expectedOutput, result);
    }
    
    @Test
    void testParameterSchemaIsCorrect() {
        // Given
        String toolName = "testTool";
        String toolDescription = "Test tool";
        
        // When
        ToolInfo toolInfo = AgentAsTool.create(toolName, toolDescription, mockAgent);
        AgentAsTool agentAsTool = (AgentAsTool) toolInfo.getInstance();
        ToolParameterSchema schema = agentAsTool.getParametersSchema();
        
        // Then
        assertNotNull(schema);
        assertEquals("object", schema.getType());
        assertNotNull(schema.getProperties());
        assertTrue(schema.getProperties().containsKey("input"));
        assertNotNull(schema.getRequired());
        assertTrue(schema.getRequired().contains("input"));
        
        ToolParameterSchema.PropertySchema inputProp = schema.getProperties().get("input");
        assertNotNull(inputProp);
        assertEquals("string", inputProp.getType());
        assertEquals("The input text to process", inputProp.getDescription());
    }
}
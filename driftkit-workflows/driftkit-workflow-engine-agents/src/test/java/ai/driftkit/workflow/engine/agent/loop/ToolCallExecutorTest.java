package ai.driftkit.workflow.engine.agent.loop;

import ai.driftkit.common.tools.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolCallExecutorTest {

    private TestTools tools;
    private ToolCallExecutor executor;

    @BeforeEach
    void setUp() {
        tools = new TestTools();
        ToolRegistry registry = new ToolRegistry();
        registry.registerClass(tools);
        executor = new ToolCallExecutor(registry, 4);
    }

    @Test
    void mixedBatchKeepsUnsafeToolsSerialAndOrderStable() {
        List<ToolCallExecutor.ResolvedCall> calls = List.of(
                ToolCallExecutor.ResolvedCall.execute(MockModelClient.toolCall("c1", "getWeather", Map.of("city", "A"))),
                ToolCallExecutor.ResolvedCall.execute(MockModelClient.toolCall("c2", "writeRecord", Map.of("key", "k", "value", "v"))),
                ToolCallExecutor.ResolvedCall.execute(MockModelClient.toolCall("c3", "searchCatalog", Map.of("query", "q"))));

        List<ToolCallExecutor.ExecutedCall> results = executor.executeAll(calls);

        assertEquals(3, results.size());
        assertEquals("c1", results.get(0).getCall().getId());
        assertEquals("c2", results.get(1).getCall().getId());
        assertEquals("c3", results.get(2).getCall().getId());
        assertTrue(results.get(0).getResult().isSuccess());
        assertTrue(results.get(1).getResult().isSuccess());
        assertTrue(results.get(1).getRenderedContent().contains("written k=v"));
    }

    @Test
    void deniedCallProducesResultWithoutExecution() {
        List<ToolCallExecutor.ExecutedCall> results = executor.executeAll(List.of(
                ToolCallExecutor.ResolvedCall.deny(
                        MockModelClient.toolCall("c1", "writeRecord", Map.of("key", "k", "value", "v")),
                        "not allowed")));

        assertFalse(results.get(0).getResult().isSuccess());
        assertTrue(results.get(0).getRenderedContent().contains("not allowed"));
        assertTrue(tools.invocations.isEmpty());
    }

    @Test
    void unknownToolFailsGracefully() {
        List<ToolCallExecutor.ExecutedCall> results = executor.executeAll(List.of(
                ToolCallExecutor.ResolvedCall.execute(
                        MockModelClient.toolCall("c1", "noSuchTool", Map.of("a", "b")))));

        assertFalse(results.get(0).getResult().isSuccess());
        assertTrue(results.get(0).getRenderedContent().contains("Tool execution failed"));
    }

    @Test
    void truncationAddsExplicitMarker() {
        assertEquals("abc", ToolCallExecutor.truncate("abc", 100));
        String truncated = ToolCallExecutor.truncate("x".repeat(200), 50);
        assertTrue(truncated.startsWith("x".repeat(50)));
        assertTrue(truncated.contains("[truncated 150 chars]"));
        assertNull(ToolCallExecutor.truncate(null, 50));
        assertEquals("abc", ToolCallExecutor.truncate("abc", 0), "non-positive limit = unlimited");
    }
}

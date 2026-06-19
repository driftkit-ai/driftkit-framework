package ai.driftkit.workflow.engine.agent.loop;

import ai.driftkit.common.domain.client.ModelMessage;
import ai.driftkit.common.domain.client.Role;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DefaultContextManagerTest {

    private LoopState stateWithToolHistory(int exchanges) {
        LoopState state = LoopState.builder().build();
        state.addMessage(ModelMessage.system("system prompt"));
        for (int i = 0; i < exchanges; i++) {
            state.addMessage(ModelMessage.user("question " + i));
            state.addMessage(ModelMessage.assistantToolCalls("checking " + i, List.of(
                    MockModelClient.toolCall("call_" + i, "getWeather", java.util.Map.of("city", "X" + i)))));
            state.addMessage(ModelMessage.tool("tool result payload ".repeat(20) + i, "call_" + i));
            state.addMessage(ModelMessage.assistant("answer " + i));
        }
        return state;
    }

    @Test
    void microCompactionElidesOnlyOldToolResults() {
        DefaultContextManager manager = DefaultContextManager.builder()
                .summaryEnabled(false)
                .keepRecentMessages(4)
                .minElisionChars(100)
                .build();

        LoopState state = stateWithToolHistory(3);
        int total = state.getMessages().size(); // 1 system + 3*4 = 13

        manager.microCompact(state);

        // Old tool results elided…
        ModelMessage oldTool = state.getMessages().get(3);
        assertEquals(Role.tool, oldTool.getRole());
        assertTrue(oldTool.getContent().startsWith("[tool result elided:"));
        assertEquals("call_0", oldTool.getToolCallId(), "pairing must survive compaction");

        // …recent window untouched
        ModelMessage recentTool = state.getMessages().get(total - 2);
        assertEquals(Role.tool, recentTool.getRole());
        assertFalse(recentTool.getContent().startsWith("[tool result elided:"));

        // Structure unchanged
        assertEquals(total, state.getMessages().size());
        assertEquals(Role.system, state.getMessages().get(0).getRole());
    }

    @Test
    void summaryCompactionReplacesOldMessagesAndKeepsInvariants() {
        MockModelClient summarizer = new MockModelClient();
        summarizer.enqueueText("SUMMARY: user asked about weather in X0..X2, answers given");

        DefaultContextManager manager = DefaultContextManager.builder()
                .modelClient(summarizer)
                .keepRecentMessages(4)
                .build();

        LoopState state = stateWithToolHistory(3);
        int before = state.getMessages().size();

        assertTrue(manager.summaryCompact(state));

        List<ModelMessage> messages = state.getMessages();
        assertTrue(messages.size() < before);
        // Invariant: system head preserved
        assertEquals(Role.system, messages.get(0).getRole());
        // Summary message inserted right after the head
        assertTrue(messages.get(1).getContent().contains("CONTEXT SUMMARY"));
        assertTrue(messages.get(1).getContent().contains("SUMMARY: user asked"));
        // Invariant: no orphan tool message at the start of the kept suffix
        assertNotEquals(Role.tool, messages.get(2).getRole());
        // The summarizer was called with the compact prompt
        assertTrue(summarizer.requests.get(0).getMessages().get(0).getContent().get(0).getText()
                .contains("TEXT ONLY"));
    }

    @Test
    void summaryCircuitBreakerDegradesToMicroCompactionOnly() {
        MockModelClient failing = new MockModelClient();
        failing.enqueueFailure("boom 1");
        failing.enqueueFailure("boom 2");
        failing.enqueueFailure("boom 3");

        DefaultContextManager manager = DefaultContextManager.builder()
                .modelClient(failing)
                .keepRecentMessages(4)
                .build();

        LoopState state = stateWithToolHistory(3);
        assertFalse(manager.summaryCompact(state));
        assertFalse(manager.summaryCompact(state));
        assertFalse(manager.summaryCompact(state));
        // Breaker open: no further model calls
        assertFalse(manager.summaryCompact(state));
        assertEquals(3, failing.requests.size());
    }

    @Test
    void microCompactionIsCacheAwareAndSkipsWhenSavingsAreTooSmall() {
        // Savings below the amortization threshold: editing old messages would
        // invalidate the provider prefix cache without paying for itself.
        DefaultContextManager manager = DefaultContextManager.builder()
                .summaryEnabled(false)
                .keepRecentMessages(4)
                .minElisionChars(1_000_000)
                .build();

        LoopState state = stateWithToolHistory(3);
        String oldToolContent = state.getMessages().get(3).getContent();

        manager.microCompact(state);

        assertEquals(oldToolContent, state.getMessages().get(3).getContent(),
                "below-threshold savings must not invalidate the prefix cache");
    }

    @Test
    void zeroThresholdAlwaysElides() {
        DefaultContextManager manager = DefaultContextManager.builder()
                .summaryEnabled(false)
                .keepRecentMessages(4)
                .minElisionChars(0)
                .build();

        LoopState state = stateWithToolHistory(3);
        manager.microCompact(state);

        assertTrue(state.getMessages().get(3).getContent().startsWith("[tool result elided:"),
                "minElisionChars=0 must always elide old tool results");
    }

    @Test
    void manageBeforeTurnIsNoopUnderThreshold() {
        DefaultContextManager manager = DefaultContextManager.builder()
                .summaryEnabled(false)
                .build();
        LoopState state = stateWithToolHistory(1);
        String toolContentBefore = state.getMessages().get(3).getContent();

        manager.manageBeforeTurn(state);

        assertEquals(toolContentBefore, state.getMessages().get(3).getContent());
    }

    @Test
    void tokenEstimationIsPessimistic() {
        // 300 chars -> at least 100 tokens with the 3-chars-per-token heuristic
        assertEquals(100, TokenEstimator.estimateTokens("x".repeat(300)));
        assertEquals(0, TokenEstimator.estimateTokens(""));
        assertTrue(TokenEstimator.estimateTokens(ModelMessage.user("abc")) > 0);
    }
}

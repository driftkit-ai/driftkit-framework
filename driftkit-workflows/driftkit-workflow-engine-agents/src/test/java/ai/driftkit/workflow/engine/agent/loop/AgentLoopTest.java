package ai.driftkit.workflow.engine.agent.loop;

import ai.driftkit.common.domain.client.ModelMessage;
import ai.driftkit.common.domain.client.Role;
import ai.driftkit.common.tools.ToolCall;
import ai.driftkit.common.tools.ToolRegistry;
import ai.driftkit.common.utils.JsonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AgentLoopTest {

    private MockModelClient client;
    private TestTools tools;
    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        client = new MockModelClient();
        tools = new TestTools();
        registry = new ToolRegistry();
        registry.registerClass(tools);
    }

    private AgentLoop loop(LoopPolicy policy, AgentHook... hooks) {
        return AgentLoop.builder()
                .modelClient(client)
                .toolExecutor(new ToolCallExecutor(registry, policy.getMaxToolConcurrency()))
                .policy(policy)
                .hooks(Arrays.asList(hooks))
                .requestConfig(AgentLoop.RequestConfig.builder()
                        .model("mock-model")
                        .tools(Arrays.asList(registry.getTools()))
                        .build())
                .build();
    }

    private LoopState stateWithUser(String message) {
        LoopState state = LoopState.builder().build();
        state.addMessage(ModelMessage.system("You are a test agent"));
        state.addMessage(ModelMessage.user(message));
        return state;
    }

    @Nested
    class MultiTurn {

        @Test
        void toolResultsAreFedBackToTheModel() {
            client.enqueueToolCalls("checking weather",
                    MockModelClient.toolCall("call_1", "getWeather", Map.of("city", "Berlin")));
            client.enqueueText("It is sunny in Berlin");

            AgentLoopResult result = loop(LoopPolicy.defaults()).run(stateWithUser("weather in berlin?"));

            assertEquals(StopReason.END_TURN, result.getStopReason());
            assertEquals("It is sunny in Berlin", result.getText());
            assertEquals(2, result.getTurns());
            assertEquals(1, result.getToolResults().size());
            assertTrue(result.getToolResults().get(0).isSuccess());

            // The SECOND model request must contain the assistant tool-call echo and the tool result
            List<ModelMessage> secondRequest = client.lastRequestMessagesAsModelMessages();
            ModelMessage assistantEcho = secondRequest.stream()
                    .filter(m -> m.getRole() == Role.assistant && m.getToolCalls() != null)
                    .findFirst().orElseThrow(() -> new AssertionError("assistant tool-call echo missing"));
            assertEquals("getWeather", assistantEcho.getToolCalls().get(0).getFunction().getName());

            ModelMessage toolResult = secondRequest.stream()
                    .filter(m -> m.getRole() == Role.tool)
                    .findFirst().orElseThrow(() -> new AssertionError("tool result message missing"));
            assertEquals("call_1", toolResult.getToolCallId());
            assertTrue(toolResult.getContent().contains("Sunny in Berlin"));

            // Usage accumulated over both turns
            assertEquals(2, result.getUsage().getModelCalls());
            assertEquals(30, result.getUsage().getTotalTokens());
        }

        @Test
        void multipleSafeToolsRunConcurrentlyWithResultsInOrder() throws Exception {
            tools.concurrencyLatch = new java.util.concurrent.CountDownLatch(2);
            client.enqueueToolCalls(null,
                    MockModelClient.toolCall("call_a", "getWeather", Map.of("city", "Paris")),
                    MockModelClient.toolCall("call_b", "searchCatalog", Map.of("query", "umbrella")));
            client.enqueueText("done");

            AgentLoopResult result = loop(LoopPolicy.defaults()).run(stateWithUser("weather and umbrella"));

            assertEquals(StopReason.END_TURN, result.getStopReason());
            assertTrue(tools.maxObservedConcurrency.get() >= 2,
                    "safe tools must actually run in parallel, observed " + tools.maxObservedConcurrency.get());

            // Tool result messages must be in original call order
            List<ModelMessage> secondRequest = client.lastRequestMessagesAsModelMessages();
            List<ModelMessage> toolMessages = secondRequest.stream().filter(m -> m.getRole() == Role.tool).toList();
            assertEquals(2, toolMessages.size());
            assertEquals("call_a", toolMessages.get(0).getToolCallId());
            assertEquals("call_b", toolMessages.get(1).getToolCallId());
        }

        @Test
        void failingToolBecomesErrorResultAndLoopContinues() {
            client.enqueueToolCalls(null,
                    MockModelClient.toolCall("call_1", "alwaysFails", Map.of("input", "x")));
            client.enqueueText("recovered");

            AgentLoopResult result = loop(LoopPolicy.defaults()).run(stateWithUser("try the failing tool"));

            assertEquals(StopReason.END_TURN, result.getStopReason());
            assertFalse(result.getToolResults().get(0).isSuccess());

            ModelMessage toolMessage = client.lastRequestMessagesAsModelMessages().stream()
                    .filter(m -> m.getRole() == Role.tool).findFirst().orElseThrow();
            assertTrue(toolMessage.getContent().contains("Tool execution failed"));
        }

        @Test
        void oversizedToolResultIsTruncated() {
            client.enqueueToolCalls(null,
                    MockModelClient.toolCall("call_1", "hugeResult", Map.of("seed", "abc")));
            client.enqueueText("done");

            loop(LoopPolicy.defaults()).run(stateWithUser("huge"));

            ModelMessage toolMessage = client.lastRequestMessagesAsModelMessages().stream()
                    .filter(m -> m.getRole() == Role.tool).findFirst().orElseThrow();
            assertTrue(toolMessage.getContent().contains("[truncated"));
            assertTrue(toolMessage.getContent().length() < 200);
        }
    }

    @Nested
    class MalformedModelOutput {

        @Test
        void toolCallWithNullIdDoesNotBreakTheLoop() {
            // Some providers (or buggy responses) may omit the call id — the loop must
            // still execute the tool and pair the result with a null id without NPE.
            client.enqueueToolCalls(null,
                    MockModelClient.toolCall(null, "getWeather", Map.of("city", "Oslo")));
            client.enqueueText("done despite null id");

            AgentLoopResult result = loop(LoopPolicy.defaults()).run(stateWithUser("null id"));

            assertEquals(StopReason.END_TURN, result.getStopReason());
            assertEquals(1, result.getToolResults().size());
            assertTrue(result.getToolResults().get(0).isSuccess());
        }
    }

    @Nested
    class Limits {

        @Test
        void maxTurnsStopsTheLoopWithPartialResult() {
            // Model endlessly requests tools
            for (int i = 0; i < 10; i++) {
                client.enqueueToolCalls("thinking " + i,
                        MockModelClient.toolCall("call_" + i, "getWeather", Map.of("city", "Oslo")));
            }

            LoopPolicy policy = LoopPolicy.builder().maxTurns(3).build();
            AgentLoopResult result = loop(policy).run(stateWithUser("loop forever"));

            assertEquals(StopReason.MAX_TURNS, result.getStopReason());
            assertEquals(3, result.getTurns());
            assertEquals(3, result.getToolResults().size());
        }

        @Test
        void tokenBudgetStopsTheLoop() {
            for (int i = 0; i < 10; i++) {
                client.enqueueToolCalls(null,
                        MockModelClient.toolCall("call_" + i, "getWeather", Map.of("city", "Oslo")));
            }

            // Each scripted response costs 15 tokens; budget allows two turns
            LoopPolicy policy = LoopPolicy.builder().maxTotalTokens(30L).build();
            AgentLoopResult result = loop(policy).run(stateWithUser("budget test"));

            assertEquals(StopReason.BUDGET, result.getStopReason());
            assertEquals(2, result.getTurns());
        }

        @Test
        void deadlineStopsTheLoop() {
            client.enqueue(req -> {
                try {
                    Thread.sleep(80);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return MockModelClient.toolCallResponse(null,
                        MockModelClient.toolCall("call_1", "getWeather", Map.of("city", "Oslo")));
            });
            client.enqueueText("never reached");

            LoopPolicy policy = LoopPolicy.builder().maxDuration(Duration.ofMillis(50)).build();
            AgentLoopResult result = loop(policy).run(stateWithUser("deadline"));

            assertEquals(StopReason.DEADLINE, result.getStopReason());
        }

        @Test
        void policyRejectsInvalidConfiguration() {
            assertThrows(IllegalArgumentException.class, () -> LoopPolicy.builder().maxTurns(0).build());
            assertThrows(IllegalArgumentException.class,
                    () -> LoopPolicy.builder().maxTurns(LoopPolicy.HARD_CAP_TURNS + 1).build());
            assertThrows(IllegalArgumentException.class,
                    () -> LoopPolicy.builder().maxDuration(Duration.ZERO).build());
            // Upper bound prevents pathological exponential backoff configurations
            assertThrows(IllegalArgumentException.class,
                    () -> LoopPolicy.builder().maxConsecutiveFailures(11).build());
            assertThrows(IllegalArgumentException.class,
                    () -> LoopPolicy.builder().maxConsecutiveFailures(0).build());
        }

        @Test
        void deadlineIsEnforcedInsideRetryLoop() {
            // First call burns past the deadline, then fails — the retry attempt must
            // NOT fire another model call after the deadline.
            client.enqueue(req -> {
                try {
                    Thread.sleep(80);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                throw new RuntimeException("transient failure after deadline");
            });
            client.enqueueText("must never be called");

            LoopPolicy policy = LoopPolicy.builder()
                    .maxDuration(Duration.ofMillis(50))
                    .retryBaseDelay(Duration.ofMillis(1))
                    .build();
            AgentLoopResult result = loop(policy).run(stateWithUser("deadline in retry"));

            assertEquals(StopReason.DEADLINE, result.getStopReason());
            assertEquals(1, client.remainingSteps(), "no retry may fire past the deadline");
        }
    }

    @Nested
    class Recovery {

        @Test
        void transientFailureIsRetriedAndSucceeds() {
            client.enqueueFailure("rate limited");
            client.enqueueText("ok after retry");

            LoopPolicy policy = LoopPolicy.builder().retryBaseDelay(Duration.ofMillis(1)).build();
            AgentLoopResult result = loop(policy).run(stateWithUser("retry me"));

            assertEquals(StopReason.END_TURN, result.getStopReason());
            assertEquals("ok after retry", result.getText());
            assertEquals(0, result.getState().getConsecutiveFailures());
        }

        @Test
        void circuitBreakerOpensAfterConsecutiveFailures() {
            client.enqueueFailure("boom 1");
            client.enqueueFailure("boom 2");
            client.enqueueFailure("boom 3");
            client.enqueueText("never reached");

            LoopPolicy policy = LoopPolicy.builder().retryBaseDelay(Duration.ofMillis(1)).build();
            AgentLoopResult result = loop(policy).run(stateWithUser("break me"));

            assertEquals(StopReason.CIRCUIT_BREAKER, result.getStopReason());
            assertNotNull(result.getError());
            assertEquals(1, client.remainingSteps(), "loop must stop after 3 consecutive failures");
        }

        @Test
        void contextOverflowTriggersReactiveCompactionAndRetries() {
            ContextManager compacting = new ContextManager() {
                @Override
                public void manageBeforeTurn(LoopState state) {
                }

                @Override
                public boolean reactiveCompact(LoopState state) {
                    state.getMessages().removeIf(m -> m.getRole() == Role.user
                            && m.getContent() != null && m.getContent().startsWith("PADDING"));
                    return true;
                }
            };

            client.enqueueFailure("400: maximum context length exceeded");
            client.enqueueText("compacted and fine");

            AgentLoop loop = AgentLoop.builder()
                    .modelClient(client)
                    .toolExecutor(new ToolCallExecutor(registry, 4))
                    .policy(LoopPolicy.builder().retryBaseDelay(Duration.ofMillis(1)).build())
                    .contextManager(compacting)
                    .requestConfig(AgentLoop.RequestConfig.builder().model("mock-model").build())
                    .build();

            LoopState state = stateWithUser("real question");
            state.getMessages().add(1, ModelMessage.user("PADDING ".repeat(100)));

            AgentLoopResult result = loop.run(state);

            assertEquals(StopReason.END_TURN, result.getStopReason());
            assertEquals("compacted and fine", result.getText());
            assertTrue(result.getState().isAttemptedReactiveCompact());
        }
    }

    @Nested
    class Hooks {

        @Test
        void denyIsFedBackAsToolResultNotException() {
            AgentHook denyWrites = new AgentHook() {
                @Override
                public HookDecision preToolUse(ToolCall call, LoopState state) {
                    if ("writeRecord".equals(call.getFunction().getName())) {
                        return HookDecision.deny("writes are forbidden in this mode");
                    }
                    return HookDecision.allow();
                }
            };

            client.enqueueToolCalls(null,
                    MockModelClient.toolCall("call_1", "writeRecord", Map.of("key", "k", "value", "v")));
            client.enqueueText("understood, not writing");

            AgentLoopResult result = loop(LoopPolicy.defaults(), denyWrites).run(stateWithUser("write it"));

            assertEquals(StopReason.END_TURN, result.getStopReason());
            assertTrue(tools.invocations.stream().noneMatch(i -> i.startsWith("writeRecord")),
                    "denied tool must not execute");

            ModelMessage toolMessage = client.lastRequestMessagesAsModelMessages().stream()
                    .filter(m -> m.getRole() == Role.tool).findFirst().orElseThrow();
            assertTrue(toolMessage.getContent().contains("denied"));
            assertTrue(toolMessage.getContent().contains("writes are forbidden"));
        }

        @Test
        void hookCanRewriteArguments() {
            AgentHook maskCity = new AgentHook() {
                @Override
                public HookDecision preToolUse(ToolCall call, LoopState state) {
                    return HookDecision.allow(Map.of("city",
                            com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.textNode("MASKED")));
                }
            };

            client.enqueueToolCalls(null,
                    MockModelClient.toolCall("call_1", "getWeather", Map.of("city", "SecretCity")));
            client.enqueueText("done");

            loop(LoopPolicy.defaults(), maskCity).run(stateWithUser("weather"));

            assertTrue(tools.invocations.contains("getWeather:MASKED"),
                    "hook-updated arguments must reach the tool, got " + tools.invocations);
        }
    }

    @Nested
    class HumanInTheLoop {

        private final AgentHook askForDestructive = new AgentHook() {
            @Override
            public HookDecision preToolUse(ToolCall call, LoopState state) {
                if ("writeRecord".equals(call.getFunction().getName())) {
                    return HookDecision.ask("destructive operation requires approval");
                }
                return HookDecision.allow();
            }
        };

        @Test
        void askSuspendsWithZeroSideEffectsAndApproveResumes() throws Exception {
            client.enqueueToolCalls("about to write",
                    MockModelClient.toolCall("call_1", "writeRecord", Map.of("key", "k", "value", "v")));
            client.enqueueText("record written, all done");

            AgentLoop loop = loop(LoopPolicy.defaults(), askForDestructive);
            AgentLoopResult suspended = loop.run(stateWithUser("write the record"));

            assertEquals(StopReason.PENDING_APPROVAL, suspended.getStopReason());
            assertTrue(suspended.isSuspended());
            assertEquals(1, suspended.getPendingApprovals().size());
            assertEquals("writeRecord", suspended.getPendingApprovals().get(0).getFunction().getName());
            assertEquals("destructive operation requires approval", suspended.getApprovalReason());
            assertTrue(tools.invocations.isEmpty(), "no tool may execute before approval");

            // Serialization round-trip — the HITL invariant (plan, section 7)
            String json = JsonUtils.toJson(suspended.getState());
            LoopState restored;
            try {
                restored = JsonUtils.fromJson(json, LoopState.class);
            } catch (Exception e) {
                throw new AssertionError("LoopState must survive JSON round-trip", e);
            }
            assertTrue(restored.isSuspended());

            AgentLoopResult resumed = loop.resume(restored, ApprovalDecision.approve());

            assertEquals(StopReason.END_TURN, resumed.getStopReason());
            assertEquals("record written, all done", resumed.getText());
            assertTrue(tools.invocations.contains("writeRecord:k"), "approved tool must execute");
        }

        @Test
        void rejectFeedsRefusalBackToModel() {
            client.enqueueToolCalls(null,
                    MockModelClient.toolCall("call_1", "writeRecord", Map.of("key", "k", "value", "v")));
            client.enqueueText("ok, will not write");

            AgentLoop loop = loop(LoopPolicy.defaults(), askForDestructive);
            AgentLoopResult suspended = loop.run(stateWithUser("write the record"));
            AgentLoopResult resumed = loop.resume(suspended.getState(),
                    ApprovalDecision.reject("operator said no"));

            assertEquals(StopReason.END_TURN, resumed.getStopReason());
            assertTrue(tools.invocations.isEmpty(), "rejected tool must not execute");

            ModelMessage toolMessage = client.lastRequestMessagesAsModelMessages().stream()
                    .filter(m -> m.getRole() == Role.tool).findFirst().orElseThrow();
            assertTrue(toolMessage.getContent().contains("operator said no"));
        }

        @Test
        void suspendAdapterRoundTripsThroughStepResultMetadata() {
            client.enqueueToolCalls(null,
                    MockModelClient.toolCall("call_1", "writeRecord", Map.of("key", "k", "value", "v")));

            AgentLoop loop = loop(LoopPolicy.defaults(), askForDestructive);
            AgentLoopResult suspended = loop.run(stateWithUser("write the record"));

            var stepSuspend = AgentLoopSuspensions.toSuspend(suspended);
            assertEquals(ApprovalDecision.class, stepSuspend.nextInputClass());
            assertEquals("writeRecord", stepSuspend.promptToUser().pendingCalls().get(0).toolName());

            LoopState restored = AgentLoopSuspensions.restoreState(stepSuspend.metadata());
            assertTrue(restored.isSuspended());
            assertEquals(1, restored.getPendingToolCalls().size());
        }
    }
}

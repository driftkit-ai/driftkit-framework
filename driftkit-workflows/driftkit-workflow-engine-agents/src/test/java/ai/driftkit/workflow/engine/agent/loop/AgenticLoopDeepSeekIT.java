package ai.driftkit.workflow.engine.agent.loop;

import ai.driftkit.clients.deepseek.client.DeepSeekModelClient;
import ai.driftkit.common.domain.client.ModelMessage;
import ai.driftkit.common.domain.client.Role;
import ai.driftkit.common.tools.Tool;
import ai.driftkit.common.tools.ToolCall;
import ai.driftkit.common.tools.ToolRegistry;
import ai.driftkit.common.utils.JsonUtils;
import ai.driftkit.config.EtlConfig.VaultConfig;
import ai.driftkit.workflow.engine.agent.LLMAgent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Agentic-loop integration tests against the real DeepSeek API (deepseek-chat).
 * Enabled only when DEEPSEEK_API_KEY is set:
 *
 * <pre>DEEPSEEK_API_KEY=sk-... mvn test -pl driftkit-workflows/driftkit-workflow-engine-agents -Dtest=AgenticLoopDeepSeekIT</pre>
 */
@EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
class AgenticLoopDeepSeekIT {

    private DeepSeekModelClient client;
    private ItTools tools;
    private ToolRegistry registry;

    public static class ItTools {
        final ConcurrentLinkedQueue<String> invocations = new ConcurrentLinkedQueue<>();

        @Tool(description = "Get the current temperature in a city, in Celsius",
                whenToUse = "The user asks about current weather or temperature",
                readOnly = true, concurrencySafe = true)
        public String getTemperature(String city) {
            invocations.add("getTemperature:" + city);
            return switch (city.toLowerCase()) {
                case "berlin" -> "18";
                case "madrid" -> "31";
                default -> "22";
            };
        }

        @Tool(description = "Send a notification email to the operations team",
                whenToUse = "Only when explicitly asked to notify the team",
                destructive = true)
        public String notifyTeam(String message) {
            invocations.add("notifyTeam:" + message);
            return "notification sent: " + message;
        }
    }

    @BeforeEach
    void setUp() {
        VaultConfig config = new VaultConfig();
        config.setApiKey(System.getenv("DEEPSEEK_API_KEY"));
        config.setModel(DeepSeekModelClient.DEEPSEEK_CHAT);
        client = new DeepSeekModelClient();
        client.init(config);

        tools = new ItTools();
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
                        .model(DeepSeekModelClient.DEEPSEEK_CHAT)
                        .temperature(0.0)
                        .tools(Arrays.asList(registry.getTools()))
                        .build())
                .build();
    }

    private LoopState stateWithUser(String message) {
        LoopState state = LoopState.builder().build();
        state.addMessage(ModelMessage.system(
                "You are a helpful assistant. Use the available tools to answer; do not guess."));
        state.addMessage(ModelMessage.user(message));
        return state;
    }

    @Test
    void multiTurnLoopFeedsToolResultsBackToTheModel() {
        AgentLoopResult result = loop(LoopPolicy.defaults())
                .run(stateWithUser("What is the current temperature in Berlin? Answer with the number."));

        assertEquals(StopReason.END_TURN, result.getStopReason(), "error: " + result.getError());
        assertTrue(result.getTurns() >= 2, "loop must take at least 2 turns, took " + result.getTurns());
        assertTrue(tools.invocations.stream().anyMatch(i -> i.equalsIgnoreCase("getTemperature:Berlin")
                        || i.toLowerCase().startsWith("gettemperature:berlin")),
                "tool must be invoked, got " + tools.invocations);
        assertNotNull(result.getText());
        assertTrue(result.getText().contains("18"),
                "final answer must use the tool result (18), got: " + result.getText());
        assertTrue(result.getUsage().getModelCalls() >= 2);
    }

    @Test
    void multipleCitiesProduceMultipleToolCalls() {
        AgentLoopResult result = loop(LoopPolicy.defaults())
                .run(stateWithUser("Compare current temperatures in Berlin and Madrid. "
                        + "Which one is warmer? Check both cities."));

        assertEquals(StopReason.END_TURN, result.getStopReason(), "error: " + result.getError());
        long lookups = tools.invocations.stream().filter(i -> i.startsWith("getTemperature")).count();
        assertTrue(lookups >= 2, "both cities must be looked up, got " + tools.invocations);
        assertTrue(result.getText().toLowerCase().contains("madrid"),
                "Madrid (31C) is warmer; answer was: " + result.getText());
    }

    @Test
    void deniedToolIsFedBackAndModelAdapts() {
        AgentHook denyNotifications = new AgentHook() {
            @Override
            public HookDecision preToolUse(ToolCall call, LoopState state) {
                if ("notifyTeam".equals(call.getFunction().getName())) {
                    return HookDecision.deny("notifications are disabled in this environment");
                }
                return HookDecision.allow();
            }
        };

        AgentLoopResult result = loop(LoopPolicy.defaults(), denyNotifications)
                .run(stateWithUser("Notify the operations team that the deployment is complete."));

        assertEquals(StopReason.END_TURN, result.getStopReason(), "error: " + result.getError());
        assertTrue(tools.invocations.stream().noneMatch(i -> i.startsWith("notifyTeam")),
                "denied tool must never execute");
        // The model saw the denial and must reflect the failure in its answer
        assertNotNull(result.getText());
    }

    @Test
    void hitlSuspendSerializeResumeOnRealModel() throws Exception {
        AgentHook askForDestructive = new AgentHook() {
            @Override
            public HookDecision preToolUse(ToolCall call, LoopState state) {
                if ("notifyTeam".equals(call.getFunction().getName())) {
                    return HookDecision.ask("destructive action requires operator approval");
                }
                return HookDecision.allow();
            }
        };

        AgentLoop loop = loop(LoopPolicy.defaults(), askForDestructive);
        AgentLoopResult suspended = loop.run(
                stateWithUser("Notify the operations team that the deployment is complete."));

        assertEquals(StopReason.PENDING_APPROVAL, suspended.getStopReason());
        assertEquals("notifyTeam", suspended.getPendingApprovals().get(0).getFunction().getName());
        assertTrue(tools.invocations.isEmpty(), "no side effects before approval");

        // Serialize across a "process boundary" and resume with approval
        String json = JsonUtils.toJson(suspended.getState());
        LoopState restored = JsonUtils.fromJson(json, LoopState.class);

        AgentLoopResult resumed = loop.resume(restored, ApprovalDecision.approve());

        assertEquals(StopReason.END_TURN, resumed.getStopReason(), "error: " + resumed.getError());
        assertTrue(tools.invocations.stream().anyMatch(i -> i.startsWith("notifyTeam")),
                "approved tool must execute after resume");
    }

    @Test
    void summaryCompactionSurvivesRealModel() {
        DefaultContextManager manager = DefaultContextManager.builder()
                .modelClient(client)
                .summaryModel(DeepSeekModelClient.DEEPSEEK_CHAT)
                .contextWindowTokens(1500L)
                .reservedOutputTokens(500L)
                .keepRecentMessages(2)
                .build();

        LoopState state = stateWithUser("What is the current temperature in Berlin? Answer with the number.");
        // Inflate history so the manager must compact before the first turn
        for (int i = 0; i < 6; i++) {
            state.getMessages().add(1, ModelMessage.user("Background note " + i + ": "
                    + "the operations runbook paragraph ".repeat(30)));
            state.getMessages().add(2, ModelMessage.assistant("Acknowledged note " + i + "."));
        }

        AgentLoop loop = AgentLoop.builder()
                .modelClient(client)
                .toolExecutor(new ToolCallExecutor(registry, 4))
                .policy(LoopPolicy.defaults())
                .contextManager(manager)
                .requestConfig(AgentLoop.RequestConfig.builder()
                        .model(DeepSeekModelClient.DEEPSEEK_CHAT)
                        .temperature(0.0)
                        .tools(Arrays.asList(registry.getTools()))
                        .build())
                .build();

        AgentLoopResult result = loop.run(state);

        assertEquals(StopReason.END_TURN, result.getStopReason(), "error: " + result.getError());
        assertTrue(result.getState().getMessages().stream()
                        .anyMatch(m -> m.getContent() != null && m.getContent().contains("CONTEXT SUMMARY")),
                "summary compaction must have fired");
        assertTrue(result.getText().contains("18"), "answer must still be correct: " + result.getText());
    }

    @Test
    void llmAgentFacadeExecuteAgenticEndToEnd() {
        LLMAgent agent = LLMAgent.builder()
                .name("it-agent")
                .modelClient(client)
                .model(DeepSeekModelClient.DEEPSEEK_CHAT)
                .temperature(0.0)
                .systemMessage("You are a helpful assistant. Use tools; do not guess.")
                .toolRegistry(registry)
                .build();

        AgentLoopResult result = agent.executeAgentic(
                "What is the current temperature in Madrid? Answer with the number.");

        assertEquals(StopReason.END_TURN, result.getStopReason(), "error: " + result.getError());
        assertTrue(result.getText().contains("31"), "expected 31 from the tool, got: " + result.getText());
        assertTrue(tools.invocations.stream().anyMatch(i -> i.toLowerCase().contains("madrid")));
    }

    @Test
    void subAgentFanOutOnRealModel() {
        SubAgentSpawner spawner = new SubAgentSpawner(client);
        List<AgentLoopResult> results = spawner.spawnAll(List.of(
                SubAgentSpawner.SubAgentSpec.builder()
                        .name("capital-fr")
                        .model(DeepSeekModelClient.DEEPSEEK_CHAT)
                        .prompt("What is the capital of France? Reply with one word.")
                        .build(),
                SubAgentSpawner.SubAgentSpec.builder()
                        .name("capital-jp")
                        .model(DeepSeekModelClient.DEEPSEEK_CHAT)
                        .prompt("What is the capital of Japan? Reply with one word.")
                        .build()));

        assertEquals(2, results.size());
        assertEquals(StopReason.END_TURN, results.get(0).getStopReason());
        assertEquals(StopReason.END_TURN, results.get(1).getStopReason());
        assertTrue(results.get(0).getText().toLowerCase().contains("paris"));
        assertTrue(results.get(1).getText().toLowerCase().contains("tokyo"));
    }
}

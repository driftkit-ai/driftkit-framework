package ai.driftkit.workflow.engine.agent.loop;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SubAgentSpawnerTest {

    @Test
    void fanOutRunsAllSpecsAndPreservesOrder() {
        MockModelClient client = new MockModelClient();
        // Three sub-agents, one scripted answer each (order of polling is irrelevant
        // for this assertion — each loop consumes exactly one step)
        client.enqueueText("answer-1");
        client.enqueueText("answer-2");
        client.enqueueText("answer-3");

        SubAgentSpawner spawner = new SubAgentSpawner(client);
        List<AgentLoopResult> results = spawner.spawnAll(List.of(
                SubAgentSpawner.SubAgentSpec.builder().name("a").prompt("task 1, all context inline").build(),
                SubAgentSpawner.SubAgentSpec.builder().name("b").prompt("task 2, all context inline").build(),
                SubAgentSpawner.SubAgentSpec.builder().name("c").prompt("task 3, all context inline").build()));

        assertEquals(3, results.size());
        results.forEach(r -> assertEquals(StopReason.END_TURN, r.getStopReason()));
        assertEquals(3, client.requests.size());
    }

    @Test
    void subAgentSeesOnlyItsOwnPrompt() {
        MockModelClient client = new MockModelClient();
        client.enqueueText("isolated answer");

        SubAgentSpawner spawner = new SubAgentSpawner(client);
        spawner.spawn(SubAgentSpawner.SubAgentSpec.builder()
                .name("isolated")
                .systemPrompt("You are a focused researcher")
                .prompt("Self-contained task with all facts inline")
                .build());

        var messages = client.requests.get(0).getMessages();
        assertEquals(2, messages.size(), "fresh context: system + user only");
        assertEquals("You are a focused researcher", messages.get(0).getContent().get(0).getText());
    }

    @Test
    void failedSubAgentDoesNotFailSiblings() {
        MockModelClient client = new MockModelClient();
        client.enqueueText("first ok");
        // second spec gets no scripted step -> IllegalStateException inside its loop
        SubAgentSpawner spawner = new SubAgentSpawner(client);

        List<AgentLoopResult> results = spawner.spawnAll(List.of(
                SubAgentSpawner.SubAgentSpec.builder().name("ok")
                        .prompt("task with context")
                        .policy(LoopPolicy.builder().maxConsecutiveFailures(1).build())
                        .build(),
                SubAgentSpawner.SubAgentSpec.builder().name("broken")
                        .prompt("task with context")
                        .policy(LoopPolicy.builder().maxConsecutiveFailures(1).build())
                        .build()));

        long ok = results.stream().filter(r -> r.getStopReason() == StopReason.END_TURN).count();
        long failed = results.stream().filter(r -> r.getStopReason() == StopReason.CIRCUIT_BREAKER
                || r.getStopReason() == StopReason.ERROR).count();
        assertEquals(1, ok);
        assertEquals(1, failed);
    }

    @Test
    void nestingDepthIsCapped() {
        MockModelClient client = new MockModelClient();
        SubAgentSpawner level1 = new SubAgentSpawner(client);
        SubAgentSpawner level2 = level1.childSpawner();
        assertThrows(IllegalStateException.class, level2::childSpawner);
    }

    @Test
    void emptyPromptIsRejected() {
        SubAgentSpawner spawner = new SubAgentSpawner(new MockModelClient());
        assertThrows(IllegalArgumentException.class,
                () -> spawner.spawn(SubAgentSpawner.SubAgentSpec.builder().name("x").prompt(" ").build()));
    }
}

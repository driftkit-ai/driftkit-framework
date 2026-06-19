package ai.driftkit.workflow.engine.agent.loop;

import ai.driftkit.common.domain.client.ModelClient;
import ai.driftkit.common.domain.client.ModelMessage;
import ai.driftkit.common.tools.ToolRegistry;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fan-out / fan-in of isolated sub-agents (plan, D8).
 *
 * <p>Isolation contract:</p>
 * <ul>
 *   <li>Every sub-agent starts with a <b>fresh context</b> — it never sees the
 *       parent's conversation. Per the "Never delegate understanding" rule the
 *       spec prompt must be self-contained: include all facts, identifiers and
 *       constraints the sub-agent needs; synthesis stays with the caller.</li>
 *   <li>The tool palette is the narrowed registry of the spec — sub-agents never
 *       inherit the parent's full palette implicitly, and the spawner itself is
 *       not exposed as a tool (anti-recursion is structural; nesting depth is
 *       additionally capped at {@link #MAX_DEPTH}).</li>
 * </ul>
 */
@Slf4j
public class SubAgentSpawner {

    /** Max nesting of spawners (parent agent = depth 0). */
    public static final int MAX_DEPTH = 2;

    private final ModelClient modelClient;
    private final int depth;
    private final AgentLoop.TurnListener turnListener;

    public SubAgentSpawner(ModelClient modelClient) {
        this(modelClient, 1, null);
    }

    public SubAgentSpawner(ModelClient modelClient, AgentLoop.TurnListener turnListener) {
        this(modelClient, 1, turnListener);
    }

    private SubAgentSpawner(ModelClient modelClient, int depth, AgentLoop.TurnListener turnListener) {
        if (modelClient == null) {
            throw new IllegalArgumentException("modelClient is required");
        }
        if (depth > MAX_DEPTH) {
            throw new IllegalStateException("Sub-agent nesting deeper than " + MAX_DEPTH + " is not allowed");
        }
        this.modelClient = modelClient;
        this.depth = depth;
        this.turnListener = turnListener;
    }

    /**
     * Spawner for the next nesting level; throws beyond {@link #MAX_DEPTH}.
     */
    public SubAgentSpawner childSpawner() {
        return new SubAgentSpawner(modelClient, depth + 1, turnListener);
    }

    @Value
    @Builder
    public static class SubAgentSpec {
        /** Short name; used in logs and trace step ids ("subagent-{name}"). */
        String name;
        /** System prompt of the sub-agent; null = none. */
        String systemPrompt;
        /** Self-contained task prompt — the sub-agent sees nothing else. */
        String prompt;
        /** Narrowed tool palette; null = no tools. */
        ToolRegistry tools;
        /** Model override; null = client default. */
        String model;
        /** Loop limits; null = defaults. */
        LoopPolicy policy;
        /** Hooks for the sub-agent's loop; null = none. */
        List<AgentHook> hooks;
        /** Context manager; null = NOOP. */
        ContextManager contextManager;
    }

    /**
     * Run one sub-agent synchronously.
     */
    public AgentLoopResult spawn(SubAgentSpec spec) {
        validate(spec);
        LoopPolicy policy = spec.getPolicy() != null ? spec.getPolicy() : LoopPolicy.defaults();
        ToolRegistry registry = spec.getTools() != null ? spec.getTools() : new ToolRegistry();

        ModelClient.Tool[] tools = registry.getTools();
        AgentLoop loop = AgentLoop.builder()
                .modelClient(modelClient)
                .toolExecutor(new ToolCallExecutor(registry, policy.getMaxToolConcurrency()))
                .hooks(spec.getHooks())
                .contextManager(spec.getContextManager())
                .policy(policy)
                .requestConfig(AgentLoop.RequestConfig.builder()
                        .model(spec.getModel())
                        .tools(tools.length > 0 ? Arrays.asList(tools) : null)
                        .build())
                .turnListener(turnListener)
                .build();

        LoopState state = LoopState.builder().build();
        if (spec.getSystemPrompt() != null && !spec.getSystemPrompt().isBlank()) {
            state.addMessage(ModelMessage.system(spec.getSystemPrompt()));
        }
        state.addMessage(ModelMessage.user(spec.getPrompt()));

        log.debug("Spawning sub-agent '{}' (depth {})", spec.getName(), depth);
        return loop.run(state);
    }

    /**
     * Run one sub-agent asynchronously on a virtual thread.
     */
    public CompletableFuture<AgentLoopResult> spawnAsync(SubAgentSpec spec) {
        validate(spec);
        return CompletableFuture.supplyAsync(() -> spawn(spec),
                command -> Thread.ofVirtual().name("subagent-" + spec.getName()).start(command));
    }

    /**
     * Fan-out: run all specs concurrently (virtual threads), fan-in: return
     * results in spec order. A failed sub-agent yields an ERROR result instead
     * of failing its siblings.
     */
    public List<AgentLoopResult> spawnAll(List<SubAgentSpec> specs) {
        specs.forEach(this::validate);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<AgentLoopResult>> futures = new ArrayList<>();
            for (SubAgentSpec spec : specs) {
                futures.add(CompletableFuture.supplyAsync(() -> spawn(spec), executor)
                        .exceptionally(e -> {
                            log.warn("Sub-agent '{}' failed", spec.getName(), e);
                            return AgentLoopResult.builder()
                                    .stopReason(StopReason.ERROR)
                                    .state(LoopState.builder().build())
                                    .toolResults(List.of())
                                    .error(e.getMessage())
                                    .build();
                        }));
            }
            return futures.stream().map(CompletableFuture::join).toList();
        }
    }

    private void validate(SubAgentSpec spec) {
        if (spec == null || spec.getPrompt() == null || spec.getPrompt().isBlank()) {
            throw new IllegalArgumentException(
                    "Sub-agent prompt must be self-contained and non-empty (never delegate understanding)");
        }
    }
}

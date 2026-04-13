package ai.driftkit.workflow.engine.agent;

import ai.driftkit.clients.deepseek.client.DeepSeekModelClient;
import ai.driftkit.common.domain.Language;
import ai.driftkit.common.domain.Prompt;
import ai.driftkit.common.domain.Prompt.State;
import ai.driftkit.common.domain.client.CacheUsage;
import ai.driftkit.common.domain.client.ModelTextResponse;
import ai.driftkit.context.core.service.InMemoryPromptService;
import ai.driftkit.context.core.service.PromptService;
import ai.driftkit.config.EtlConfig.VaultConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end integration test:
 * InMemoryPromptService → PromptService → LLMAgent → DeepSeek API → Tracing
 *
 * Creates prompts in-memory, executes them through LLMAgent with DeepSeek,
 * captures traces, verifies full pipeline including cache metrics.
 */
public class ContextEngineeringE2ETest {

    private DeepSeekModelClient deepseekClient;
    private PromptService promptService;
    private InMemoryPromptService inMemoryStore;
    private List<TraceRecord> capturedTraces;

    @BeforeEach
    void setUp() {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        assumeTrue(apiKey != null, "DEEPSEEK_API_KEY must be set");

        // 1. Init DeepSeek client
        VaultConfig config = new VaultConfig();
        config.setApiKey(apiKey);
        config.setModel(DeepSeekModelClient.DEEPSEEK_CHAT);
        deepseekClient = new DeepSeekModelClient();
        deepseekClient.init(config);

        // 2. Create InMemory prompt store and seed prompts
        inMemoryStore = new InMemoryPromptService();
        seedPrompts();

        // 3. Wrap with PromptService (null for dictionary service — not needed)
        promptService = new PromptService(inMemoryStore, null);

        // 4. Trace capture
        capturedTraces = new ArrayList<>();
    }

    private void seedPrompts() {
        inMemoryStore.savePrompt(Prompt.builder()
                .method("greeting")
                .message("Say hello to {{name}} in one sentence.")
                .systemMessage("You are friendly and brief.")
                .state(State.CURRENT)
                .language(Language.GENERAL)
                .temperature(0.0)
                .build());

        inMemoryStore.savePrompt(Prompt.builder()
                .method("translator")
                .message("Translate the following to {{language}}: {{text}}")
                .systemMessage("You are a professional translator. Output only the translation.")
                .state(State.CURRENT)
                .language(Language.GENERAL)
                .temperature(0.0)
                .build());

        inMemoryStore.savePrompt(Prompt.builder()
                .method("analyzer")
                .message("Analyze the sentiment of this text and reply with one word (positive/negative/neutral): {{text}}")
                .state(State.CURRENT)
                .language(Language.GENERAL)
                .temperature(0.0)
                .build());
    }

    /**
     * Simple tracing provider that captures traces in-memory for assertions.
     */
    private RequestTracingProvider createTracingProvider() {
        return new RequestTracingProvider() {
            @Override
            public void traceImageRequest(ai.driftkit.common.domain.client.ModelImageRequest r, ai.driftkit.common.domain.client.ModelImageResponse resp, RequestContext c) {}
            @Override
            public void traceImageToTextRequest(ai.driftkit.common.domain.client.ModelTextRequest r, ModelTextResponse resp, RequestContext c) {}

            @Override
            public void traceTextRequest(
                    ai.driftkit.common.domain.client.ModelTextRequest request,
                    ModelTextResponse response,
                    RequestContext context) {
                TraceRecord trace = new TraceRecord();
                trace.contextId = context.getContextId();
                trace.contextType = context.getContextType();
                trace.promptId = context.getPromptId();
                trace.workflowId = context.getWorkflowId();
                trace.workflowStep = context.getWorkflowStep();
                trace.model = response != null ? response.getModel() : null;
                trace.response = response != null ? response.getResponse() : null;
                if (response != null && response.getUsage() != null) {
                    trace.promptTokens = response.getUsage().getPromptTokens();
                    trace.completionTokens = response.getUsage().getCompletionTokens();
                    trace.cacheUsage = response.getUsage().getCacheUsage();
                }
                if (response != null && response.getTrace() != null) {
                    trace.latencyMs = response.getTrace().getExecutionTimeMs();
                    trace.estimatedCost = response.getTrace().getEstimatedCostUSD();
                }
                capturedTraces.add(trace);

                System.out.println("--- TRACE ---");
                System.out.println("  Agent: " + trace.contextId);
                System.out.println("  Type: " + trace.contextType);
                System.out.println("  Prompt: " + trace.promptId);
                System.out.println("  Pipeline: " + trace.workflowId + " / " + trace.workflowStep);
                System.out.println("  Model: " + trace.model);
                System.out.println("  Tokens: prompt=" + trace.promptTokens + " completion=" + trace.completionTokens);
                System.out.println("  Latency: " + trace.latencyMs + "ms");
                System.out.println("  Cost: $" + (trace.estimatedCost != null ? String.format("%.6f", trace.estimatedCost) : "N/A"));
                if (trace.cacheUsage != null) {
                    System.out.println("  Cache: hit=" + trace.cacheUsage.getCacheHitTokens()
                            + " miss=" + trace.cacheUsage.getCacheMissTokens()
                            + " ratio=" + String.format("%.2f", trace.cacheUsage.getHitRatio()));
                }
                System.out.println("  Response: " + (trace.response != null ? trace.response.substring(0, Math.min(100, trace.response.length())) : "null"));
                System.out.println("-------------");
            }
        };
    }

    // ===== TESTS =====

    @Test
    public void fullPipeline_promptService_agent_deepseek_tracing() {
        System.out.println("\n========== E2E: InMemory Prompts → LLMAgent → DeepSeek → Tracing ==========\n");

        RequestTracingProvider tracer = createTracingProvider();

        // --- Step 1: Verify prompts are in store ---
        System.out.println("=== Prompts in store ===");
        List<Prompt> allPrompts = inMemoryStore.getPrompts();
        assertEquals(3, allPrompts.size(), "Should have 3 prompts");
        for (Prompt p : allPrompts) {
            System.out.println("  " + p.getMethod() + " (v" + p.getVersion() + ", " + p.getState() + "): "
                    + p.getMessage().substring(0, Math.min(60, p.getMessage().length())) + "...");
        }
        System.out.println();

        // --- Step 2: Create agent with prompt service ---
        LLMAgent agent = LLMAgent.builder()
                .modelClient(deepseekClient)
                .promptService(promptService)
                .tracingProvider(tracer)
                .name("e2e-agent")
                .build();

        // --- Step 3: Execute "greeting" prompt ---
        System.out.println("=== Execute: greeting ===");
        AgentResponse<String> greeting = agent.executeWithPrompt(
                "greeting",
                Map.of("name", "Ivan")
        );
        assertNotNull(greeting);
        assertNotNull(greeting.getText());
        System.out.println("Result: " + greeting.getText());
        System.out.println();

        // --- Step 4: Execute "translator" prompt ---
        System.out.println("=== Execute: translator ===");
        AgentResponse<String> translation = agent.executeWithPrompt(
                "translator",
                Map.of("language", "Spanish", "text", "Hello, how are you?")
        );
        assertNotNull(translation);
        assertNotNull(translation.getText());
        System.out.println("Result: " + translation.getText());
        System.out.println();

        // --- Step 5: Execute "analyzer" prompt ---
        System.out.println("=== Execute: analyzer ===");
        AgentResponse<String> analysis = agent.executeWithPrompt(
                "analyzer",
                Map.of("text", "I love this product, it works perfectly!")
        );
        assertNotNull(analysis);
        assertNotNull(analysis.getText());
        assertTrue(analysis.getText().toLowerCase().contains("positive"),
                "Should be positive sentiment");
        System.out.println("Result: " + analysis.getText());
        System.out.println();

        // --- Step 6: Sequential agent pipeline ---
        System.out.println("=== Pipeline: greeting → translator (SequentialAgent) ===");
        LLMAgent greeter = LLMAgent.builder()
                .modelClient(deepseekClient)
                .promptService(promptService)
                .tracingProvider(tracer)
                .name("greeter")
                .systemMessage("You are friendly. Respond in one sentence.")
                .build();

        LLMAgent translator = LLMAgent.builder()
                .modelClient(deepseekClient)
                .tracingProvider(tracer)
                .name("translator-agent")
                .systemMessage("Translate the following text to Spanish. Output only the translation.")
                .build();

        SequentialAgent pipeline = SequentialAgent.builder()
                .name("greet-and-translate")
                .customName(true)
                .agent(greeter)
                .agent(translator)
                .build();

        String pipelineResult = pipeline.execute("Say hello to Ivan");
        assertNotNull(pipelineResult);
        System.out.println("Pipeline result: " + pipelineResult);
        System.out.println();

        // --- Step 7: Verify traces ---
        System.out.println("=== Trace Summary ===");
        System.out.println("Total traces captured: " + capturedTraces.size());
        assertEquals(5, capturedTraces.size(), "Should have 5 traces (3 prompts + 2 pipeline steps)");

        // Verify prompt-based traces
        TraceRecord greetingTrace = capturedTraces.get(0);
        assertEquals("greeting", greetingTrace.promptId);
        assertNotNull(greetingTrace.promptTokens);
        assertTrue(greetingTrace.promptTokens > 0);

        TraceRecord translatorTrace = capturedTraces.get(1);
        assertEquals("translator", translatorTrace.promptId);

        TraceRecord analyzerTrace = capturedTraces.get(2);
        assertEquals("analyzer", analyzerTrace.promptId);

        // Verify pipeline traces have hierarchical context
        TraceRecord pipelineStep0 = capturedTraces.get(3);
        assertEquals("greet-and-translate", pipelineStep0.workflowId);
        assertEquals("step-0-greeter", pipelineStep0.workflowStep);

        TraceRecord pipelineStep1 = capturedTraces.get(4);
        assertEquals("greet-and-translate", pipelineStep1.workflowId);
        assertEquals("step-1-translator-agent", pipelineStep1.workflowStep);

        // Print total cost
        double totalCost = capturedTraces.stream()
                .mapToDouble(t -> t.estimatedCost != null ? t.estimatedCost : 0)
                .sum();
        int totalTokens = capturedTraces.stream()
                .mapToInt(t -> (t.promptTokens != null ? t.promptTokens : 0) + (t.completionTokens != null ? t.completionTokens : 0))
                .sum();
        System.out.println("Total tokens: " + totalTokens);
        System.out.println("Total estimated cost: $" + String.format("%.6f", totalCost));
        System.out.println("\n========== E2E PASSED ==========\n");
    }

    // ===== Helper =====

    static class TraceRecord {
        String contextId;
        String contextType;
        String promptId;
        String workflowId;
        String workflowStep;
        String model;
        String response;
        Integer promptTokens;
        Integer completionTokens;
        CacheUsage cacheUsage;
        long latencyMs;
        Double estimatedCost;
    }
}

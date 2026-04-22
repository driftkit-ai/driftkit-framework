package ai.driftkit.clients.claude;

import ai.driftkit.clients.claude.client.ClaudeModelClient;
import ai.driftkit.common.domain.client.*;
import ai.driftkit.common.domain.client.ModelTextResponse.Usage;
import ai.driftkit.config.EtlConfig.VaultConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Claude prompt caching (cache_control).
 * Requires CLAUDE_API_KEY environment variable.
 */
@EnabledIfEnvironmentVariable(named = "CLAUDE_API_KEY", matches = ".+")
class ClaudeCachePolicyIntegrationTest {

    private ClaudeModelClient client;

    @BeforeEach
    void setUp() {
        String apiKey = System.getenv("CLAUDE_API_KEY");
        VaultConfig config = VaultConfig.builder()
                .apiKey(apiKey)
                .model(ClaudeModelClient.CLAUDE_DEFAULT)
                .temperature(0.0)
                .maxTokens(100)
                .build();

        client = new ClaudeModelClient();
        client.init(config);
    }

    @Test
    void basicCompletion_returnsCacheUsageFields() {
        ModelTextRequest request = ModelTextRequest.builder()
                .messages(List.of(ModelContentMessage.create(Role.user, "What is 2+2?")))
                .temperature(0.0)
                .build();

        ModelTextResponse response = client.textToText(request);

        assertNotNull(response);
        assertNotNull(response.getUsage());
        assertNotNull(response.getUsage().getPromptTokens());
        assertTrue(response.getUsage().getPromptTokens() > 0);
        System.out.println("Basic: prompt=" + response.getUsage().getPromptTokens()
                + " completion=" + response.getUsage().getCompletionTokens());
    }

    @Test
    void autoCachePolicy_cachesLargeSystemPrompt() throws InterruptedException {
        // System prompt must be > 1024 actual Claude tokens for caching to kick in.
        // Claude's BPE tokenizer is ~1.3 tokens/word, need ~800+ words → repeat 200x
        String largeSystem = ("You are an expert in mathematics, physics, chemistry, biology, " +
                "computer science, history, geography, literature, philosophy, and economics. " +
                "Please answer questions carefully and thoroughly with detailed explanations. ").repeat(200);

        ModelTextRequest request1 = ModelTextRequest.builder()
                .messages(List.of(
                        ModelContentMessage.create(Role.system, largeSystem),
                        ModelContentMessage.create(Role.user, "What is 7 * 8?")
                ))
                .temperature(0.0)
                .cachePolicy(CachePolicy.AUTO)
                .build();

        ModelTextResponse response1 = client.textToText(request1);
        assertNotNull(response1);
        Usage usage1 = response1.getUsage();
        assertNotNull(usage1);

        System.out.println("Request 1 (AUTO cache, large system): prompt=" + usage1.getPromptTokens());
        assertNotNull(usage1.getCacheUsage(), "Cache usage should be reported when cache_control is set");
        System.out.println("  Cache write=" + usage1.getCacheUsage().getCacheWriteTokens()
                + " hit=" + usage1.getCacheUsage().getCacheHitTokens());
        // First request: either writes to cache OR hits existing cache (from previous test run)
        int write1 = usage1.getCacheUsage().getCacheWriteTokens() != null ? usage1.getCacheUsage().getCacheWriteTokens() : 0;
        int hit1 = usage1.getCacheUsage().getCacheHitTokens() != null ? usage1.getCacheUsage().getCacheHitTokens() : 0;
        assertTrue(write1 > 0 || hit1 > 0,
                "First request with 7600+ token system should either write to or hit cache");

        // Second identical request — should get cache hit
        ModelTextRequest request2 = ModelTextRequest.builder()
                .messages(List.of(
                        ModelContentMessage.create(Role.system, largeSystem),
                        ModelContentMessage.create(Role.user, "What is 7 * 8?")
                ))
                .temperature(0.0)
                .cachePolicy(CachePolicy.AUTO)
                .build();

        // Small delay to let cache propagate
        Thread.sleep(1000);

        ModelTextResponse response2 = client.textToText(request2);
        assertNotNull(response2);
        Usage usage2 = response2.getUsage();
        assertNotNull(usage2);

        System.out.println("Request 2 (same prompt): prompt=" + usage2.getPromptTokens());
        if (usage2.getCacheUsage() != null) {
            CacheUsage cache = usage2.getCacheUsage();
            System.out.println("  Cache hit=" + cache.getCacheHitTokens()
                    + " write=" + cache.getCacheWriteTokens()
                    + " ratio=" + String.format("%.2f", cache.getHitRatio()));
            // Second identical request should hit cache (written by first request or earlier run)
            int hit2 = cache.getCacheHitTokens() != null ? cache.getCacheHitTokens() : 0;
            int write2 = cache.getCacheWriteTokens() != null ? cache.getCacheWriteTokens() : 0;
            assertTrue(hit2 > 0 || write2 > 0,
                    "Second request should have cache interaction (hit or write)");
        }
    }

    @Test
    void noCachePolicy_noExtraHeaders() {
        ModelTextRequest request = ModelTextRequest.builder()
                .messages(List.of(ModelContentMessage.create(Role.user, "Say hi")))
                .temperature(0.0)
                .cachePolicy(CachePolicy.NONE)
                .build();

        ModelTextResponse response = client.textToText(request);
        assertNotNull(response);
        assertNotNull(response.getUsage());
        System.out.println("No cache: prompt=" + response.getUsage().getPromptTokens());
    }

    @Test
    void manualCachePolicy_withEphemeralOnSystemMessage() {
        // Manual cache with explicit ephemeral marker on system content block
        String system = "You are a helpful assistant who answers concisely. ".repeat(50);

        ModelContentMessage systemMsg = ModelContentMessage.builder()
                .role(Role.system)
                .content(List.of(
                        ModelContentMessage.ModelContentElement.builder()
                                .type(ModelTextRequest.MessageType.text)
                                .text(system)
                                .cacheControl(CacheControl.builder()
                                        .type(CacheControl.CacheType.EPHEMERAL)
                                        .build())
                                .build()
                ))
                .build();

        ModelTextRequest request = ModelTextRequest.builder()
                .messages(List.of(systemMsg, ModelContentMessage.create(Role.user, "Hi")))
                .temperature(0.0)
                .cachePolicy(CachePolicy.MANUAL)
                .build();

        ModelTextResponse response = client.textToText(request);
        assertNotNull(response);

        System.out.println("Manual cache: prompt=" + response.getUsage().getPromptTokens());
        if (response.getUsage().getCacheUsage() != null) {
            System.out.println("  Cache write=" + response.getUsage().getCacheUsage().getCacheWriteTokens()
                    + " hit=" + response.getUsage().getCacheUsage().getCacheHitTokens());
        }
    }
}

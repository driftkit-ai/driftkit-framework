package ai.driftkit.clients.deepseek;

import ai.driftkit.clients.deepseek.client.DeepSeekModelClient;
import ai.driftkit.common.domain.client.*;
import ai.driftkit.common.domain.client.ModelTextRequest.MessageType;
import ai.driftkit.common.domain.client.ModelTextRequest.ReasoningEffort;
import ai.driftkit.common.domain.client.ModelTextResponse.Usage;
import ai.driftkit.common.domain.streaming.StreamingCallback;
import ai.driftkit.common.domain.streaming.StreamingResponse;
import ai.driftkit.config.EtlConfig.VaultConfig;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeNotNull;

/**
 * Integration tests for DeepSeek API client.
 * Requires DEEPSEEK_API_KEY environment variable.
 *
 * Tests cover:
 * - Basic chat completion (deepseek-chat)
 * - Thinking/reasoning mode (deepseek-reasoner)
 * - Prefix cache metrics
 * - Streaming
 * - ReasoningEffort mapping
 */
public class DeepSeekModelClientIntegrationTest {

    private static final String API_KEY_ENV = "DEEPSEEK_API_KEY";

    private String apiKey;
    private DeepSeekModelClient client;

    @Before
    public void setUp() {
        apiKey = System.getenv(API_KEY_ENV);
        assumeNotNull("DEEPSEEK_API_KEY env variable must be set", apiKey);

        VaultConfig config = new VaultConfig();
        config.setApiKey(apiKey);
        config.setModel(DeepSeekModelClient.DEEPSEEK_CHAT);

        client = new DeepSeekModelClient();
        client.init(config);
    }

    // ---- Basic Chat ----

    @Test
    public void testBasicChatCompletion() {
        ModelTextRequest request = buildTextRequest("What is 2+2? Answer with just the number.", null);

        ModelTextResponse response = client.textToText(request);

        assertNotNull("Response should not be null", response);
        assertNotNull("Choices should not be null", response.getChoices());
        assertFalse("Should have at least one choice", response.getChoices().isEmpty());

        String text = response.getResponse();
        assertNotNull("Response text should not be null", text);
        assertTrue("Response should contain '4'", text.contains("4"));

        // Verify usage
        Usage usage = response.getUsage();
        assertNotNull("Usage should not be null", usage);
        assertNotNull("Prompt tokens should be set", usage.getPromptTokens());
        assertNotNull("Completion tokens should be set", usage.getCompletionTokens());
        assertTrue("Prompt tokens should be > 0", usage.getPromptTokens() > 0);
        assertTrue("Completion tokens should be > 0", usage.getCompletionTokens() > 0);

        System.out.println("Basic chat response: " + text);
        System.out.println("Usage: prompt=" + usage.getPromptTokens()
                + " completion=" + usage.getCompletionTokens()
                + " total=" + usage.getTotalTokens());
    }

    // ---- Cache Metrics ----

    @Test
    public void testCacheMetricsPresent() {
        // Send the same request twice — second should have cache hits
        String longSystemPrompt = "You are an expert mathematician. " +
                "You always explain your reasoning step by step. " +
                "You format your answers clearly with examples. ".repeat(50);

        ModelTextRequest request1 = buildTextRequestWithSystem(
                longSystemPrompt,
                "What is 7 * 8?",
                null
        );

        ModelTextResponse response1 = client.textToText(request1);
        assertNotNull(response1);
        Usage usage1 = response1.getUsage();
        assertNotNull("Usage should not be null", usage1);

        System.out.println("Request 1 - prompt tokens: " + usage1.getPromptTokens());
        if (usage1.getCacheUsage() != null) {
            System.out.println("Request 1 - cache hit: " + usage1.getCacheUsage().getCacheHitTokens()
                    + " miss: " + usage1.getCacheUsage().getCacheMissTokens());
        } else {
            System.out.println("Request 1 - no cache usage reported (first request)");
        }

        // Second identical request — should trigger prefix cache hit
        ModelTextRequest request2 = buildTextRequestWithSystem(
                longSystemPrompt,
                "What is 7 * 8?",
                null
        );

        ModelTextResponse response2 = client.textToText(request2);
        assertNotNull(response2);
        Usage usage2 = response2.getUsage();
        assertNotNull("Usage should not be null", usage2);

        System.out.println("Request 2 - prompt tokens: " + usage2.getPromptTokens());
        if (usage2.getCacheUsage() != null) {
            CacheUsage cache = usage2.getCacheUsage();
            System.out.println("Request 2 - cache hit: " + cache.getCacheHitTokens()
                    + " miss: " + cache.getCacheMissTokens()
                    + " ratio: " + String.format("%.2f", cache.getHitRatio()));
        } else {
            System.out.println("Request 2 - no cache usage (caching may take a few seconds to warm up)");
        }
    }

    // ---- Thinking / Reasoning Mode ----

    @Test
    public void testReasoningWithDeepSeekReasoner() {
        ModelTextRequest request = buildTextRequest(
                "Which is greater: 9.11 or 9.8? Think carefully.",
                ReasoningEffort.high
        );
        request.setModel(DeepSeekModelClient.DEEPSEEK_REASONER);

        ModelTextResponse response = client.textToText(request);

        assertNotNull(response);
        assertFalse(response.getChoices().isEmpty());

        ModelMessage message = response.getChoices().getFirst().getMessage();
        assertNotNull("Content should not be null", message.getContent());
        assertNotNull("Reasoning content should be present", message.getReasoningContent());
        assertFalse("Reasoning content should not be empty", message.getReasoningContent().isEmpty());

        System.out.println("Reasoning: " + message.getReasoningContent().substring(0,
                Math.min(200, message.getReasoningContent().length())) + "...");
        System.out.println("Answer: " + message.getContent());

        // Verify reasoning tokens in usage
        Usage usage = response.getUsage();
        if (usage != null && usage.getReasoningTokens() != null) {
            System.out.println("Reasoning tokens: " + usage.getReasoningTokens());
            assertTrue("Reasoning tokens should be > 0", usage.getReasoningTokens() > 0);
        }
    }

    @Test
    public void testThinkingEnabledViaHighEffort() {
        // ReasoningEffort.high on deepseek-chat should enable thinking
        ModelTextRequest request = buildTextRequest(
                "What is 15 * 17? Show your work.",
                ReasoningEffort.high
        );

        ModelTextResponse response = client.textToText(request);

        assertNotNull(response);
        String text = response.getResponse();
        assertNotNull(text);
        assertTrue("Should contain 255", text.contains("255"));

        ModelMessage message = response.getChoices().getFirst().getMessage();
        // With thinking enabled, reasoning_content should be present
        if (message.getReasoningContent() != null) {
            System.out.println("Thinking was enabled, reasoning: "
                    + message.getReasoningContent().substring(0,
                    Math.min(100, message.getReasoningContent().length())) + "...");
        }
        System.out.println("Answer: " + text);
    }

    @Test
    public void testMediumEffortDoesNotEnableThinking() {
        // ReasoningEffort.medium on deepseek-chat should NOT enable thinking
        ModelTextRequest request = buildTextRequest("Say hello.", ReasoningEffort.medium);

        ModelTextResponse response = client.textToText(request);

        assertNotNull(response);
        ModelMessage message = response.getChoices().getFirst().getMessage();

        // reasoning_content should be null for non-thinking mode
        assertNull("Medium effort should not enable thinking for deepseek-chat",
                message.getReasoningContent());

        System.out.println("Response (no thinking): " + message.getContent());
    }

    // ---- Streaming ----

    @Test
    public void testStreaming() throws InterruptedException {
        ModelTextRequest request = buildTextRequest(
                "Count from 1 to 5, each number on a new line.",
                null
        );

        StreamingResponse<String> streaming = client.streamTextToText(request);
        assertNotNull(streaming);

        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder fullResponse = new StringBuilder();
        List<String> chunks = new ArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();

        streaming.subscribe(new StreamingCallback<>() {
            @Override
            public void onNext(String chunk) {
                chunks.add(chunk);
                fullResponse.append(chunk);
                System.out.print(chunk);
            }

            @Override
            public void onError(Throwable throwable) {
                System.err.println("\nStreaming error: " + throwable.getMessage());
                error.set(throwable);
                latch.countDown();
            }

            @Override
            public void onComplete() {
                System.out.println("\n=== Streaming completed, " + chunks.size() + " chunks ===");
                latch.countDown();
            }
        });

        boolean completed = latch.await(30, TimeUnit.SECONDS);

        assertTrue("Streaming should complete within 30s", completed);
        assertNull("No error should occur", error.get());
        assertFalse("Should receive chunks", chunks.isEmpty());

        String response = fullResponse.toString();
        assertTrue("Response should contain '1'", response.contains("1"));
        assertTrue("Response should contain '5'", response.contains("5"));
    }

    @Test
    public void testStreamingCancellation() throws InterruptedException {
        ModelTextRequest request = buildTextRequest(
                "Write a very long story about a space adventure.",
                null
        );

        StreamingResponse<String> streaming = client.streamTextToText(request);

        CountDownLatch started = new CountDownLatch(3);
        List<String> chunks = new ArrayList<>();

        streaming.subscribe(new StreamingCallback<>() {
            @Override
            public void onNext(String chunk) {
                chunks.add(chunk);
                started.countDown();
                if (chunks.size() >= 3) {
                    streaming.cancel();
                }
            }

            @Override
            public void onError(Throwable throwable) { }

            @Override
            public void onComplete() { }
        });

        boolean received = started.await(15, TimeUnit.SECONDS);
        assertTrue("Should receive at least 3 chunks", received);

        Thread.sleep(500);
        assertFalse("Stream should not be active after cancellation", streaming.isActive());
        System.out.println("Cancellation OK, received " + chunks.size() + " chunks before cancel");
    }

    // ---- JSON Mode ----

    @Test
    public void testJsonObjectMode() {
        ModelTextRequest request = buildTextRequest(
                "Return a JSON object with fields: name (string) and age (number). Use name='Alice' and age=30.",
                null
        );
        request.setResponseFormat(new ResponseFormat(ResponseFormat.ResponseType.JSON_OBJECT, null));

        ModelTextResponse response = client.textToText(request);

        assertNotNull(response);
        String text = response.getResponse();
        assertNotNull(text);
        assertTrue("Should be valid JSON with 'name'", text.contains("\"name\""));
        assertTrue("Should contain 'Alice'", text.contains("Alice"));
        assertTrue("Should contain age", text.contains("30"));

        System.out.println("JSON response: " + text);
    }

    // ---- Helpers ----

    private ModelTextRequest buildTextRequest(String userMessage, ReasoningEffort effort) {
        ModelTextRequest.ModelTextRequestBuilder builder = ModelTextRequest.builder()
                .model(DeepSeekModelClient.DEEPSEEK_CHAT)
                .temperature(0.0)
                .messages(Collections.singletonList(
                        ModelContentMessage.builder()
                                .role(Role.user)
                                .content(Collections.singletonList(
                                        ModelContentMessage.ModelContentElement.builder()
                                                .type(MessageType.text)
                                                .text(userMessage)
                                                .build()
                                ))
                                .build()
                ));

        if (effort != null) {
            builder.reasoningEffort(effort);
        }

        return builder.build();
    }

    private ModelTextRequest buildTextRequestWithSystem(String systemMessage, String userMessage,
                                                        ReasoningEffort effort) {
        List<ModelContentMessage> messages = List.of(
                ModelContentMessage.builder()
                        .role(Role.system)
                        .content(Collections.singletonList(
                                ModelContentMessage.ModelContentElement.builder()
                                        .type(MessageType.text)
                                        .text(systemMessage)
                                        .build()
                        ))
                        .build(),
                ModelContentMessage.builder()
                        .role(Role.user)
                        .content(Collections.singletonList(
                                ModelContentMessage.ModelContentElement.builder()
                                        .type(MessageType.text)
                                        .text(userMessage)
                                        .build()
                        ))
                        .build()
        );

        ModelTextRequest.ModelTextRequestBuilder builder = ModelTextRequest.builder()
                .model(DeepSeekModelClient.DEEPSEEK_CHAT)
                .temperature(0.0)
                .messages(messages);

        if (effort != null) {
            builder.reasoningEffort(effort);
        }

        return builder.build();
    }
}

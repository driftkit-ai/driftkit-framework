package ai.driftkit.workflow.engine.agent;

import ai.driftkit.clients.openai.client.OpenAIModelClient;
import ai.driftkit.common.domain.client.ModelClient;
import ai.driftkit.common.domain.streaming.StreamingCallback;
import ai.driftkit.config.EtlConfig.VaultConfig;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration test for LLMAgent streaming functionality.
 * Requires OPENAI_API_KEY environment variable to be set.
 */
public class LLMAgentStreamingIntegrationTest {

    @Test
    void testStreamingResponse() throws InterruptedException {
        String apiKey = System.getenv("OPENAI_API_KEY");
        assumeTrue(apiKey != null && !apiKey.isBlank(), "OPENAI_API_KEY is not set");

        VaultConfig config = new VaultConfig();
        config.setApiKey(apiKey);
        config.setModel("gpt-4o-mini");
        config.setBaseUrl("https://api.openai.com");

        ModelClient modelClient = new OpenAIModelClient().init(config);

        LLMAgent agent = LLMAgent.builder()
                .modelClient(modelClient)
                .systemMessage("You are a helpful assistant.")
                .maxTokens(200)
                .temperature(0.1)
                .build();

        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder result = new StringBuilder();
        AtomicReference<Throwable> error = new AtomicReference<>();

        CompletableFuture<String> streamingResponse = agent.executeStreaming(
                "Count from 1 to 5, with each number on a new line.", new StreamingCallback<String>() {
                    @Override
                    public void onNext(String item) {
                        result.append(item);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        error.set(throwable);
                        latch.countDown();
                    }

                    @Override
                    public void onComplete() {
                        latch.countDown();
                    }
                });


        boolean completed = latch.await(30, TimeUnit.SECONDS);
        assertTrue(completed, "Stream did not complete in time");
        assertNull(error.get(), () -> "Error during streaming: " + error.get());
        String output = result.toString();
        assertFalse(output.isBlank(), "Streaming response was empty");
        assertTrue(output.contains("1"));
        assertTrue(output.contains("5"));
    }
}

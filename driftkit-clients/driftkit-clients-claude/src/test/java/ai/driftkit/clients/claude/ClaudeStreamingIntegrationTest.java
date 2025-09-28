package ai.driftkit.clients.claude;

import ai.driftkit.clients.claude.client.ClaudeModelClient;
import ai.driftkit.common.domain.client.ModelClient;
import ai.driftkit.common.domain.client.ModelTextRequest;
import ai.driftkit.common.domain.client.ModelContentMessage;
import ai.driftkit.common.domain.client.Role;
import ai.driftkit.common.domain.streaming.StreamingCallback;
import ai.driftkit.common.domain.streaming.StreamingResponse;
import ai.driftkit.config.EtlConfig.VaultConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Assumptions;

public class ClaudeStreamingIntegrationTest {

    @Test
    public void testStreamingResponse() throws InterruptedException {
        String apiKey = System.getenv("CLAUDE_API_KEY");
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(), "CLAUDE_API_KEY is not set");

        VaultConfig config = new VaultConfig();
        config.setApiKey(apiKey);
        config.setModel("claude-3-haiku-20240307");
        config.setBaseUrl("https://api.anthropic.com");

        ModelClient modelClient = new ClaudeModelClient().init(config);

        ModelTextRequest request = ModelTextRequest.builder()
                .model("claude-3-haiku-20240307")
                .temperature(0.1)
                .messages(List.of(
                        ModelContentMessage.create(Role.user, "Count from 1 to 5, with each number on a new line.")
                ))
                .build();

        StreamingResponse<String> streamingResponse = modelClient.streamTextToText(request);

        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder result = new StringBuilder();
        AtomicReference<Throwable> error = new AtomicReference<>();

        streamingResponse.subscribe(new StreamingCallback<String>() {
            @Override
            public void onNext(String item) {
                System.out.print(item);
                result.append(item);
            }

            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
                latch.countDown();
            }

            @Override
            public void onComplete() {
                System.out.println("\n[Stream completed]");
                latch.countDown();
            }
        });

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        assertTrue(completed, "Stream did not complete in time");
        assertNull(error.get(), "Error during streaming: " + error.get());
        
        String output = result.toString();
        assertFalse(output.isBlank(), "Streaming response was empty");
        assertTrue(output.contains("1"), "Response should contain '1'");
        assertTrue(output.contains("5"), "Response should contain '5'");
    }

    @Test
    public void testStreamingCancellation() throws InterruptedException {
        String apiKey = System.getenv("CLAUDE_API_KEY");
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(), "CLAUDE_API_KEY is not set");

        VaultConfig config = new VaultConfig();
        config.setApiKey(apiKey);
        config.setModel("claude-3-haiku-20240307");
        config.setBaseUrl("https://api.anthropic.com");

        ModelClient modelClient = new ClaudeModelClient().init(config);

        ModelTextRequest request = ModelTextRequest.builder()
                .model("claude-3-haiku-20240307")
                .temperature(0.1)
                .messages(List.of(
                        ModelContentMessage.create(Role.user, "Count slowly from 1 to 20, with each number on a new line.")
                ))
                .build();

        StreamingResponse<String> streamingResponse = modelClient.streamTextToText(request);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger chunkCount = new AtomicInteger(0);

        streamingResponse.subscribe(new StreamingCallback<String>() {
            @Override
            public void onNext(String item) {
                int count = chunkCount.incrementAndGet();
                System.out.print(item);
                
                // Cancel after receiving a few chunks
                if (count >= 5) {
                    streamingResponse.cancel();
                }
            }

            @Override
            public void onError(Throwable throwable) {
                latch.countDown();
            }

            @Override
            public void onComplete() {
                latch.countDown();
            }
        });

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertTrue(completed, "Stream should have been cancelled quickly");
        assertFalse(streamingResponse.isActive(), "Stream should not be active after cancellation");
        assertTrue(chunkCount.get() >= 5, "Should have received some chunks before cancellation");
    }
}
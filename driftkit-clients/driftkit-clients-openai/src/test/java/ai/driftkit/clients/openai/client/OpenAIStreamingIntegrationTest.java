package ai.driftkit.clients.openai.client;

import ai.driftkit.common.domain.client.*;
import ai.driftkit.common.domain.streaming.StreamingCallback;
import ai.driftkit.common.domain.streaming.StreamingResponse;
import ai.driftkit.config.EtlConfig.VaultConfig;
import org.junit.Before;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * Integration test for OpenAI streaming API
 * Requires OPENAI_API_KEY environment variable to be set
 */
public class OpenAIStreamingIntegrationTest {

    private String token = System.getenv("OPENAI_API_KEY");
    
    private OpenAIModelClient client;
    
    @Before
    public void setUp() {
        // Use the token from the field
        VaultConfig config = new VaultConfig();
        config.setApiKey(token);
        config.setModel("gpt-4o-mini");
        config.setBaseUrl("https://api.openai.com");
        
        client = new OpenAIModelClient();
        client.init(config);
    }
    
//    @Test
    public void testRealStreamingAPI() throws InterruptedException {
        System.out.println("Testing OpenAI Streaming API with real API key...");
        
        ModelTextRequest request = ModelTextRequest.builder()
                .model("gpt-4o-mini")
                .temperature(0.7)
                .messages(Arrays.asList(
                    ModelContentMessage.builder()
                        .role(Role.system)
                        .content(Collections.singletonList(
                            ModelContentMessage.ModelContentElement.builder()
                                .type(ModelTextRequest.MessageType.text)
                                .text("You are a helpful assistant.")
                                .build()
                        ))
                        .build(),
                    ModelContentMessage.builder()
                        .role(Role.user)
                        .content(Collections.singletonList(
                            ModelContentMessage.ModelContentElement.builder()
                                .type(ModelTextRequest.MessageType.text)
                                .text("Count from 1 to 10 slowly, with each number on a new line")
                                .build()
                        ))
                        .build()
                ))
                .build();
        
        StreamingResponse<String> streamingResponse = client.streamTextToText(request);
        assertNotNull(streamingResponse);
        
        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder fullResponse = new StringBuilder();
        List<String> chunks = new ArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        long startTime = System.currentTimeMillis();
        
        streamingResponse.subscribe(new StreamingCallback<String>() {
            @Override
            public void onNext(String chunk) {
                chunks.add(chunk);
                fullResponse.append(chunk);
                System.out.print(chunk); // Print chunks as they arrive
                System.out.flush();
            }
            
            @Override
            public void onError(Throwable throwable) {
                System.err.println("\nError occurred: " + throwable.getMessage());
                throwable.printStackTrace();
                error.set(throwable);
                latch.countDown();
            }
            
            @Override
            public void onComplete() {
                long duration = System.currentTimeMillis() - startTime;
                System.out.println("\n\n=== Streaming completed in " + duration + "ms ===");
                System.out.println("Total chunks received: " + chunks.size());
                latch.countDown();
            }
        });
        
        // Wait for streaming to complete
        boolean completed = latch.await(30, TimeUnit.SECONDS);
        
        // Assertions
        assertTrue("Streaming should complete within 30 seconds", completed);
        assertNull("No error should occur during streaming", error.get());
        assertFalse("Should receive at least one chunk", chunks.isEmpty());
        
        String response = fullResponse.toString();
        assertFalse("Response should not be empty", response.isEmpty());
        
        // Check that numbers are present
        assertTrue("Response should contain '1'", response.contains("1"));
        assertTrue("Response should contain '10'", response.contains("10"));
        
        System.out.println("\nFull response length: " + response.length() + " characters");
        System.out.println("Chunks received: " + chunks.size());
    }
    
//    @Test
    public void testStreamingWithShortResponse() throws InterruptedException {
        System.out.println("\nTesting streaming with short response...");
        
        // Ensure client is initialized
        if (client == null) {
            setUp();
        }
        
        ModelTextRequest request = ModelTextRequest.builder()
                .model("gpt-4o-mini")
                .temperature(0.0)
                .messages(Collections.singletonList(
                    ModelContentMessage.builder()
                        .role(Role.user)
                        .content(Collections.singletonList(
                            ModelContentMessage.ModelContentElement.builder()
                                .type(ModelTextRequest.MessageType.text)
                                .text("What is 2+2? Answer with just the number.")
                                .build()
                        ))
                        .build()
                ))
                .build();
        
        StreamingResponse<String> streamingResponse = client.streamTextToText(request);
        
        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder response = new StringBuilder();
        AtomicReference<Throwable> error = new AtomicReference<>();
        
        streamingResponse.subscribe(new StreamingCallback<String>() {
            @Override
            public void onNext(String chunk) {
                response.append(chunk);
                System.out.print(chunk);
            }
            
            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
                latch.countDown();
            }
            
            @Override
            public void onComplete() {
                System.out.println("\n=== Completed ===");
                latch.countDown();
            }
        });
        
        boolean completed = latch.await(10, TimeUnit.SECONDS);
        
        assertTrue(completed);
        assertNull(error.get());
        assertTrue(response.toString().contains("4"));
    }
    
//    @Test
    public void testStreamingCancellation() throws InterruptedException {
        System.out.println("\nTesting streaming cancellation...");
        
        // Ensure client is initialized
        if (client == null) {
            setUp();
        }
        
        ModelTextRequest request = ModelTextRequest.builder()
                .model("gpt-4o-mini")
                .temperature(0.7)
                .messages(Collections.singletonList(
                    ModelContentMessage.builder()
                        .role(Role.user)
                        .content(Collections.singletonList(
                            ModelContentMessage.ModelContentElement.builder()
                                .type(ModelTextRequest.MessageType.text)
                                .text("Write a very long story about space exploration")
                                .build()
                        ))
                        .build()
                ))
                .build();
        
        StreamingResponse<String> streamingResponse = client.streamTextToText(request);
        
        CountDownLatch startedLatch = new CountDownLatch(3); // Wait for 3 chunks
        CountDownLatch finishedLatch = new CountDownLatch(1);
        List<String> chunks = new ArrayList<>();
        
        streamingResponse.subscribe(new StreamingCallback<String>() {
            @Override
            public void onNext(String chunk) {
                chunks.add(chunk);
                System.out.print(chunk);
                startedLatch.countDown();
                
                // Cancel after receiving 3 chunks
                if (chunks.size() >= 3) {
                    System.out.println("\n\n=== Cancelling stream ===");
                    streamingResponse.cancel();
                }
            }
            
            @Override
            public void onError(Throwable throwable) {
                finishedLatch.countDown();
            }
            
            @Override
            public void onComplete() {
                finishedLatch.countDown();
            }
        });
        
        // Wait for at least 3 chunks
        boolean started = startedLatch.await(10, TimeUnit.SECONDS);
        assertTrue("Should receive at least 3 chunks", started);
        
        // Give some time for potential additional chunks after cancellation
        Thread.sleep(500);
        
        // Should not receive many more chunks after cancellation
        System.out.println("\nTotal chunks received: " + chunks.size());
        assertTrue("Should stop receiving chunks shortly after cancellation", chunks.size() <= 10);
        assertFalse("Stream should not be active after cancellation", streamingResponse.isActive());
    }
}
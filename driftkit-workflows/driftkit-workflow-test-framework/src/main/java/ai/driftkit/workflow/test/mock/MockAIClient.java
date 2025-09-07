package ai.driftkit.workflow.test.mock;

import ai.driftkit.common.domain.client.ModelClient;
import ai.driftkit.common.domain.client.ModelTextRequest;
import ai.driftkit.common.domain.client.ModelTextResponse;
import ai.driftkit.common.domain.client.ModelImageResponse.ModelContentMessage;
import ai.driftkit.common.domain.client.ModelImageResponse.ModelMessage;
import ai.driftkit.common.domain.client.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Mock AI client for testing workflows that use AI models.
 * Provides various response strategies and interaction tracking.
 */
@Slf4j
@Builder
@AllArgsConstructor
public class MockAIClient extends ModelClient<Void> {
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    @Builder.Default
    private final Map<String, ResponseStrategy> responseStrategies = new ConcurrentHashMap<>();
    
    @Builder.Default
    private final List<CallRecord> callHistory = Collections.synchronizedList(new ArrayList<>());
    
    @Builder.Default
    private final ResponseStrategy defaultStrategy = ResponseStrategy.fixed("Default response");
    
    @Builder.Default
    private final AtomicInteger callCount = new AtomicInteger(0);
    
    @Builder.Default
    private final boolean recordCalls = true;
    
    public ModelTextResponse textToText(ModelTextRequest request) {
        return textToText(request, null);
    }
    
    public ModelTextResponse textToText(ModelTextRequest request, Map<String, Object> options) {
        int callNumber = callCount.incrementAndGet();
        
        // Record the call
        if (recordCalls) {
            callHistory.add(new CallRecord(callNumber, request, options, System.currentTimeMillis()));
        }
        
        // Find matching strategy
        ResponseStrategy strategy = findStrategy(request);
        
        // Generate response
        String response = strategy.generateResponse(request, callNumber);
        
        log.debug("Mock AI call #{}: Generated response: {}", callNumber, response);
        
        return ModelTextResponse.builder()
            .id("mock-" + UUID.randomUUID())
            .model(request.getModel())
            .choices(List.of(
                ModelTextResponse.ResponseMessage.builder()
                    .message(ModelMessage.builder()
                        .role(Role.assistant)
                        .content(response)
                        .build())
                    .build()
            ))
            .usage(ModelTextResponse.Usage.builder()
                .promptTokens(10)
                .completionTokens(15)
                .totalTokens(25)
                .build())
            .build();
    }
    
    @Override
    public Set<Capability> getCapabilities() {
        return Set.of(Capability.TEXT_TO_TEXT);
    }
    
    /**
     * Add a response strategy for specific conditions.
     */
    public MockAIClient withStrategy(String name, ResponseStrategy strategy) {
        responseStrategies.put(name, strategy);
        return this;
    }
    
    /**
     * Add a fixed response for prompts containing specific text.
     */
    public MockAIClient whenPromptContains(String text, String response) {
        String strategyName = "contains_" + text;
        responseStrategies.put(strategyName, ResponseStrategy.conditional(
            request -> containsText(request, text),
            ResponseStrategy.fixed(response)
        ));
        return this;
    }
    
    /**
     * Add a pattern-based response.
     */
    public MockAIClient whenPromptMatches(String pattern, String response) {
        Pattern regex = Pattern.compile(pattern);
        String strategyName = "matches_" + pattern;
        responseStrategies.put(strategyName, ResponseStrategy.conditional(
            request -> matchesPattern(request, regex),
            ResponseStrategy.fixed(response)
        ));
        return this;
    }
    
    /**
     * Add responses that vary by call count.
     */
    public MockAIClient withSequentialResponses(String... responses) {
        responseStrategies.put("sequential", ResponseStrategy.sequential(responses));
        return this;
    }
    
    /**
     * Add a response that simulates tool/function calling.
     */
    public MockAIClient withFunctionCall(String functionName, Map<String, Object> args) {
        try {
            Map<String, Object> functionCall = Map.of(
                "function", functionName,
                "arguments", args
            );
            String jsonResponse = OBJECT_MAPPER.writeValueAsString(functionCall);
            responseStrategies.put("function_" + functionName, ResponseStrategy.fixed(jsonResponse));
        } catch (Exception e) {
            log.error("Failed to create function call response", e);
        }
        return this;
    }
    
    /**
     * Get call history.
     */
    public List<CallRecord> getCallHistory() {
        return new ArrayList<>(callHistory);
    }
    
    /**
     * Get call count.
     */
    public int getCallCount() {
        return callCount.get();
    }
    
    /**
     * Reset the mock state.
     */
    public void reset() {
        callCount.set(0);
        callHistory.clear();
    }
    
    /**
     * Verify that a call was made with specific content.
     */
    public boolean wasCalledWith(Predicate<ModelTextRequest> predicate) {
        return callHistory.stream()
            .anyMatch(record -> predicate.test(record.request()));
    }
    
    /**
     * Get the last call made.
     */
    public CallRecord getLastCall() {
        if (callHistory.isEmpty()) {
            return null;
        }
        return callHistory.get(callHistory.size() - 1);
    }
    
    private ResponseStrategy findStrategy(ModelTextRequest request) {
        // Try conditional strategies first
        for (ResponseStrategy strategy : responseStrategies.values()) {
            if (strategy.matches(request)) {
                return strategy;
            }
        }
        return defaultStrategy;
    }
    
    private boolean containsText(ModelTextRequest request, String text) {
        String content = extractContent(request);
        return content.toLowerCase().contains(text.toLowerCase());
    }
    
    private boolean matchesPattern(ModelTextRequest request, Pattern pattern) {
        String content = extractContent(request);
        return pattern.matcher(content).find();
    }
    
    private String extractContent(ModelTextRequest request) {
        StringBuilder content = new StringBuilder();
        
        if (request.getMessages() != null) {
            for (ModelContentMessage message : request.getMessages()) {
                content.append(message.toString()).append(" ");
            }
        }
        
        return content.toString();
    }
    
    
    /**
     * Record of an AI client call.
     */
    public record CallRecord(
        int callNumber,
        ModelTextRequest request,
        Map<String, Object> options,
        long timestamp
    ) {}
    
    /**
     * Response generation strategy.
     */
    @FunctionalInterface
    public interface ResponseStrategy {
        String generateResponse(ModelTextRequest request, int callNumber);
        
        default boolean matches(ModelTextRequest request) {
            return true;
        }
        
        /**
         * Create a fixed response strategy.
         */
        static ResponseStrategy fixed(String response) {
            return (request, callNumber) -> response;
        }
        
        /**
         * Create a sequential response strategy.
         */
        static ResponseStrategy sequential(String... responses) {
            return (request, callNumber) -> {
                int index = (callNumber - 1) % responses.length;
                return responses[index];
            };
        }
        
        /**
         * Create a random response strategy.
         */
        static ResponseStrategy random(String... responses) {
            Random random = new Random();
            return (request, callNumber) -> 
                responses[random.nextInt(responses.length)];
        }
        
        /**
         * Create a conditional response strategy.
         */
        static ResponseStrategy conditional(Predicate<ModelTextRequest> condition, 
                                          ResponseStrategy strategy) {
            return new ResponseStrategy() {
                @Override
                public String generateResponse(ModelTextRequest request, int callNumber) {
                    return strategy.generateResponse(request, callNumber);
                }
                
                @Override
                public boolean matches(ModelTextRequest request) {
                    return condition.test(request);
                }
            };
        }
        
        /**
         * Create a function-based response strategy.
         */
        static ResponseStrategy dynamic(Function<ModelTextRequest, String> generator) {
            return (request, callNumber) -> generator.apply(request);
        }
    }
    
}
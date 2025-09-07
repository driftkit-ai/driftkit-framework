package ai.driftkit.workflow.test.core;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Test context that holds shared state and configuration for workflow tests.
 * Thread-safe implementation for use in concurrent test scenarios.
 */
@Slf4j
@Getter
public class WorkflowTestContext {
    
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private final MockRegistry mockRegistry = new MockRegistry();
    private final ExecutionTracker executionTracker = new ExecutionTracker();
    private WorkflowTestInterceptor testInterceptor;
    
    /**
     * Stores an attribute in the test context.
     * 
     * @param key the attribute key
     * @param value the attribute value
     * @return this context for chaining
     */
    public WorkflowTestContext setAttribute(String key, Object value) {
        Objects.requireNonNull(key, "key cannot be null");
        attributes.put(key, value);
        return this;
    }
    
    /**
     * Gets an attribute from the test context.
     * 
     * @param key the attribute key
     * @param <T> the expected type
     * @return the attribute value or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        Objects.requireNonNull(key, "key cannot be null");
        return (T) attributes.get(key);
    }
    
    /**
     * Gets an attribute from the test context with a default value.
     * 
     * @param key the attribute key
     * @param defaultValue the default value if not found
     * @param <T> the expected type
     * @return the attribute value or default value
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key, T defaultValue) {
        Objects.requireNonNull(key, "key cannot be null");
        return (T) attributes.getOrDefault(key, defaultValue);
    }
    
    /**
     * Removes an attribute from the test context.
     * 
     * @param key the attribute key
     * @return the removed value or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T removeAttribute(String key) {
        Objects.requireNonNull(key, "key cannot be null");
        return (T) attributes.remove(key);
    }
    
    /**
     * Checks if an attribute exists in the test context.
     * 
     * @param key the attribute key
     * @return true if the attribute exists
     */
    public boolean hasAttribute(String key) {
        Objects.requireNonNull(key, "key cannot be null");
        return attributes.containsKey(key);
    }
    
    /**
     * Clears all state in the test context.
     */
    public void clear() {
        log.debug("Clearing test context");
        attributes.clear();
        mockRegistry.clear();
        executionTracker.clear();
    }
    
    /**
     * Configures the test context using a fluent configuration lambda.
     * 
     * @param configurer the configuration lambda
     * @return this context for chaining
     */
    public WorkflowTestContext configure(TestConfigurer configurer) {
        Objects.requireNonNull(configurer, "configurer cannot be null");
        configurer.configure(new TestConfiguration(this));
        return this;
    }
    
    /**
     * Interface for test configuration.
     */
    @FunctionalInterface
    public interface TestConfigurer {
        void configure(TestConfiguration config);
    }
    
    /**
     * Test configuration builder.
     */
    public static class TestConfiguration {
        private final WorkflowTestContext context;
        
        TestConfiguration(WorkflowTestContext context) {
            this.context = context;
        }
        
        /**
         * Starts mock configuration.
         * 
         * @return mock builder
         */
        public MockBuilder mock() {
            return new MockBuilder(context.mockRegistry);
        }
        
        /**
         * Sets a context attribute.
         * 
         * @param key the attribute key
         * @param value the attribute value
         * @return this configuration for chaining
         */
        public TestConfiguration withAttribute(String key, Object value) {
            context.setAttribute(key, value);
            return this;
        }
    }
    
    /**
     * Gets the mock builder for fluent API.
     * 
     * @return mock builder
     */
    public MockBuilder getMockBuilder() {
        return new MockBuilder(mockRegistry);
    }
    
    /**
     * Sets the test interceptor.
     * 
     * @param interceptor the interceptor to set
     */
    public void setTestInterceptor(WorkflowTestInterceptor interceptor) {
        this.testInterceptor = interceptor;
    }
    
    /**
     * Resets the test context state.
     * Alias for clear() for better API consistency.
     */
    public void reset() {
        clear();
    }
}
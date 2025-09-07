package ai.driftkit.workflow.engine.agent;

import lombok.extern.slf4j.Slf4j;

/**
 * Registry for RequestTracingProvider instances.
 * Allows Spring-based implementations to register themselves automatically.
 */
@Slf4j
public class RequestTracingRegistry {
    
    private static volatile RequestTracingProvider instance;
    
    /**
     * Register a tracing provider (typically called by Spring components)
     */
    public static void register(RequestTracingProvider provider) {
        instance = provider;
        log.info("Registered RequestTracingProvider: {}", provider.getClass().getSimpleName());
    }
    
    /**
     * Get the current tracing provider instance
     */
    public static RequestTracingProvider getInstance() {
        return instance;
    }
    
    /**
     * Check if a tracing provider is available
     */
    public static boolean isAvailable() {
        return instance != null;
    }
    
    /**
     * Unregister the current provider (useful for testing)
     */
    public static void unregister() {
        RequestTracingProvider old = instance;
        instance = null;
        if (old != null) {
            log.info("Unregistered RequestTracingProvider: {}", old.getClass().getSimpleName());
        }
    }
}
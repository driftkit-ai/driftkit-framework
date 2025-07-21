package ai.driftkit.context.core.registry;

import ai.driftkit.context.core.service.PromptService;
import lombok.extern.slf4j.Slf4j;

/**
 * Global registry for PromptService instances.
 * 
 * This registry allows components (like LLMAgent) to automatically discover and use PromptService
 * when available in the system without requiring explicit configuration.
 * 
 * Usage:
 * - Spring Boot applications should register PromptService via auto-configuration
 * - Manual applications can register via PromptServiceRegistry.register()
 * - Components automatically use registered instance if available
 */
@Slf4j
public class PromptServiceRegistry {
    
    private static volatile PromptService globalInstance;
    private static final Object lock = new Object();
    
    /**
     * Register a PromptService instance globally.
     * 
     * @param promptService The PromptService instance to register
     */
    public static void register(PromptService promptService) {
        synchronized (lock) {
            if (globalInstance != null && globalInstance != promptService) {
                log.warn("Replacing existing PromptService registration. " +
                        "Previous: {}, New: {}", 
                        globalInstance.getClass().getSimpleName(),
                        promptService.getClass().getSimpleName());
            }
            globalInstance = promptService;
            log.debug("Registered PromptService: {}", promptService.getClass().getSimpleName());
        }
    }
    
    /**
     * Get the globally registered PromptService instance.
     * 
     * @return The registered PromptService, or null if none is registered
     */
    public static PromptService getInstance() {
        return globalInstance;
    }
    
    /**
     * Check if a PromptService is registered.
     * 
     * @return true if a PromptService is registered, false otherwise
     */
    public static boolean isRegistered() {
        return globalInstance != null;
    }
    
    /**
     * Unregister the current PromptService instance.
     * This is mainly useful for testing scenarios.
     */
    public static void unregister() {
        synchronized (lock) {
            if (globalInstance != null) {
                log.debug("Unregistered PromptService: {}", globalInstance.getClass().getSimpleName());
                globalInstance = null;
            }
        }
    }
    
    /**
     * Get the registered PromptService or throw an exception if none is available.
     * 
     * @return The registered PromptService
     * @throws IllegalStateException if no PromptService is registered
     */
    public static PromptService getRequiredInstance() {
        PromptService instance = getInstance();
        if (instance == null) {
            throw new IllegalStateException("No PromptService is registered. " +
                    "Please ensure PromptService is available in your application context " +
                    "or register one manually via PromptServiceRegistry.register()");
        }
        return instance;
    }
}
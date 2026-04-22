package ai.driftkit.context.core.service;

import java.util.Collections;
import java.util.Map;

/**
 * Thread-local context for overriding prompts during pipeline test execution.
 * When active, PromptService returns the override text instead of querying storage.
 *
 * Usage:
 * <pre>
 * PromptOverrideContext.set(Map.of("classifier", "override prompt text"));
 * try {
 *     // Any PromptService.getCurrentPrompt("classifier") will return the override
 *     workflowEngine.execute(workflowId, input);
 * } finally {
 *     PromptOverrideContext.clear();
 * }
 * </pre>
 */
public class PromptOverrideContext {

    private static final ThreadLocal<Map<String, String>> OVERRIDES = new ThreadLocal<>();

    /**
     * Set prompt overrides for the current thread. Key = prompt method, Value = override text.
     */
    public static void set(Map<String, String> overrides) {
        OVERRIDES.set(overrides != null ? overrides : Collections.emptyMap());
    }

    /**
     * Get the override text for a prompt method, or null if not overridden.
     */
    public static String getOverride(String method) {
        Map<String, String> overrides = OVERRIDES.get();
        return overrides != null ? overrides.get(method) : null;
    }

    /**
     * Check if there are any active overrides.
     */
    public static boolean isActive() {
        Map<String, String> overrides = OVERRIDES.get();
        return overrides != null && !overrides.isEmpty();
    }

    /**
     * Clear overrides for the current thread. Always call in finally block.
     */
    public static void clear() {
        OVERRIDES.remove();
    }
}

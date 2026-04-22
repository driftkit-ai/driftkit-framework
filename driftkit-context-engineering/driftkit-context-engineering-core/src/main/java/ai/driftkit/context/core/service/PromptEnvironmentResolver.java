package ai.driftkit.context.core.service;

import ai.driftkit.common.domain.Language;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-local environment context for prompt resolution.
 * When set, PromptServiceBase resolves prompts for the specified environment
 * instead of always returning CURRENT state.
 *
 * The actual resolution (version lookup from PromptEnvironment collection)
 * is handled by a registered resolver function.
 */
public class PromptEnvironmentResolver {

    /**
     * Resolves a prompt method+language+environment to a specific version number.
     * Returns null if no environment mapping exists (falls back to CURRENT).
     */
    @FunctionalInterface
    public interface VersionResolver {
        Integer resolve(String method, Language language, String environment);
    }

    private static final ThreadLocal<String> CURRENT_ENVIRONMENT = new ThreadLocal<>();
    private static final AtomicReference<VersionResolver> resolver = new AtomicReference<>();

    /**
     * Register the version resolver (called once at app startup).
     */
    public static void setResolver(VersionResolver r) {
        resolver.set(r);
    }

    /**
     * Set the environment for the current thread (e.g., "staging").
     */
    public static void setEnvironment(String environment) {
        CURRENT_ENVIRONMENT.set(environment);
    }

    /**
     * Get the current environment, or null if not set (defaults to CURRENT state).
     */
    public static String getEnvironment() {
        return CURRENT_ENVIRONMENT.get();
    }

    /**
     * Resolve version for the given method in the current environment.
     * Returns null if no resolver or no environment is set.
     */
    public static Integer resolveVersion(String method, Language language) {
        String env = CURRENT_ENVIRONMENT.get();
        VersionResolver r = resolver.get();
        if (env == null || r == null) return null;
        return r.resolve(method, language, env);
    }

    public static void clear() {
        CURRENT_ENVIRONMENT.remove();
    }
}

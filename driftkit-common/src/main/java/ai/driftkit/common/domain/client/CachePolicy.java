package ai.driftkit.common.domain.client;

/**
 * Request-level caching strategy for AI model calls.
 */
public enum CachePolicy {

    /**
     * No caching hints. Provider may still cache automatically (OpenAI/DeepSeek).
     */
    NONE,

    /**
     * Automatic caching.
     * <ul>
     *   <li>Claude: auto-places {@code cache_control} breakpoints on system messages
     *       and large content blocks (&gt;1024 tokens).</li>
     *   <li>OpenAI/DeepSeek: no-op (prefix caching is native).</li>
     * </ul>
     */
    AUTO,

    /**
     * Manual caching. Uses {@link CacheControl} markers on individual content blocks.
     * Only effective for Claude. OpenAI/DeepSeek ignore markers.
     */
    MANUAL
}

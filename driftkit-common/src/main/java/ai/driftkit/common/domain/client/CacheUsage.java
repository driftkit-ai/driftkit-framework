package ai.driftkit.common.domain.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Unified cache usage metrics across all AI providers.
 * <p>
 * Maps from provider-specific fields:
 * <ul>
 *   <li>Claude: {@code cache_read_input_tokens} / {@code cache_creation_input_tokens}</li>
 *   <li>OpenAI: {@code prompt_tokens_details.cached_tokens}</li>
 *   <li>DeepSeek: {@code prompt_cache_hit_tokens} / {@code prompt_cache_miss_tokens}</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CacheUsage {

    /** Tokens served from cache (discounted cost). */
    private Integer cacheHitTokens;

    /** Tokens not found in cache (full price). */
    private Integer cacheMissTokens;

    /** Tokens written to cache. Claude: +25% cost surcharge. */
    private Integer cacheWriteTokens;

    /**
     * Cache hit ratio (0.0 - 1.0).
     * Returns 0.0 if no cache-related tokens are reported.
     */
    public double getHitRatio() {
        int hit = cacheHitTokens != null ? cacheHitTokens : 0;
        int miss = cacheMissTokens != null ? cacheMissTokens : 0;
        int total = hit + miss;
        return total > 0 ? (double) hit / total : 0.0;
    }
}

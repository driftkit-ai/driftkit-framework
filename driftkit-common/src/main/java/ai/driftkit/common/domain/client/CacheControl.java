package ai.driftkit.common.domain.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cache control marker for individual content blocks in AI requests.
 * <p>
 * For Claude: translates to {@code cache_control: {"type": "ephemeral"}} on content blocks.
 * For OpenAI/DeepSeek: serves as a hint; actual caching is automatic (prefix-based).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CacheControl {

    private CacheType type;

    public enum CacheType {
        /** Claude: explicit breakpoint with 5-minute TTL. */
        EPHEMERAL,
        /** Provider decides caching strategy (OpenAI/DeepSeek prefix matching). */
        AUTO
    }

    public static CacheControl ephemeral() {
        return new CacheControl(CacheType.EPHEMERAL);
    }

    public static CacheControl auto() {
        return new CacheControl(CacheType.AUTO);
    }
}

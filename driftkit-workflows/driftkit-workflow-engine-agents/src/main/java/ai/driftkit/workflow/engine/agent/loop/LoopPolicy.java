package ai.driftkit.workflow.engine.agent.loop;

import lombok.Builder;
import lombok.Value;

import java.time.Duration;

/**
 * Limits and recovery configuration of an agentic loop.
 *
 * <p>The loop bound is finite by construction: {@code maxTurns} is validated into
 * {@code 1..HARD_CAP_TURNS} at build time. {@code while(true)} implementations
 * are forbidden by review rule — see the improvement plan, section 7.</p>
 */
@Value
public class LoopPolicy {

    /** Absolute ceiling for turns; not overridable by configuration. */
    public static final int HARD_CAP_TURNS = 100;

    /** Max model turns per loop run. Validated: 1..HARD_CAP_TURNS. */
    int maxTurns;

    /** Optional wall-clock limit for the entire loop run; null = no deadline. */
    Duration maxDuration;

    /** Hard stop on total tokens (prompt+completion) across all turns; non-positive = unlimited. */
    long maxTotalTokens;

    /** Circuit breaker: consecutive model-call failures before giving up. */
    int maxConsecutiveFailures;

    /** Base delay between model-call retries; doubled per consecutive failure. */
    Duration retryBaseDelay;

    /** One reactive compaction attempt on context-overflow errors. */
    boolean reactiveCompactEnabled;

    /** Max tools executed concurrently within one concurrency-safe batch. */
    int maxToolConcurrency;

    @Builder(toBuilder = true)
    private LoopPolicy(Integer maxTurns, Duration maxDuration, Long maxTotalTokens,
                       Integer maxConsecutiveFailures, Duration retryBaseDelay,
                       Boolean reactiveCompactEnabled, Integer maxToolConcurrency) {
        this.maxTurns = maxTurns != null ? maxTurns : 10;
        this.maxDuration = maxDuration;
        this.maxTotalTokens = maxTotalTokens != null ? maxTotalTokens : 0;
        this.maxConsecutiveFailures = maxConsecutiveFailures != null ? maxConsecutiveFailures : 3;
        this.retryBaseDelay = retryBaseDelay != null ? retryBaseDelay : Duration.ofMillis(500);
        this.reactiveCompactEnabled = reactiveCompactEnabled == null || reactiveCompactEnabled;
        this.maxToolConcurrency = maxToolConcurrency != null ? maxToolConcurrency : 10;

        if (this.maxTurns < 1 || this.maxTurns > HARD_CAP_TURNS) {
            throw new IllegalArgumentException(
                    "maxTurns must be in 1..%d, got %d".formatted(HARD_CAP_TURNS, this.maxTurns));
        }
        if (this.maxConsecutiveFailures < 1 || this.maxConsecutiveFailures > 10) {
            throw new IllegalArgumentException("maxConsecutiveFailures must be in 1..10, got "
                    + this.maxConsecutiveFailures);
        }
        if (this.maxToolConcurrency < 1) {
            throw new IllegalArgumentException("maxToolConcurrency must be >= 1");
        }
        if (this.maxDuration != null && (this.maxDuration.isZero() || this.maxDuration.isNegative())) {
            throw new IllegalArgumentException("maxDuration must be positive when set");
        }
    }

    public static LoopPolicy defaults() {
        return LoopPolicy.builder().build();
    }
}

package ai.driftkit.common.tools;

import lombok.Builder;
import lombok.Value;

/**
 * Execution metadata of a tool, used by the agent loop for batching,
 * permission policies and context protection.
 */
@Value
@Builder
public class ToolMetadata {

    public static final int DEFAULT_MAX_RESULT_CHARS = 50_000;

    /** Tool does not mutate external state; may be auto-approved and retried. */
    @Builder.Default
    boolean readOnly = false;

    /** Tool may run in parallel with other concurrency-safe tools. */
    @Builder.Default
    boolean concurrencySafe = false;

    /** Tool performs a hard-to-reverse action; subject to HITL approval gates. */
    @Builder.Default
    boolean destructive = false;

    /** Max characters of the rendered result fed back to the model; non-positive = unlimited. */
    @Builder.Default
    int maxResultChars = DEFAULT_MAX_RESULT_CHARS;

    /** Positive guidance rendered into the tool description. */
    String whenToUse;

    /** Negative guidance rendered into the tool description. */
    String whenNotToUse;

    public static ToolMetadata defaults() {
        return ToolMetadata.builder().build();
    }

    public static ToolMetadata from(Tool annotation) {
        return ToolMetadata.builder()
                .readOnly(annotation.readOnly())
                .concurrencySafe(annotation.concurrencySafe())
                .destructive(annotation.destructive())
                .maxResultChars(annotation.maxResultChars())
                .whenToUse(emptyToNull(annotation.whenToUse()))
                .whenNotToUse(emptyToNull(annotation.whenNotToUse()))
                .build();
    }

    /**
     * Compose the full model-facing description: base description plus
     * "when to use / when not to use" guidance (Claude Code tool-prompt pattern).
     */
    public String composeDescription(String baseDescription) {
        if (whenToUse == null && whenNotToUse == null) {
            return baseDescription;
        }
        StringBuilder sb = new StringBuilder(baseDescription == null ? "" : baseDescription);
        if (whenToUse != null) {
            sb.append("\nWhen to use: ").append(whenToUse);
        }
        if (whenNotToUse != null) {
            sb.append("\nWhen NOT to use: ").append(whenNotToUse);
        }
        return sb.toString();
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}

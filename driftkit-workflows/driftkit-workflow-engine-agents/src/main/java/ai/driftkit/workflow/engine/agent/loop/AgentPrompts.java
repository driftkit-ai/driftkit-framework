package ai.driftkit.workflow.engine.agent.loop;

import ai.driftkit.context.core.service.PromptService;
import ai.driftkit.context.core.util.DefaultPromptLoader;

import java.util.Map;

/**
 * Identifiers of the starter agent prompts (plan, D9). The prompt BODIES are not
 * hardcoded here: they live as resources under {@code /prompts/{id}.prompt} and are
 * auto-registered into {@link PromptService} on first use by
 * {@link DefaultPromptLoader}, which makes them editable through the
 * context-engineering platform. The resource is only the fallback/seed; a
 * platform-edited CURRENT version always wins.
 *
 * <p>Prompt design follows the Anthropic prompt-pattern catalog: hard "TEXT ONLY"
 * constraint with cost framing (compact), 8-section summary structure (compact),
 * prompt-injection warning (system), Bad/Good anti-pattern pairs (evaluator).</p>
 */
public final class AgentPrompts {

    private AgentPrompts() {
    }

    /** Conversation-summary (compaction) prompt. Variable: {@code {{conversation}}}. */
    public static final String COMPACT_PROMPT_ID = "driftkit.agent.compact";

    /** Default agent system prompt (tool discipline + prompt-injection warning). */
    public static final String SYSTEM_PROMPT_ID = "driftkit.agent.system.default";

    /** LoopAgent evaluator prompt (criteria + Bad/Good anti-patterns). */
    public static final String EVALUATOR_PROMPT_ID = "loop.agent.structured.evaluation";

    /**
     * Resolve a prompt body with variables applied: PromptService CURRENT version
     * first, bundled resource as fallback.
     *
     * @throws IllegalStateException if neither the service nor the resources know the id
     */
    public static String load(String promptId, Map<String, Object> variables) {
        String prompt = DefaultPromptLoader.loadPrompt(promptId, variables == null ? Map.of() : variables);
        if (prompt == null) {
            throw new IllegalStateException("Prompt not found: " + promptId
                    + " — expected a PromptService entry or a /prompts/" + promptId + ".prompt resource");
        }
        return prompt;
    }

    /**
     * Seed the starter prompts into the PromptService so they become visible and
     * editable in the context-engineering platform. Existing prompts are untouched.
     */
    public static void registerDefaults() {
        DefaultPromptLoader.loadPrompt(COMPACT_PROMPT_ID, Map.of());
        DefaultPromptLoader.loadPrompt(SYSTEM_PROMPT_ID, Map.of());
        DefaultPromptLoader.loadPrompt(EVALUATOR_PROMPT_ID, Map.of());
    }
}

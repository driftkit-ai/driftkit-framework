package ai.driftkit.common.tools;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark methods as tool functions that can be called by language models.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Tool {

    /**
     * Function name. If not specified, method name is used.
     */
    String name() default "";

    /**
     * Function description for the language model.
     */
    String description();

    /**
     * When the model should prefer this tool. Rendered into the tool description
     * so the model sees it on every call.
     */
    String whenToUse() default "";

    /**
     * When the model should NOT use this tool. Negative guidance reduces
     * over-application; rendered into the tool description.
     */
    String whenNotToUse() default "";

    /**
     * Tool does not mutate any external state. Read-only tools may be
     * auto-approved by HITL policies and freely retried.
     */
    boolean readOnly() default false;

    /**
     * Tool may run concurrently with other concurrency-safe tools in the same
     * batch of tool calls. Defaults to false: unsafe tools execute serially.
     */
    boolean concurrencySafe() default false;

    /**
     * Tool performs a hard-to-reverse action (delete, send, pay). Destructive
     * tools are subject to approval gates in HITL policies.
     */
    boolean destructive() default false;

    /**
     * Maximum characters of the rendered tool result fed back to the model.
     * Longer results are truncated with an explicit marker so a single tool
     * cannot exhaust the context window. Non-positive value = no limit.
     */
    int maxResultChars() default 50_000;
}

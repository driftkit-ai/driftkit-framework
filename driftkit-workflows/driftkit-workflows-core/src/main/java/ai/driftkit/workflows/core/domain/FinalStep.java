package ai.driftkit.workflows.core.domain;

public @interface FinalStep {
    String expression() default "";    // The logic to execute

    int invocationLimit() default 1;
}

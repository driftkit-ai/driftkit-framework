package ai.driftkit.workflow.engine.domain;

import ai.driftkit.workflow.engine.schema.AIFunctionSchema;

/**
 * Metadata about a workflow step.
 */
public record StepMetadata(
    String id,
    String description,
    boolean async,
    AIFunctionSchema inputSchema,
    AIFunctionSchema outputSchema
) {}
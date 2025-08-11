package ai.driftkit.workflow.engine.domain;

/**
 * Basic workflow metadata.
 */
public record WorkflowMetadata(
    String id,
    String version,
    String description,
    Class<?> inputType,
    Class<?> outputType
) {}
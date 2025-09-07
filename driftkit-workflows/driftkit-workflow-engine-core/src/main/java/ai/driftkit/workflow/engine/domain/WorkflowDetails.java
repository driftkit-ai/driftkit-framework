package ai.driftkit.workflow.engine.domain;

import ai.driftkit.workflow.engine.schema.AIFunctionSchema;
import java.util.List;

/**
 * Detailed workflow information including steps.
 */
public record WorkflowDetails(
    WorkflowMetadata metadata,
    List<StepMetadata> steps,
    String initialStepId,
    AIFunctionSchema initialSchema
) {}
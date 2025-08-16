package ai.driftkit.workflow.engine.builder;

/**
 * Marker interface for internal routing objects used by fluent API workflows.
 * Objects implementing this interface are used for routing decisions only
 * and should not be passed as input data to subsequent workflow steps.
 */
public interface InternalRoutingMarker {
}
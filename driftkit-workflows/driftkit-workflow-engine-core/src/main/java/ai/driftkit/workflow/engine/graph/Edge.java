package ai.driftkit.workflow.engine.graph;

import java.util.function.Predicate;

/**
 * Represents an edge in the workflow graph, defining the transition between steps.
 * Edges can be conditional or unconditional, and typed or untyped.
 */
public record Edge(
    String fromStepId,
    String toStepId,
    EdgeType type,
    Class<?> eventType,
    Predicate<Object> condition,
    String description
) {
    /**
     * Types of edges in the workflow graph.
     */
    public enum EdgeType {
        /**
         * Standard sequential edge - flows when previous step returns Continue
         */
        SEQUENTIAL,
        
        /**
         * Branch edge - flows when previous step returns Branch with matching event type
         */
        BRANCH,
        
        /**
         * Conditional edge - flows based on predicate evaluation
         */
        CONDITIONAL,
        
        /**
         * Error edge - flows when previous step returns Fail
         */
        ERROR,
        
        /**
         * Parallel edge - indicates parallel execution branch
         */
        PARALLEL
    }
    
    /**
     * Validates the Edge parameters.
     */
    public Edge {
        if (fromStepId == null || fromStepId.isBlank()) {
            throw new IllegalArgumentException("From step ID cannot be null or blank");
        }
        if (toStepId == null || toStepId.isBlank()) {
            throw new IllegalArgumentException("To step ID cannot be null or blank");
        }
        if (type == null) {
            type = EdgeType.SEQUENTIAL;
        }
        if (description == null || description.isBlank()) {
            description = generateDescription(type, eventType);
        }
    }
    
    /**
     * Creates a simple sequential edge.
     */
    public static Edge sequential(String from, String to) {
        return new Edge(from, to, EdgeType.SEQUENTIAL, null, null, null);
    }
    
    /**
     * Creates a branch edge for a specific event type.
     */
    public static Edge branch(String from, String to, Class<?> eventType) {
        return new Edge(from, to, EdgeType.BRANCH, eventType, null, null);
    }
    
    /**
     * Creates a conditional edge with a predicate.
     */
    public static Edge conditional(String from, String to, Predicate<Object> condition, String description) {
        return new Edge(from, to, EdgeType.CONDITIONAL, null, condition, description);
    }
    
    /**
     * Creates an error handling edge.
     */
    public static Edge error(String from, String to) {
        return new Edge(from, to, EdgeType.ERROR, null, null, "On error");
    }
    
    /**
     * Creates a parallel execution edge.
     */
    public static Edge parallel(String from, String to) {
        return new Edge(from, to, EdgeType.PARALLEL, null, null, "Parallel execution");
    }
    
    /**
     * Checks if this edge should be followed given the step result.
     * 
     * @param stepResult The result from the previous step
     * @return true if this edge should be followed
     */
    public boolean shouldFollow(Object stepResult) {
        return switch (type) {
            case SEQUENTIAL -> true; // Always follow sequential edges
            case BRANCH -> eventType != null && eventType.isInstance(stepResult);
            case CONDITIONAL -> condition != null && condition.test(stepResult);
            case ERROR -> stepResult instanceof Throwable;
            case PARALLEL -> true; // Parallel edges are always valid
        };
    }
    
    /**
     * Generates a default description based on edge type.
     */
    private static String generateDescription(EdgeType type, Class<?> eventType) {
        return switch (type) {
            case SEQUENTIAL -> "Continue";
            case BRANCH -> eventType != null ? "On " + eventType.getSimpleName() : "Branch";
            case CONDITIONAL -> "Conditional";
            case ERROR -> "On error";
            case PARALLEL -> "Parallel";
        };
    }
    
    /**
     * Creates a more readable string representation.
     */
    @Override
    public String toString() {
        return fromStepId + " -> " + toStepId + " [" + description + "]";
    }
}
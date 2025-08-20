package ai.driftkit.workflow.engine.core;

import ai.driftkit.workflow.engine.graph.Edge;
import ai.driftkit.workflow.engine.graph.StepNode;
import ai.driftkit.workflow.engine.graph.WorkflowGraph;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Default implementation of StepRouter that provides type-based routing logic.
 * This implementation uses the following priority order:
 * 1. Conditional edges (explicit predicates)
 * 2. Type-based routing (finding steps that accept the data type)
 * 3. Sequential edges (fallback behavior)
 */
@Slf4j
public class DefaultStepRouter implements StepRouter {
    
    @Override
    public String findNextStep(WorkflowGraph<?, ?> graph, String currentStepId, Object data) {
        List<Edge> edges = graph.getOutgoingEdges(currentStepId);

        if (edges.isEmpty()) {
            // No explicit edges - try pure type-based routing
            if (data != null) {
                log.debug("No edges from step {}, attempting pure type-based routing for type {}", 
                         currentStepId, data.getClass().getSimpleName());
                return findStepForInputType(graph, data.getClass(), currentStepId);
            }
            return null;
        }

        // Priority 1: Check conditional edges (explicit predicates)
        for (Edge edge : edges) {
            if (edge.type() == Edge.EdgeType.CONDITIONAL && edge.shouldFollow(data)) {
                log.debug("Following conditional edge from {} to {}", currentStepId, edge.toStepId());
                return edge.toStepId();
            }
        }

        // Priority 2: Type-based resolution - find step that accepts the data type
        if (data != null) {
            Class<?> dataType = data.getClass();

            // Check all edges and find target steps that can accept this data type
            for (Edge edge : edges) {
                // Skip non-sequential edges for type matching
                if (edge.type() != Edge.EdgeType.SEQUENTIAL && edge.type() != Edge.EdgeType.BRANCH) {
                    continue;
                }

                String targetStepId = edge.toStepId();
                StepNode targetStep = graph.getNode(targetStepId).orElse(null);

                if (targetStep != null && targetStep.canAcceptInput(dataType)) {
                    log.debug("Type-based routing: {} -> {} (data type: {})",
                            currentStepId, targetStepId, dataType.getSimpleName());
                    return targetStepId;
                }
            }

            // If no direct edge target accepts the type, search all nodes
            log.debug("No direct edge target accepts type {}, searching all nodes", dataType.getSimpleName());
            String typeMatchingStep = findStepForInputType(graph, dataType, currentStepId);
            if (typeMatchingStep != null) {
                log.debug("Found step {} that accepts type {} (no direct edge)",
                        typeMatchingStep, dataType.getSimpleName());
                return typeMatchingStep;
            }
        }

        // Priority 3: Fall back to sequential edges (original behavior)
        List<Edge> sequentialEdges = edges.stream()
                .filter(e -> e.type() == Edge.EdgeType.SEQUENTIAL)
                .collect(Collectors.toList());

        if (sequentialEdges.size() == 1) {
            return sequentialEdges.get(0).toStepId();
        } else if (sequentialEdges.size() > 1) {
            // If multiple sequential edges exist, prefer one where target accepts the data type
            if (data != null) {
                for (Edge edge : sequentialEdges) {
                    StepNode targetStep = graph.getNode(edge.toStepId()).orElse(null);
                    if (targetStep != null && targetStep.canAcceptInput(data.getClass())) {
                        log.debug("Multiple sequential edges: choosing {} which accepts type {}",
                                edge.toStepId(), data.getClass().getSimpleName());
                        return edge.toStepId();
                    }
                }
            }

            log.warn("Multiple sequential edges from step: {}. Using first one.", currentStepId);
            return sequentialEdges.get(0).toStepId();
        }

        return null;
    }
    
    @Override
    public String findBranchTarget(WorkflowGraph<?, ?> graph, String currentStepId, Object event) {
        List<Edge> edges = graph.getOutgoingEdges(currentStepId);

        // Priority 1: Find matching branch edge by event type
        for (Edge edge : edges) {
            if (edge.type() == Edge.EdgeType.BRANCH && edge.shouldFollow(event)) {
                return edge.toStepId();
            }
        }

        // Priority 2: Type-based resolution - find any step that accepts the event type
        if (event != null) {
            Class<?> eventType = event.getClass();

            // First check branch edge targets
            for (Edge edge : edges) {
                if (edge.type() == Edge.EdgeType.BRANCH) {
                    StepNode targetStep = graph.getNode(edge.toStepId()).orElse(null);
                    if (targetStep != null && targetStep.canAcceptInput(eventType)) {
                        log.debug("Branch type-based routing: {} -> {} (event type: {})",
                                currentStepId, edge.toStepId(), eventType.getSimpleName());
                        return edge.toStepId();
                    }
                }
            }

            // If no branch edge target accepts the type, search all nodes
            String typeMatchingStep = findStepForInputType(graph, eventType, currentStepId);
            if (typeMatchingStep != null) {
                log.debug("Branch found step {} that accepts event type {} (no direct edge)",
                        typeMatchingStep, eventType.getSimpleName());
                return typeMatchingStep;
            }
        }

        return null;
    }
    
    @Override
    public String findStepForInputType(WorkflowGraph<?, ?> graph, Class<?> inputType, String excludeStepId) {
        
        // First, check if there are any outgoing edges from the current step
        List<Edge> edges = graph.getOutgoingEdges(excludeStepId);

        // Look for branch edges that match the input type
        for (Edge edge : edges) {
            if (edge.type() == Edge.EdgeType.BRANCH && edge.eventType() != null &&
                    edge.eventType().isAssignableFrom(inputType)) {
                return edge.toStepId();
            }
        }

        // If no direct edge found, search all nodes for one that accepts this input type
        for (StepNode node : graph.nodes().values()) {
            // Allow steps to process the same input type again (self-loop for type-based routing)
            if (!node.isInitial() && 
                node.canAcceptInput(inputType)) {
                log.debug("Found step {} that can accept input type {}",
                        node.id(), inputType.getSimpleName());
                return node.id();
            }
        }
        return null;
    }
}
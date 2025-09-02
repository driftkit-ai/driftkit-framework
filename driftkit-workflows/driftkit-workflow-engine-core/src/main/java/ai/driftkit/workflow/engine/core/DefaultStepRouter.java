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
        log.debug("Finding branch target from step: {} for event: {} (type: {})", 
                 currentStepId, event, event != null ? event.getClass().getName() : "null");
        
        List<Edge> edges = graph.getOutgoingEdges(currentStepId);
        log.debug("Available edges from {}: {}", currentStepId, edges.size());

        // Priority 1: Find matching branch edge by event type
        for (Edge edge : edges) {
            log.debug("Checking edge: {} -> {}, type: {}, eventType: {}, branchValue: {}", 
                edge.fromStepId(), edge.toStepId(), edge.type(), 
                edge.eventType() != null ? edge.eventType().getSimpleName() : "null",
                edge.branchValue());
            
            if (edge.type() == Edge.EdgeType.BRANCH && edge.shouldFollow(event)) {
                log.debug("Found matching branch edge: {} -> {}", currentStepId, edge.toStepId());
                return edge.toStepId();
            }
        }

        // Priority 2: Type-based resolution - find any step that accepts the event type
        if (event != null) {
            Class<?> eventType = event.getClass();
            log.debug("No exact branch match, trying type-based resolution for {}", eventType.getName());

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

        log.warn("No branch target found for event: {} from step: {}", event, currentStepId);
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
        // First pass: look for steps other than the excluded one
        for (StepNode node : graph.nodes().values()) {
            // Skip the excluded step to prevent infinite loops
            if (!node.isInitial() && 
                !node.id().equals(excludeStepId)) {
                
                Class<?> expectedType = node.executor() != null ? node.executor().getInputType() : null;
                log.debug("Checking step {} with expected type {} against input type {}", 
                         node.id(), 
                         expectedType != null ? expectedType.getName() : "null",
                         inputType.getName());
                
                if (node.canAcceptInput(inputType)) {
                    log.debug("Found step {} that can accept input type {}",
                            node.id(), inputType.getSimpleName());
                    return node.id();
                }
            }
        }
        
        // Second pass: if no other step found, check if the excluded step can handle it
        // This allows steps to handle multiple inputs of the same type (e.g., multiple questions)
        if (excludeStepId != null) {
            StepNode excludedNode = graph.getNode(excludeStepId).orElse(null);
            if (excludedNode != null && !excludedNode.isInitial() && excludedNode.canAcceptInput(inputType)) {
                log.debug("No other step found, allowing return to the same step {} for input type {}",
                         excludeStepId, inputType.getSimpleName());
                return excludeStepId;
            }
        }
        
        log.warn("No step found that can accept input type {} (excluding {}). Available steps:", 
                inputType.getName(), excludeStepId);
        for (StepNode node : graph.nodes().values()) {
            if (!node.isInitial()) {
                Class<?> expectedType = node.executor() != null ? node.executor().getInputType() : null;
                log.warn("  Step {}: expects {}", node.id(), 
                        expectedType != null ? expectedType.getName() : "null");
            }
        }
        
        return null;
    }
}
package ai.driftkit.workflow.engine.graph;

import ai.driftkit.workflow.engine.core.WorkflowAnalyzer;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Immutable representation of a workflow as a directed acyclic graph (DAG).
 * This is the compiled form of a workflow definition that can be executed by the engine.
 * 
 * @param <T> The type of input data for the workflow
 * @param <R> The type of the final result of the workflow
 */
@Slf4j
@Builder
public record WorkflowGraph<T, R>(
    String id,
    String version,
    Class<T> inputType,
    Class<R> outputType,
    Map<String, StepNode> nodes,
    Map<String, List<Edge>> edges,
    String initialStepId,
    Object workflowInstance,
    Map<String, WorkflowAnalyzer.AsyncStepMetadata> asyncStepMetadata
) {
    /**
     * Validates the WorkflowGraph structure.
     */
    public WorkflowGraph {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Workflow ID cannot be null or blank");
        }
        if (version == null || version.isBlank()) {
            version = "1.0";
        }
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("Workflow must have at least one node");
        }
        if (edges == null) {
            edges = Collections.emptyMap();
        }
        if (asyncStepMetadata == null) {
            asyncStepMetadata = Collections.emptyMap();
        }
        
        // Make collections immutable
        nodes = Collections.unmodifiableMap(new HashMap<>(nodes));
        edges = Collections.unmodifiableMap(
            edges.entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    e -> Collections.unmodifiableList(new ArrayList<>(e.getValue()))
                ))
        );
        asyncStepMetadata = Collections.unmodifiableMap(new HashMap<>(asyncStepMetadata));
        
        // Validate initial step
        if (initialStepId == null || initialStepId.isBlank()) {
            // Try to find a step marked as initial
            initialStepId = nodes.values().stream()
                .filter(StepNode::isInitial)
                .map(StepNode::id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                    "No initial step specified and none marked as initial"
                ));
        }
        
        if (!nodes.containsKey(initialStepId)) {
            throw new IllegalArgumentException("Initial step not found: " + initialStepId);
        }
        
        // Validate graph structure
        validateGraph(nodes, edges);
    }
    
    /**
     * Gets all outgoing edges from a specific step.
     */
    public List<Edge> getOutgoingEdges(String stepId) {
        return edges.getOrDefault(stepId, Collections.emptyList());
    }
    
    /**
     * Gets all incoming edges to a specific step.
     */
    public List<Edge> getIncomingEdges(String stepId) {
        return edges.values().stream()
            .flatMap(List::stream)
            .filter(edge -> edge.toStepId().equals(stepId))
            .collect(Collectors.toList());
    }
    
    /**
     * Gets a step node by ID.
     */
    public Optional<StepNode> getNode(String stepId) {
        return Optional.ofNullable(nodes.get(stepId));
    }
    
    /**
     * Finds all terminal nodes (nodes with no outgoing edges).
     */
    public Set<String> getTerminalNodes() {
        Set<String> terminals = new HashSet<>(nodes.keySet());
        edges.values().stream()
            .flatMap(List::stream)
            .map(Edge::fromStepId)
            .forEach(terminals::remove);
        return terminals;
    }
    
    /**
     * Checks if the graph contains cycles.
     */
    public boolean hasCycles() {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();
        
        for (String nodeId : nodes.keySet()) {
            if (hasCyclesHelper(nodeId, visited, recursionStack)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean hasCyclesHelper(String nodeId, Set<String> visited, Set<String> recursionStack) {
        visited.add(nodeId);
        recursionStack.add(nodeId);
        
        List<Edge> outgoing = getOutgoingEdges(nodeId);
        for (Edge edge : outgoing) {
            if (!visited.contains(edge.toStepId())) {
                if (hasCyclesHelper(edge.toStepId(), visited, recursionStack)) {
                    return true;
                }
            } else if (recursionStack.contains(edge.toStepId())) {
                return true;
            }
        }
        
        recursionStack.remove(nodeId);
        return false;
    }
    
    /**
     * Performs a topological sort of the graph nodes.
     * 
     * @return List of node IDs in topological order
     * @throws IllegalStateException if the graph contains cycles
     */
    public List<String> topologicalSort() {
        if (hasCycles()) {
            throw new IllegalStateException("Cannot perform topological sort on graph with cycles");
        }
        
        Map<String, Integer> inDegree = new HashMap<>();
        nodes.keySet().forEach(id -> inDegree.put(id, 0));
        
        // Calculate in-degrees
        edges.values().stream()
            .flatMap(List::stream)
            .forEach(edge -> inDegree.merge(edge.toStepId(), 1, Integer::sum));
        
        // Find all nodes with no incoming edges
        Queue<String> queue = new LinkedList<>();
        inDegree.entrySet().stream()
            .filter(e -> e.getValue() == 0)
            .map(Map.Entry::getKey)
            .forEach(queue::offer);
        
        List<String> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            sorted.add(current);
            
            // Reduce in-degree for all neighbors
            getOutgoingEdges(current).forEach(edge -> {
                int newDegree = inDegree.merge(edge.toStepId(), -1, Integer::sum);
                if (newDegree == 0) {
                    queue.offer(edge.toStepId());
                }
            });
        }
        
        return sorted;
    }
    
    /**
     * Validates the graph structure.
     */
    private static void validateGraph(Map<String, StepNode> nodes, Map<String, List<Edge>> edges) {
        // Check for orphaned edges
        for (Map.Entry<String, List<Edge>> entry : edges.entrySet()) {
            String fromId = entry.getKey();
            if (!nodes.containsKey(fromId)) {
                throw new IllegalArgumentException("Edge references non-existent source node: " + fromId);
            }
            
            for (Edge edge : entry.getValue()) {
                if (!nodes.containsKey(edge.toStepId())) {
                    throw new IllegalArgumentException(
                        "Edge references non-existent target node: " + edge.toStepId()
                    );
                }
            }
        }
        
        // Warn about unreachable nodes (except initial)
        Set<String> reachable = new HashSet<>();
        Queue<String> toVisit = new LinkedList<>();
        
        // Find initial nodes
        nodes.values().stream()
            .filter(StepNode::isInitial)
            .map(StepNode::id)
            .forEach(id -> {
                reachable.add(id);
                toVisit.offer(id);
            });
        
        // If no explicit initial nodes, assume nodes with no incoming edges
        if (reachable.isEmpty()) {
            Set<String> hasIncoming = edges.values().stream()
                .flatMap(List::stream)
                .map(Edge::toStepId)
                .collect(Collectors.toSet());
            
            nodes.keySet().stream()
                .filter(id -> !hasIncoming.contains(id))
                .forEach(id -> {
                    reachable.add(id);
                    toVisit.offer(id);
                });
        }
        
        // Traverse the graph
        while (!toVisit.isEmpty()) {
            String current = toVisit.poll();
            List<Edge> outgoing = edges.getOrDefault(current, Collections.emptyList());
            
            for (Edge edge : outgoing) {
                if (!reachable.contains(edge.toStepId())) {
                    reachable.add(edge.toStepId());
                    toVisit.offer(edge.toStepId());
                }
            }
        }
        
        // Check for unreachable nodes
        Set<String> unreachable = new HashSet<>(nodes.keySet());
        unreachable.removeAll(reachable);
        
        if (!unreachable.isEmpty()) {
            log.warn("Workflow graph contains unreachable nodes: {}", unreachable);
        }
    }
    
    /**
     * Creates a string representation suitable for debugging.
     */
    @Override
    public String toString() {
        return "WorkflowGraph{" +
               "id='" + id + '\'' +
               ", version='" + version + '\'' +
               ", nodes=" + nodes.size() +
               ", edges=" + edges.values().stream().mapToInt(List::size).sum() +
               ", initial='" + initialStepId + '\'' +
               '}';
    }
}
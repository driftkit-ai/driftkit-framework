package ai.driftkit.workflows.core.service;

import ai.driftkit.workflows.core.domain.MethodInfo;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class representing the workflow graph of methods.
 */
@Data
@NoArgsConstructor
public class WorkflowGraph {
    private Class<?> workflowClass; // The workflow class or interface
    // Graph nodes: method name -> MethodInfo
    private Map<String, MethodInfo> methods = new HashMap<>();
    // Graph edges: from method -> list of methods it connects to
    private Map<String, List<String>> adjacencyList = new HashMap<>();

    /**
     * Adds a method to the graph.
     */
    public void addMethod(MethodInfo methodInfo) {
        methods.put(methodInfo.getMethodName(), methodInfo);
    }

    /**
     * Adds an edge between two methods in the graph.
     */
    public void addEdge(String fromMethod, String toMethod) {
        adjacencyList.computeIfAbsent(fromMethod, k -> new ArrayList<>())
                .add(toMethod);
    }

    /**
     * Retrieves the MethodInfo for a given method name.
     */
    public MethodInfo getMethodInfo(String methodName) {
        return methods.get(methodName);
    }
}

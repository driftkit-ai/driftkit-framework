package ai.driftkit.workflow.engine.builder;

import ai.driftkit.workflow.engine.core.WorkflowContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Builder for multi-way branching (on/is/otherwise pattern).
 */
public class OnBuilder<T, R, V> {
    private final WorkflowBuilder<T, R> parentBuilder;
    private final Function<WorkflowContext, V> selector;
    private final Map<V, Consumer<WorkflowBuilder<?, ?>>> cases = new LinkedHashMap<>();
    private Consumer<WorkflowBuilder<?, ?>> otherwiseCase;
    
    OnBuilder(WorkflowBuilder<T, R> parentBuilder, Function<WorkflowContext, V> selector) {
        this.parentBuilder = parentBuilder;
        this.selector = selector;
    }
    
    public OnBuilder<T, R, V> is(V value, Consumer<WorkflowBuilder<?, ?>> caseFlow) {
        if (value == null) {
            throw new IllegalArgumentException("Case value cannot be null");
        }
        if (caseFlow == null) {
            throw new IllegalArgumentException("Case flow cannot be null");
        }
        if (cases.containsKey(value)) {
            throw new IllegalStateException("Duplicate case value: " + value);
        }
        cases.put(value, caseFlow);
        return this;
    }
    
    public WorkflowBuilder<T, R> otherwise(Consumer<WorkflowBuilder<?, ?>> otherwiseFlow) {
        if (otherwiseFlow == null) {
            throw new IllegalArgumentException("Otherwise flow cannot be null");
        }
        if (cases.isEmpty()) {
            throw new IllegalStateException("Must define at least one 'is' case before 'otherwise'");
        }
        this.otherwiseCase = otherwiseFlow;
        // Add multi-branch step to parent builder
        parentBuilder.addBuildStep(new WorkflowBuilder.MultiBranchStep<>(selector, cases, otherwiseCase));
        return parentBuilder;
    }
    
    Map<V, Consumer<WorkflowBuilder<?, ?>>> getCases() {
        return cases;
    }
    
    Consumer<WorkflowBuilder<?, ?>> getOtherwiseCase() {
        return otherwiseCase;
    }
    
    Function<WorkflowContext, V> getSelector() {
        return selector;
    }
}
package ai.driftkit.workflows.core.domain;

import ai.driftkit.workflows.core.domain.OnInvocationsLimit;
import ai.driftkit.workflows.core.domain.RetryPolicy;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.lang.reflect.Type;
import java.util.List;

/**
 * Class to store information about methods.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MethodInfo {
    private String methodName;                // Method name
    private List<Type> inputEvents;           // Input events (excluding WorkflowContext)
    private List<Type> outputEvents;          // Output events (return types)
    private RetryPolicy retryPolicy; // Retry policy for the method
    private String description;               // Description from @StepInfo
    private String category;                  // Category from @StepInfo
    private String conditionExpression;       // Condition expression
    private String trueStep;                  // Next step if condition is true
    private String falseStep;                 // Next step if condition is false
    // Fields for @LLMRequest
    private String prompt;                    // Prompt template
    private String modelName;                 // Model name
    private String nextStep;                  // Default next step
    // Fields for @InlineStep and @FinalStep
    private String expression;                // Expression to evaluate

    private List<String> parameterNames;      // Parameter names
    private List<Type> allParamTypes;         // All parameter types (including WorkflowContext)
    private boolean isAbstract;               // Indicates if the method is abstract
    private boolean isFinal;                  // Indicates if the method is final

    private int invocationsLimit;
    private OnInvocationsLimit stepOnInvocationsLimit;
}

package ai.driftkit.workflows.core.service;

import ai.driftkit.workflows.core.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * WorkflowAnalyzer is responsible for building and executing workflows
 * based on annotations and method signatures.
 */
@Slf4j
public class WorkflowAnalyzer {
    public static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Builds the executable workflow graph by analyzing the specified Workflow class.
     */
    public static ExecutableWorkflowGraph buildExecutableWorkflowGraph(Object instance) {
        Class<?> workflowClass = instance.getClass();
        ExecutableWorkflowGraph graph = new ExecutableWorkflowGraph();
        graph.setWorkflowClass(workflowClass); // Store the workflow class

        Method[] methods = workflowClass.getMethods();

        List<MethodInfo> stepMethods = new ArrayList<>();

        // Collect information about methods to include in the workflow
        for (Method method : methods) {
            boolean isWorkflowMethod = false;

            Type returnType = method.getGenericReturnType();
            boolean isStopEvent = isAssignableFrom(returnType, StopEvent.class);

            // Check if method is annotated with LLMRequest, InlineStep, or Step
            if (method.isAnnotationPresent(LLMRequest.class)
                    || method.isAnnotationPresent(InlineStep.class)
                    || method.isAnnotationPresent(FinalStep.class)
                    || method.isAnnotationPresent(Step.class)) {
                isWorkflowMethod = true;
            } else {
                if (isAssignableFrom(returnType, DataEvent.class)
                        || isStopEvent) {
                    isWorkflowMethod = true;
                }
            }

            if (isWorkflowMethod) {
                // Proceed to collect method information

                // Get annotations
                LLMRequest llmRequestAnnotation = method.getAnnotation(LLMRequest.class);
                InlineStep inlineStepAnnotation = method.getAnnotation(InlineStep.class);
                FinalStep finalStepAnnotation = method.getAnnotation(FinalStep.class);
                Step stepAnnotation = method.getAnnotation(Step.class);

                // Get RetryPolicy
                RetryPolicy retryPolicy = (stepAnnotation != null)
                        ? stepAnnotation.retryPolicy()
                        : new RetryPolicy() {
                    public int delay() {
                        return 5;
                    }

                    public int maximumAttempts() {
                        return 10;
                    }

                    public Class<? extends Annotation> annotationType() {
                        return RetryPolicy.class;
                    }
                };

                // Collect all parameter types (including WorkflowContext)
                Type[] paramTypes = method.getGenericParameterTypes();
                List<Type> allParamTypes = Arrays.asList(paramTypes);

                // Collect input events (excluding WorkflowContext)
                List<Type> inputEvents = Arrays.stream(paramTypes)
                        .filter(type -> !isWorkflowContext(type))
                        .collect(Collectors.toList());

                // Get parameter names
                Parameter[] parameters = method.getParameters();
                List<String> parameterNames = new ArrayList<>();
                for (Parameter parameter : parameters) {
                    parameterNames.add(parameter.getName());
                }

                // Get output events
                List<Type> outputEvents = new ArrayList<>();
                outputEvents.add(returnType);

                // Determine the step name
                String stepName = method.getName();

                // Process additional annotations
                String description = "";
                String category = "";
                String conditionExpression = "";
                String prompt = "";
                String modelName = "";
                String nextStep = "";
                String trueStep = "";
                String falseStep = "";
                String expression = "";
                int invocationsLimit = 0;
                OnInvocationsLimit onInvocationsLimit = OnInvocationsLimit.STOP;


                if (method.isAnnotationPresent(StepInfo.class)) {
                    StepInfo stepInfo = method.getAnnotation(StepInfo.class);
                    description = stepInfo.description();
                    category = stepInfo.category();
                }

                if (llmRequestAnnotation != null) {
                    prompt = llmRequestAnnotation.prompt();
                    modelName = llmRequestAnnotation.modelName();
                    nextStep = llmRequestAnnotation.nextStep();
                    conditionExpression = llmRequestAnnotation.condition();
                    trueStep = llmRequestAnnotation.trueStep();
                    falseStep = llmRequestAnnotation.falseStep();
                }

                if (inlineStepAnnotation != null) {
                    expression = inlineStepAnnotation.expression();
                    nextStep = inlineStepAnnotation.nextStep();
                    conditionExpression = inlineStepAnnotation.condition();
                    trueStep = inlineStepAnnotation.trueStep();
                    falseStep = inlineStepAnnotation.falseStep();
                    invocationsLimit = inlineStepAnnotation.invocationLimit();
                    onInvocationsLimit = inlineStepAnnotation.onInvocationsLimit();
                }

                if (finalStepAnnotation != null) {
                    expression = finalStepAnnotation.expression();
                    invocationsLimit = finalStepAnnotation.invocationLimit();
                }

                if (stepAnnotation != null && stepAnnotation.name() != null && !stepAnnotation.name().isEmpty()) {
                    stepName = stepAnnotation.name();
                    invocationsLimit = stepAnnotation.invocationLimit();
                    onInvocationsLimit = stepAnnotation.onInvocationsLimit();
                    nextStep = stepAnnotation.nextStep();
                }

                // Determine if the method is abstract
                boolean isAbstractMethod = Modifier.isAbstract(method.getModifiers());

                // Create MethodInfo object
                MethodInfo methodInfo = new MethodInfo(
                        stepName,
                        inputEvents,
                        outputEvents,
                        retryPolicy,
                        description,
                        category,
                        conditionExpression,
                        trueStep,
                        falseStep,
                        prompt,
                        modelName,
                        nextStep,
                        expression,
                        parameterNames,
                        allParamTypes,
                        isAbstractMethod, // Include abstract flag
                        finalStepAnnotation != null || isStopEvent,
                        invocationsLimit == 0 ? 5 : invocationsLimit,
                        onInvocationsLimit
                );

                stepMethods.add(methodInfo);
                graph.addMethod(methodInfo);
            }
        }

        // Establish connections between methods based on specified next steps
        for (MethodInfo method : stepMethods) {
            // If the method specifies next steps, add edges accordingly
            if (method.getTrueStep() != null && !method.getTrueStep().isEmpty()) {
                graph.addEdge(method.getMethodName(), method.getTrueStep());
            }
            if (method.getFalseStep() != null && !method.getFalseStep().isEmpty()) {
                graph.addEdge(method.getMethodName(), method.getFalseStep());
            }
            if (method.getNextStep() != null && !method.getNextStep().isEmpty()) {
                graph.addEdge(method.getMethodName(), method.getNextStep());
            }
        }

        ExecutableWorkflowGraph.register(workflowClass, instance, graph);
        return graph;
    }

    /**
     * Checks if the type is WorkflowContext.
     */
    private static boolean isWorkflowContext(Type type) {
        if (type instanceof Class<?>) {
            return WorkflowContext.class
                    .isAssignableFrom((Class<?>) type);
        } else if (type instanceof ParameterizedType) {
            Type rawType = ((ParameterizedType) type).getRawType();
            if (rawType instanceof Class<?>) {
                return WorkflowContext.class
                        .isAssignableFrom((Class<?>) rawType);
            }
        }
        return false;
    }

    private static boolean isAssignableFrom(Type type, Class<?> clazz) {
        if (type instanceof Class<?>) {
            return clazz.isAssignableFrom((Class<?>) type);
        } else if (type instanceof ParameterizedType) {
            Type rawType = ((ParameterizedType) type).getRawType();
            if (rawType instanceof Class<?>) {
                return clazz.isAssignableFrom((Class<?>) rawType);
            }
        }
        return false;
    }

}
package ai.driftkit.workflows.core.service;

import ai.driftkit.common.domain.client.*;
import ai.driftkit.common.domain.client.ResponseFormat;
import ai.driftkit.common.utils.ModelUtils;
import ai.driftkit.workflows.core.domain.*;
import ai.driftkit.common.utils.JsonUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.jexl2.Expression;
import org.apache.commons.jexl2.JexlContext;
import org.apache.commons.jexl2.JexlEngine;
import org.apache.commons.jexl2.MapContext;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Class extending WorkflowGraph to execute the workflow.
 */
@Slf4j
@Data
@NoArgsConstructor
public class ExecutableWorkflowGraph extends WorkflowGraph {

    private static final int MAX_DEPTH = 1000;
    private static Map<Class<?>, WorkflowGraphInstance> workflowGraphMap;

    static {
        workflowGraphMap = new ConcurrentHashMap<>();
    }

    static void register(Class<?> workflowCls, Object instance, ExecutableWorkflowGraph graph) {
        workflowGraphMap.put(workflowCls, new WorkflowGraphInstance(graph, instance));
    }

    public <T> StopEvent<T> execute(Object workflowInstance, StartEvent startEvent, WorkflowContext workflowContext) throws Exception {
        return execute(workflowInstance, startEvent, workflowContext, null);
    }

    /**
     * Executes the workflow starting from StartEvent.
     */
    public <T> StopEvent<T> execute(Object workflowInstance, StartEvent startEvent,
                                    WorkflowContext workflowContext,
                                    ModelClient modelClient) throws Exception {
        List<MethodInfo> startingMethods = getStartMethodsInfo();

        if (startingMethods.isEmpty()) {
            throw new IllegalStateException(
                    "No starting step found (methods accepting StartEvent)");
        }

        MethodInfo startingMethodInfo = startingMethods.get(0);

        return executeStep(workflowInstance, startingMethodInfo, startEvent,
                workflowContext, 0, modelClient);
    }

    private <T> StopEvent<T> executeStep(Object workflowInstance, MethodInfo methodInfo,
                              WorkflowEvent currentEvent,
                              WorkflowContext workflowContext,
                              int depth, ModelClient modelClient) throws Exception {
        if (depth > MAX_DEPTH) {
            throw new StackOverflowError("Maximum execution depth exceeded");
        }

        String methodName = methodInfo.getMethodName();

        int counter = workflowContext.getCounter(WorkflowContext.getMethodInvocationsCounterName(methodName));

        if (methodInfo.getInvocationsLimit() <= counter) {
            switch (methodInfo.getStepOnInvocationsLimit()) {
                case ERROR -> {
                    throw new StackOverflowError("Maximum invocations limit exceeded [%s] for method [%s]".formatted(counter, methodName));
                }
                case STOP -> {
                    String finalResult = ((DataEvent) currentEvent).getResult().toString();

                    return StopEvent.ofString(finalResult);
                }
                case CONTINUE -> {
                    String nextStepName = methodInfo.getNextStep();
                    MethodInfo nextMethodInfo = getMethodInfo(nextStepName);
                    if (nextMethodInfo == null) {
                        throw new IllegalStateException("No method found for step name: " + nextStepName);
                    }

                    return executeStep(workflowInstance, nextMethodInfo, currentEvent,
                            workflowContext, depth + 1, modelClient);
                }
            }
        }

        workflowContext.onStepInvocation(methodName, currentEvent);

        // Build arguments for the method
        Object[] args = buildMethodArguments(methodInfo,
                currentEvent, workflowContext);

        try {
            Object returnValue;

            if (methodInfo.getPrompt() != null
                    && !methodInfo.getPrompt().isEmpty()) {
                // Handling methods annotated with @LLMRequest
                if (modelClient == null) {
                    throw new IllegalArgumentException("ModelClient is required for methods annotated with @LLMRequest");
                }
                returnValue = invokeLLMRequest(methodInfo, args,
                        workflowContext, modelClient);
            } else if (methodInfo.getExpression() != null
                    && !methodInfo.getExpression().isEmpty()) {
                // Handling methods annotated with @InlineStep and @FinalStep
                returnValue = executeInlineStep(methodInfo, args, workflowContext);
            } else {
                // Invoke the method on the workflow instance
                if (methodInfo.isAbstract()) {
                    if (methodInfo.isFinal()) {
                        return StopEvent.ofString(((DataEvent) currentEvent).getResult().toString());
                    } else {
                        throw new UnsupportedOperationException("Cannot invoke abstract method: " + methodInfo.getMethodName());
                    }
                }
                if (workflowInstance == null) {
                    throw new IllegalArgumentException("Workflow instance is required for invoking methods");
                }

                Method currentMethod = findMethod(workflowInstance.getClass(), methodInfo);

                if (currentMethod == null) {
                    currentMethod = findMethod(workflowInstance.getClass(), methodInfo);
                    throw new NoSuchMethodException("Method not found: " + methodName);
                }

                currentMethod.setAccessible(true);

                returnValue = invokeWithRetry(workflowInstance, currentMethod, args,
                        methodInfo.getRetryPolicy());
            }

            if (returnValue == null) {
                throw new IllegalStateException("Method "
                        + methodInfo.getMethodName() + " returned null");
            }

            if (returnValue instanceof StopEvent) {
                return (StopEvent<T>) returnValue;
            } else if (returnValue instanceof ExternalEvent externalEvent) {
                WorkflowGraphInstance relatedWorkflow = workflowGraphMap.get(externalEvent.getWorkflowCls());

                if (relatedWorkflow == null) {
                    throw new IllegalStateException("External workflow " + externalEvent.getWorkflowCls().getSimpleName() + " is unregistered, workflow step failed");
                }

                StopEvent<?> executed = relatedWorkflow.getGraph().execute(relatedWorkflow.getInstance(), externalEvent.getStartEvent(), workflowContext);

                MethodInfo nextMethodInfo = getMethodInfo(externalEvent.getNextStepName());

                DataEvent event = new DataEvent(executed.get(), externalEvent.getNextStepName());

                // Proceed to the next step
                return executeStep(workflowInstance, nextMethodInfo, event,
                        workflowContext, depth + 1, modelClient);
            } else if (returnValue instanceof DataEvent) {
                DataEvent<?> dataEvent = (DataEvent<?>) returnValue;

                // Determine the next step based on condition
                String nextStepName = determineNextStep(methodInfo, args, dataEvent, workflowContext);

                if (nextStepName == null || nextStepName.isEmpty()) {
                    return StopEvent.ofString(dataEvent.getResult().toString());
                }

                MethodInfo nextMethodInfo = getMethodInfo(nextStepName);
                if (nextMethodInfo == null) {
                    throw new IllegalStateException("No method found for step name: "
                            + nextStepName);
                }

                // Prepare the event for the next step
                WorkflowEvent nextEvent;
                if (methodInfo.getConditionExpression() != null && !methodInfo.getConditionExpression().isEmpty()) {
                    // If there was a condition, create a CombinedEvent
                    nextEvent = new CombinedEvent(dataEvent.getResult(), lastConditionResult);
                } else {
                    // No condition; pass the data result directly
                    nextEvent = dataEvent;
                }

                // Proceed to the next step
                return executeStep(workflowInstance, nextMethodInfo, nextEvent,
                        workflowContext, depth + 1, modelClient);
            } else {
                throw new IllegalStateException(
                        "Unexpected return type from method: "
                                + methodInfo.getMethodName());
            }
        } catch (Exception e) {
            log.error("Error executing method {}: {}",
                    methodInfo.getMethodName(), e.getMessage());
            throw e;
        }
    }

    private boolean lastConditionResult = false; // Store the last condition result

    private String determineNextStep(MethodInfo methodInfo, Object[] args, Object dataResult, WorkflowContext context) throws Exception {
        if (methodInfo.getConditionExpression() != null && !methodInfo.getConditionExpression().isEmpty()) {
            // Prepare variables for condition evaluation
            Map<String, Object> variables = prepareVariables(methodInfo, args, dataResult, context);

            // Evaluate condition
            boolean conditionResult = evaluateConditionExpression(methodInfo.getConditionExpression(), variables);
            lastConditionResult = conditionResult;

            if (conditionResult) {
                return methodInfo.getTrueStep();
            } else {
                return methodInfo.getFalseStep();
            }
        } else if (dataResult instanceof DataEvent && StringUtils.isNotBlank(((DataEvent<?>) dataResult).getNextStepName())) {
            String nextStepName = ((DataEvent<?>) dataResult).getNextStepName();

            if (nextStepName.equals(methodInfo.getMethodName()) && methodInfo.isFinal()) {
                return null;
            }

            return nextStepName;
        } else if (methodInfo.getNextStep() != null && !methodInfo.getNextStep().isEmpty()) {
            // Default next step specified
            return methodInfo.getNextStep();
        } else {
            // Attempt to find next step based on the workflow graph
            List<String> nextSteps = getAdjacencyList().get(methodInfo.getMethodName());
            if (nextSteps != null && !nextSteps.isEmpty()) {
                return nextSteps.get(0); // Proceed to the next connected step
            } else {
                return null;
            }
        }
    }

    private Map<String, Object> prepareVariables(MethodInfo methodInfo, Object[] args, Object dataResult, WorkflowContext context) throws JsonProcessingException {
        Map<String, Object> variables = new HashMap<>();

        // Add variables from method arguments
        List<String> parameterNames = methodInfo.getParameterNames();
        for (int i = 0; i < parameterNames.size(); i++) {
            String paramName = parameterNames.get(i);
            Object argValue = args[i];
            if (argValue instanceof WorkflowContext) {
                continue;
            }
            variables.put(paramName, argValue);
        }

        // Add dataResult variable
        variables.put("dataResult", dataResult);

        // If dataResult is a DataEvent, get its result
        Object result;
        if (dataResult instanceof DataEvent) {
            result = ((DataEvent<?>) dataResult).getResult();
        } else {
            result = dataResult;
        }
        variables.put("result", result);

        // If result is JsonNode, make it accessible in expression
        if (result instanceof JsonNode) {
            Map<String, Object> response = ModelUtils.OBJECT_MAPPER.convertValue(result, Map.class);
            variables.put("response", response);
        }

        // Add variables from context if needed
        // variables.putAll(context.getVariables());

        return variables;
    }

    private boolean evaluateConditionExpression(String expression, Map<String, Object> variables) throws Exception {
        JexlEngine jexl = new JexlEngine();
        Expression jexlExpression = jexl.createExpression(expression);
        JexlContext jexlContext = new MapContext(variables);
        Object result = jexlExpression.evaluate(jexlContext);
        if (result instanceof Boolean) {
            return (Boolean) result;
        } else {
            throw new IllegalArgumentException("Condition expression did not return a boolean value: " + expression);
        }
    }

    private Object invokeLLMRequest(MethodInfo methodInfo,
                                    Object[] args,
                                    WorkflowContext workflowContext,
                                    ModelClient modelClient)
            throws Exception {
        // Prepare the prompt with placeholder substitution
        String prompt = methodInfo.getPrompt();
        prompt = substituteVariables(prompt, methodInfo, args, workflowContext);

        String modelName = methodInfo.getModelName();

        // Extract configuration from arguments if present
        Map<String, Object> config = extractConfigFromArgs(args);

        // Send request to the model using the modelClient
        ModelTextResponse modelTextResponse = sendLLMRequest(prompt, modelName, config, modelClient);

        Object response;

        if (JsonUtils.isJSON(modelTextResponse.getResponse())) {
            response = modelTextResponse.getResponseJson();
        } else {
            response = modelTextResponse.getResponse();
        }

        return new DataEvent<>(response, methodInfo.getNextStep());
    }

    private Object executeInlineStep(MethodInfo methodInfo,
                                     Object[] args,
                                     WorkflowContext workflowContext)
            throws Exception {
        // Evaluate the expression
        String expression = methodInfo.getExpression();

        if ("{event}".equals(expression)) {
            return args[0];
        }

        Object result = evaluateExpression(expression, methodInfo, args, workflowContext);

        return new DataEvent<>(result, methodInfo.getNextStep());
    }

    private Object evaluateExpression(String expression,
                                      MethodInfo methodInfo,
                                      Object[] args,
                                      WorkflowContext context)
            throws Exception {
        // Substitute variables in the expression
        String substitutedExpression = substituteVariables(expression, methodInfo, args, context);

        // For simplicity, return the substituted expression
        return substitutedExpression;
    }

    private ModelTextResponse sendLLMRequest(String prompt, String modelName, Map<String, Object> config, ModelClient modelClient) {
        // Use the model client to send the request
        List<ModelImageResponse.ModelContentMessage> messages = new ArrayList<>();
        messages.add(ModelImageResponse.ModelContentMessage.create(Role.user, prompt));

        ModelTextRequest request = ModelTextRequest.builder()
                .temperature((Double) config.getOrDefault("temperature", 0.7))
                .model(modelName)
                .messages(messages)
                .responseFormat(prompt.toLowerCase().contains("json") ? ResponseFormat.jsonObject() : null)
                .build();

        return modelClient.textToText(request);
    }

    private String substituteVariables(String text, MethodInfo methodInfo,
                                       Object[] args,
                                       WorkflowContext context)
            throws Exception {
        // Map parameter names to their values
        List<String> parameterNames = methodInfo.getParameterNames();
        Map<String, Object> variables = new HashMap<>();

        for (int i = 0; i < parameterNames.size(); i++) {
            String paramName = parameterNames.get(i);
            Object argValue = args[i];

            // Exclude WorkflowContext from variables
            if (argValue instanceof WorkflowContext) {
                continue;
            }

            variables.put(paramName, argValue);
        }

        // Substitute placeholders in the text
        Pattern pattern = Pattern.compile("\\{(\\w+(?:\\.\\w+)*)\\}");
        Matcher matcher = pattern.matcher(text);

        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            Object value = resolvePlaceholderValue(placeholder, variables,
                    context);
            matcher.appendReplacement(result,
                    Matcher.quoteReplacement(
                            value != null ? value.toString() : ""));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    private Object resolvePlaceholderValue(String placeholder,
                                           Map<String, Object> variables,
                                           WorkflowContext context)
            throws Exception {
        String[] parts = placeholder.split("\\.");
        Object value = null;

        if (variables.containsKey(parts[0])) {
            value = variables.get(parts[0]);
        } else if (context.get(parts[0]) != null) {
            value = context.get(parts[0]);
        }

        for (int i = 1; i < parts.length; i++) {
            if (value == null) {
                return null;
            }
            String propertyName = parts[i];
            value = getPropertyValue(value, propertyName);
        }

        return value;
    }

    private Object getPropertyValue(Object obj, String propertyName)
            throws Exception {
        if (obj instanceof Map) {
            return ((Map<?, ?>) obj).get(propertyName);
        } else if (obj instanceof JsonNode) {
            return ((JsonNode) obj).get(propertyName);
        } else {
            Class<?> clazz = obj.getClass();
            try {
                Field field = clazz.getDeclaredField(propertyName);
                field.setAccessible(true);
                return field.get(obj);
            } catch (NoSuchFieldException e) {
                // Try to get the value via getter
                String methodName = "get"
                        + Character.toUpperCase(propertyName.charAt(0))
                        + propertyName.substring(1);
                Method method = clazz.getMethod(methodName);
                return method.invoke(obj);
            }
        }
    }

    private Map<String, Object> extractConfigFromArgs(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof Map) {
                return (Map<String, Object>) arg;
            }
        }
        return new HashMap<>();
    }

    private Object[] buildMethodArguments(MethodInfo methodInfo,
                                          Object event,
                                          WorkflowContext context)
            throws Exception {
        List<Type> paramTypes = methodInfo.getAllParamTypes();
        List<String> parameterNames = methodInfo.getParameterNames();

        Object[] args = new Object[paramTypes.size()];
        int index = 0;

        for (Type paramType : paramTypes) {
            Class<?> paramClass = getClassFromType(paramType);

            if (paramClass.isAssignableFrom(event.getClass())) {
                args[index++] = event;
            } else if (paramClass.isAssignableFrom(WorkflowContext.class)) {
                args[index++] = context;
            } else if (paramClass.isAssignableFrom(Map.class)) {
                args[index++] = new HashMap<String, Object>();
            } else if (paramClass == String.class && event instanceof DataEvent) {
                args[index++] = ((DataEvent<?>) event).getResult();
            } else {
                throw new IllegalArgumentException("Cannot match parameter of type %s".formatted(paramClass.getName()));
            }
        }
        return args;
    }

    private Class<?> getClassFromType(Type type)
            throws ClassNotFoundException {
        if (type instanceof Class<?>) {
            return (Class<?>) type;
        } else if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;
            Type rawType = pt.getRawType();
            if (rawType instanceof Class<?>) {
                return (Class<?>) rawType;
            }
        }
        throw new ClassNotFoundException("Cannot determine class from type: %s".formatted(type.getTypeName()));
    }

    private Method findMethod(Class<?> clazz, MethodInfo methodInfo) throws ClassNotFoundException {
        if (methodInfo.isAbstract()) {
            // No need to find abstract methods in the instance; they are handled internally
            return null;
        }

        Class<?>[] methodParamClasses = getParameterClasses(methodInfo.getAllParamTypes());

        for (Method method : clazz.getMethods()) {
            if (method.getName().equals(methodInfo.getMethodName())
                    && Arrays.equals(method.getParameterTypes(), methodParamClasses)) {
                return method;
            }
        }
        return null;
    }

    private Class<?>[] getParameterClasses(List<Type> types) throws ClassNotFoundException {
        List<Class<?>> classes = new ArrayList<>();

        for (Type type : types) {
            classes.add(getClassFromType(type));
        }

        return classes.toArray(new Class<?>[0]);
    }

    private Object invokeWithRetry(Object workflowInstance, Method method,
                                   Object[] args,
                                   RetryPolicy retryPolicy)
            throws Exception {
        int attempts = 0;
        int maxAttempts = retryPolicy.maximumAttempts();
        int delay = retryPolicy.delay();

        while (true) {
            try {
                return method.invoke(workflowInstance, args);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                attempts++;
                if (attempts >= maxAttempts) {
                    throw (cause != null) ? new Exception(cause) : e;
                }
                log.error(
                        "Method {} failed with exception: {}. Retrying {}/{}",
                        method.getName(), cause.getMessage(), attempts,
                        maxAttempts, e);
                Thread.sleep(delay * 1000L);
            }
        }
    }

    private List<MethodInfo> getStartMethodsInfo() {
        return getMethods().values().stream()
                .filter(mi -> mi.getInputEvents().stream()
                        .anyMatch(e -> isAssignableFrom(e,
                                StartEvent.class)))
                .collect(Collectors.toList());
    }

    private boolean isAssignableFrom(Type type, Class<?> clazz) {
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


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowGraphInstance {
        ExecutableWorkflowGraph graph;
        Object instance;
    }
}

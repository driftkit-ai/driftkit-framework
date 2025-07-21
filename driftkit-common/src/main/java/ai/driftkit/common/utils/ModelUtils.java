package ai.driftkit.common.utils;

import ai.driftkit.common.domain.client.ModelClient.Property;
import ai.driftkit.common.domain.client.ModelClient.ResponseFormatType;
import ai.driftkit.common.domain.client.ModelClient.Tool;
import ai.driftkit.common.domain.client.ModelClient.ToolFunction;
import ai.driftkit.common.domain.client.ModelClient.ToolFunction.FunctionParameters;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModelUtils {

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Description {
        String value();
    }

    public static JsonNode parseJsonMessage(String message) throws JsonProcessingException {
        return OBJECT_MAPPER.readTree(message);
    }

    public static Tool parseFunction(Method method) {
        String functionName = method.getName();

        Description descriptionAnnotation = method.getAnnotation(Description.class);
        String description = descriptionAnnotation != null ? descriptionAnnotation.value() : "";

        ResponseFormatType returnType = mapJavaTypeToResponseFormatType(method.getReturnType());

        Map<String, Property> properties = new HashMap<>();
        List<String> required = new java.util.ArrayList<>();
        Parameter[] parameters = method.getParameters();

        for (Parameter parameter : parameters) {
            String parameterName = parameter.getName();
            ResponseFormatType parameterType = mapJavaTypeToResponseFormatType(parameter.getType());
            properties.put(parameterName, new Property(parameterType));
            required.add(parameterName);
        }

        FunctionParameters functionParameters = new FunctionParameters(
                ResponseFormatType.Object, properties, required
        );

        ToolFunction function = new ToolFunction(functionName, description, functionParameters);

        return new Tool(returnType, function);
    }

    private static ResponseFormatType mapJavaTypeToResponseFormatType(Class<?> type) {
        if (type == String.class) {
            return ResponseFormatType.String;
        } else if (type == int.class || type == Integer.class) {
            return ResponseFormatType.Integer;
        } else if (type == boolean.class || type == Boolean.class) {
            return ResponseFormatType.Boolean;
        } else if (type == double.class || type == Double.class || type == float.class || type == Float.class) {
            return ResponseFormatType.Number;
        } else if (type == List.class || type.isArray()) {
            return ResponseFormatType.Array;
        } else if (type == Map.class) {
            return ResponseFormatType.Object;
        } else {
            return ResponseFormatType.Object;
        }
    }
}

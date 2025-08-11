package ai.driftkit.workflow.engine.analyzer;

import ai.driftkit.workflow.engine.core.StepResult;
import java.lang.reflect.Type;

/**
 * Utility class for type analysis operations.
 * Contains static methods extracted from TypeAnalyzer.
 */
public final class TypeUtils {
    
    private TypeUtils() {
        // Utility class
    }
    
    /**
     * Extracts the result type from a StepResult generic type.
     * Delegates to MethodAnalyzer for the actual extraction.
     * 
     * @param type The generic type to extract from
     * @return The extracted type class
     */
    public static Class<?> extractStepResultType(Type type) {
        return MethodAnalyzer.extractStepResultType(type);
    }
    
    /**
     * Checks if a source type is compatible with a target type.
     * Delegates to TypeMatcher for the actual compatibility check.
     * 
     * @param sourceType The source type (output from previous step)
     * @param targetType The target type (input of next step)
     * @return true if types are compatible
     */
    public static boolean isTypeCompatible(Class<?> sourceType, Class<?> targetType) {
        return TypeMatcher.isTypeCompatible(sourceType, targetType);
    }
    
    /**
     * Checks if a type represents StepResult.Finish.
     * 
     * @param rawType The raw type to check
     * @return true if the type is StepResult.Finish
     */
    public static boolean isFinishType(Type rawType) {
        if (rawType instanceof Class<?> clazz) {
            return StepResult.Finish.class.isAssignableFrom(clazz);
        }
        return false;
    }
}
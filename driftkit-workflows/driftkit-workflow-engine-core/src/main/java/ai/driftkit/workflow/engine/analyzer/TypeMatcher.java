package ai.driftkit.workflow.engine.analyzer;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Utility class for type matching and compatibility checks.
 * Centralizes the logic for determining if types are compatible
 * for workflow step connections.
 */
@Slf4j
@UtilityClass
public class TypeMatcher {
    
    private static final Map<Class<?>, Class<?>> PRIMITIVE_TO_WRAPPER = Map.of(
        boolean.class, Boolean.class,
        byte.class, Byte.class,
        char.class, Character.class,
        short.class, Short.class,
        int.class, Integer.class,
        long.class, Long.class,
        float.class, Float.class,
        double.class, Double.class
    );
    
    /**
     * Checks if a source type is compatible with a target type.
     * This handles inheritance, primitive/wrapper compatibility, and null cases.
     * 
     * @param sourceType The type being provided
     * @param targetType The type expected
     * @return true if the types are compatible
     */
    public static boolean isTypeCompatible(Class<?> sourceType, Class<?> targetType) {
        // Target accepts any type
        if (targetType == null || targetType == Object.class) {
            return true;
        }
        
        // Source type unknown
        if (sourceType == null) {
            return false;
        }
        
        // Check direct assignment compatibility
        if (targetType.isAssignableFrom(sourceType)) {
            return true;
        }
        
        // Check primitive type compatibility
        if (targetType.isPrimitive() || sourceType.isPrimitive()) {
            return isCompatiblePrimitive(sourceType, targetType);
        }
        
        return false;
    }
    
    /**
     * Checks compatibility between primitive types and their wrappers.
     */
    private static boolean isCompatiblePrimitive(Class<?> source, Class<?> target) {
        // Get wrapper classes for primitives
        Class<?> sourceWrapper = PRIMITIVE_TO_WRAPPER.getOrDefault(source, source);
        Class<?> targetWrapper = PRIMITIVE_TO_WRAPPER.getOrDefault(target, target);
        
        return targetWrapper.isAssignableFrom(sourceWrapper);
    }
    
    /**
     * Finds the most specific common superclass of a set of classes.
     * Used for determining workflow output type when multiple Finish types exist.
     * 
     * @param classes Set of classes to find common superclass for
     * @return The most specific common superclass
     */
    public static Class<?> findCommonSuperclass(Set<Class<?>> classes) {
        if (classes.isEmpty()) {
            return Object.class;
        }
        
        Iterator<Class<?>> iter = classes.iterator();
        Class<?> common = iter.next();
        
        while (iter.hasNext()) {
            Class<?> next = iter.next();
            common = findCommonSuperclass(common, next);
        }
        
        return common;
    }
    
    /**
     * Finds the most specific common superclass of two classes.
     */
    private static Class<?> findCommonSuperclass(Class<?> a, Class<?> b) {
        if (a.isAssignableFrom(b)) {
            return a;
        }
        if (b.isAssignableFrom(a)) {
            return b;
        }
        
        // Walk up the hierarchy
        Class<?> parent = a.getSuperclass();
        while (parent != null && !parent.isAssignableFrom(b)) {
            parent = parent.getSuperclass();
        }
        
        return parent != null ? parent : Object.class;
    }
}
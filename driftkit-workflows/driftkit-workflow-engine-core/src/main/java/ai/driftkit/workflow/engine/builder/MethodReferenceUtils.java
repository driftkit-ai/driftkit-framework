package ai.driftkit.workflow.engine.builder;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.function.Function;

/**
 * Utilities for working with method references.
 */
@Slf4j
public class MethodReferenceUtils {
    
    /**
     * Attempts to extract the target instance from a method reference.
     * This uses reflection to access the captured instance.
     * 
     * @param function The function that might be a method reference
     * @return The target instance or null if not a method reference or extraction failed
     */
    public static Object extractTargetInstance(Object function) {
        if (function == null) {
            return null;
        }
        
        Class<?> clazz = function.getClass();
        
        // Method references typically have synthetic classes with names containing $$Lambda$
        if (!clazz.isSynthetic() || !clazz.getName().contains("$$Lambda$")) {
            return null;
        }
        
        try {
            // Method references usually capture the target instance in a field named "arg$1"
            // This is implementation-specific but works for OpenJDK/Oracle JDK
            Field[] fields = clazz.getDeclaredFields();
            for (Field field : fields) {
                if (field.getName().startsWith("arg$")) {
                    field.setAccessible(true);
                    Object target = field.get(function);
                    if (target != null && !isPrimitive(target)) {
                        log.debug("Extracted target instance {} from method reference", target.getClass().getName());
                        return target;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract target from method reference", e);
        }
        
        return null;
    }
    
    private static boolean isPrimitive(Object obj) {
        return obj instanceof Number || obj instanceof String || obj instanceof Boolean || 
               obj instanceof Character || obj.getClass().isPrimitive();
    }
}
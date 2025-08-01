package ai.driftkit.embedding.core.util;

/**
 * Utility class for validation operations.
 */
public final class ValidationUtils {
    
    private ValidationUtils() {
        // Utility class
    }
    
    /**
     * Ensures that the given object is not null.
     * 
     * @param object the object to check
     * @param parameterName the name of the parameter (for error messages)
     * @param <T> the type of the object
     * @return the object if not null
     * @throws IllegalArgumentException if the object is null
     */
    public static <T> T ensureNotNull(T object, String parameterName) {
        if (object == null) {
            throw new IllegalArgumentException(parameterName + " cannot be null");
        }
        return object;
    }
    
    /**
     * Ensures that the given string is not null or empty.
     * 
     * @param string the string to check
     * @param parameterName the name of the parameter (for error messages)
     * @return the string if not null or empty
     * @throws IllegalArgumentException if the string is null or empty
     */
    public static String ensureNotBlank(String string, String parameterName) {
        if (string == null || string.trim().isEmpty()) {
            throw new IllegalArgumentException(parameterName + " cannot be null or empty");
        }
        return string;
    }
}
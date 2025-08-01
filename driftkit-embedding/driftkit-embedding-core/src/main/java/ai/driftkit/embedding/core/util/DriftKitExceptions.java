package ai.driftkit.embedding.core.util;

/**
 * Utility class for exception handling.
 */
public final class DriftKitExceptions {
    
    private DriftKitExceptions() {
        // Utility class
    }
    
    /**
     * Creates an IllegalArgumentException with the given message.
     * 
     * @param message the exception message
     * @return the exception
     */
    public static IllegalArgumentException illegalArgument(String message) {
        return new IllegalArgumentException(message);
    }
    
    /**
     * Creates an IllegalArgumentException with a formatted message.
     * 
     * @param format the format string
     * @param args the arguments
     * @return the exception
     */
    public static IllegalArgumentException illegalArgument(String format, Object... args) {
        return new IllegalArgumentException(String.format(format, args));
    }
    
    /**
     * Creates an IllegalArgumentException with the given message and cause.
     * 
     * @param message the exception message
     * @param cause the cause
     * @return the exception
     */
    public static IllegalArgumentException illegalArgument(String message, Throwable cause) {
        return new IllegalArgumentException(message, cause);
    }
    
    /**
     * Creates an IllegalStateException with the given message.
     * 
     * @param message the exception message
     * @return the exception
     */
    public static IllegalStateException illegalState(String message) {
        return new IllegalStateException(message);
    }
    
    /**
     * Creates a RuntimeException with the given message.
     * 
     * @param message the exception message
     * @return the exception
     */
    public static RuntimeException runtime(String message) {
        return new RuntimeException(message);
    }
    
    /**
     * Creates a RuntimeException with a formatted message.
     * 
     * @param format the format string
     * @param args the arguments
     * @return the exception
     */
    public static RuntimeException runtime(String format, Object... args) {
        return new RuntimeException(String.format(format, args));
    }
    
    /**
     * Creates a RuntimeException with the given message and cause.
     * 
     * @param message the exception message
     * @param cause the cause
     * @return the exception
     */
    public static RuntimeException runtime(String message, Throwable cause) {
        return new RuntimeException(message, cause);
    }
}
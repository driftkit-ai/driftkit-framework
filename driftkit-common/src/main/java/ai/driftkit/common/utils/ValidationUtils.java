package ai.driftkit.common.utils;

/**
 * ValidationUtils provides simple validation helper methods.
 */
public class ValidationUtils {

    /**
     * Ensures that the given object is not null.
     *
     * @param obj  The object to check.
     * @param name The name of the parameter.
     * @param <T>  The type of the object.
     * @return The object if it is not null.
     * @throws IllegalArgumentException if the object is null.
     */
    public static <T> T ensureNotNull(T obj, String name) {
        if (obj == null) {
            throw new IllegalArgumentException(name + " cannot be null");
        }
        return obj;
    }

    /**
     * Ensures that the given integer is greater than zero.
     *
     * @param value The integer value to check.
     * @param name  The name of the parameter.
     * @return The value if it is greater than zero.
     * @throws IllegalArgumentException if the value is null or not greater than zero.
     */
    public static Integer ensureGreaterThanZero(Integer value, String name) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(name + " must be greater than zero");
        }
        return value;
    }
}

package ai.driftkit.workflow.test.core;

import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * Centralized validation helper for the test framework.
 * Provides consistent validation and error messages.
 */
public class ValidationHelper {
    
    /**
     * Validates that an object is not null.
     * 
     * @param obj the object to validate
     * @param parameterName the parameter name for error message
     * @throws IllegalArgumentException if obj is null
     */
    public void requireNonNull(Object obj, String parameterName) {
        if (obj == null) {
            throw new IllegalArgumentException(parameterName + " cannot be null");
        }
    }
    
    /**
     * Validates that a string is not null or blank.
     * 
     * @param str the string to validate
     * @param parameterName the parameter name for error message
     * @throws IllegalArgumentException if str is null or blank
     */
    public void requireNonBlank(String str, String parameterName) {
        if (StringUtils.isBlank(str)) {
            throw new IllegalArgumentException(parameterName + " cannot be null or blank");
        }
    }
    
    /**
     * Validates that a collection is not null or empty.
     * 
     * @param collection the collection to validate
     * @param parameterName the parameter name for error message
     * @throws IllegalArgumentException if collection is null or empty
     */
    public void requireNonEmpty(Collection<?> collection, String parameterName) {
        if (collection == null || collection.isEmpty()) {
            throw new IllegalArgumentException(parameterName + " cannot be null or empty");
        }
    }
    
    /**
     * Validates that a map is not null or empty.
     * 
     * @param map the map to validate
     * @param parameterName the parameter name for error message
     * @throws IllegalArgumentException if map is null or empty
     */
    public void requireNonEmpty(Map<?, ?> map, String parameterName) {
        if (map == null || map.isEmpty()) {
            throw new IllegalArgumentException(parameterName + " cannot be null or empty");
        }
    }
    
    /**
     * Validates that an array is not null or empty.
     * 
     * @param array the array to validate
     * @param parameterName the parameter name for error message
     * @throws IllegalArgumentException if array is null or empty
     */
    public void requireNonEmpty(Object[] array, String parameterName) {
        if (array == null || array.length == 0) {
            throw new IllegalArgumentException(parameterName + " cannot be null or empty");
        }
    }
    
    /**
     * Validates that a number is positive.
     * 
     * @param number the number to validate
     * @param parameterName the parameter name for error message
     * @throws IllegalArgumentException if number is not positive
     */
    public void requirePositive(int number, String parameterName) {
        if (number <= 0) {
            throw new IllegalArgumentException(parameterName + " must be positive but was " + number);
        }
    }
    
    /**
     * Validates that a number is positive.
     * 
     * @param number the number to validate
     * @param parameterName the parameter name for error message
     * @throws IllegalArgumentException if number is not positive
     */
    public void requirePositive(long number, String parameterName) {
        if (number <= 0) {
            throw new IllegalArgumentException(parameterName + " must be positive but was " + number);
        }
    }
    
    /**
     * Validates that a number is non-negative.
     * 
     * @param number the number to validate
     * @param parameterName the parameter name for error message
     * @throws IllegalArgumentException if number is negative
     */
    public void requireNonNegative(int number, String parameterName) {
        if (number < 0) {
            throw new IllegalArgumentException(parameterName + " cannot be negative but was " + number);
        }
    }
    
    /**
     * Validates that a number is non-negative.
     * 
     * @param number the number to validate
     * @param parameterName the parameter name for error message
     * @throws IllegalArgumentException if number is negative
     */
    public void requireNonNegative(long number, String parameterName) {
        if (number < 0) {
            throw new IllegalArgumentException(parameterName + " cannot be negative but was " + number);
        }
    }
    
    /**
     * Validates that a condition is true.
     * 
     * @param condition the condition to validate
     * @param message the error message if condition is false
     * @throws IllegalArgumentException if condition is false
     */
    public void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
    
    /**
     * Validates that an object is of a specific type.
     * 
     * @param obj the object to validate
     * @param expectedType the expected type
     * @param parameterName the parameter name for error message
     * @throws IllegalArgumentException if obj is not of expected type
     */
    public void requireType(Object obj, Class<?> expectedType, String parameterName) {
        requireNonNull(obj, parameterName);
        requireNonNull(expectedType, "expectedType");
        
        if (!expectedType.isInstance(obj)) {
            throw new IllegalArgumentException(
                parameterName + " must be of type " + expectedType.getName() + 
                " but was " + obj.getClass().getName()
            );
        }
    }
    
    /**
     * Validates multiple non-null parameters at once.
     * 
     * @param parameters pairs of objects and their names
     * @throws IllegalArgumentException if any parameter is null
     */
    public void requireNonNulls(Object... parameters) {
        if (parameters.length % 2 != 0) {
            throw new IllegalArgumentException("Parameters must be provided in pairs (object, name)");
        }
        
        for (int i = 0; i < parameters.length; i += 2) {
            Object obj = parameters[i];
            String name = (String) parameters[i + 1];
            requireNonNull(obj, name);
        }
    }
}
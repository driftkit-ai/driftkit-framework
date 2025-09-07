package ai.driftkit.workflow.engine.spring.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Generic REST response wrapper.
 * Compatible with legacy workflow controller response format.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RestResponse<T> {
    private boolean success;
    private T data;
    
    public static <T> RestResponse<T> success(T data) {
        return new RestResponse<>(true, data);
    }
    
    public static <T> RestResponse<T> failure(T data) {
        return new RestResponse<>(false, data);
    }
}
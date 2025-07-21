package ai.driftkit.common.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
public class RestResponse<T> {
    @NotNull
    private boolean success;
    private T data;
    private String message;
    private String error;

    public RestResponse(boolean success) {
        this.success = success;
    }
    
    public RestResponse(boolean success, T data) {
        this.success = success;
        this.data = data;
    }
    
    public RestResponse(boolean success, T data, String message) {
        this.success = success;
        this.data = data;
        this.message = message;
    }
    
    public RestResponse(boolean success, T data, String message, String error) {
        this.success = success;
        this.data = data;
        this.message = message;
        this.error = error;
    }
}
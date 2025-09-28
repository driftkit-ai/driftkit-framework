package ai.driftkit.workflows.spring;

import ai.driftkit.common.domain.client.ModelContentMessage;
import ai.driftkit.common.domain.client.ModelContentMessage.ModelContentElement.ImageData;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parameter holder for model requests with a fluent builder API
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ModelRequestParams {
    private String promptId;
    private String promptText;
    private Map<String, Object> variables;
    private Double temperature;
    private String model;
    private List<ModelContentMessage> contextMessages;
    private ImageData imageData;

    /**
     * Create a new empty params builder
     * @return A new params builder
     */
    public static ModelRequestParams create() {
        return new ModelRequestParams();
    }

    /**
     * Add a single variable to the variables map
     * @param key The variable name
     * @param value The variable value
     * @return This builder
     */
    public ModelRequestParams withVariable(String key, Object value) {
        if (this.variables == null) {
            this.variables = new HashMap<>();
        }
        this.variables.put(key, value);
        return this;
    }
}
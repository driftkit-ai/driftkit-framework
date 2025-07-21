package ai.driftkit.workflows.core.domain;

import ai.driftkit.common.utils.JsonUtils;
import ai.driftkit.workflows.core.service.WorkflowAnalyzer;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StopEvent<T> implements WorkflowEvent {
    private String result;
    @JsonIgnore
    private transient Class<T> cls;

    public static <T> StopEvent<T> ofObject(T obj) throws JsonProcessingException {
        return (StopEvent<T>) new StopEvent<>(JsonUtils.toJson(obj), obj.getClass());
    }

    public static StopEvent ofJson(String obj) throws JsonProcessingException {
        return new StopEvent<>(obj, String.class);
    }

    public static StopEvent ofString(String obj) throws JsonProcessingException {
        return new StopEvent<>(obj, String.class);
    }

    public T get() throws JsonProcessingException {
        return get(cls);
    }

    public <T> T get(Class<T> cls) throws JsonProcessingException {
        return WorkflowAnalyzer.objectMapper.readValue(result, cls);
    }
}

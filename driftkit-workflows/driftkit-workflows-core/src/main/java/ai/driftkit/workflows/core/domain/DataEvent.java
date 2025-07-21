package ai.driftkit.workflows.core.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataEvent<T> implements WorkflowEvent {
    private T result;
    private String nextStepName;
}

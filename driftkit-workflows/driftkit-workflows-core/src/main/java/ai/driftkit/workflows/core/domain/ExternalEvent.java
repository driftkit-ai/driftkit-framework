package ai.driftkit.workflows.core.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExternalEvent<T extends StartEvent> implements WorkflowEvent {
    private Class<?> workflowCls;
    private T startEvent;
    private String nextStepName;
}

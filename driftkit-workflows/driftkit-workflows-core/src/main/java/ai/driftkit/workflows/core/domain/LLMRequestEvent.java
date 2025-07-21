package ai.driftkit.workflows.core.domain;

import ai.driftkit.common.domain.MessageTask;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LLMRequestEvent extends StartEvent {
    private MessageTask task;
}
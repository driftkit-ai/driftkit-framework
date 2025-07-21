package ai.driftkit.workflows.core.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StartQueryEvent extends StartEvent {
    private String query;
}

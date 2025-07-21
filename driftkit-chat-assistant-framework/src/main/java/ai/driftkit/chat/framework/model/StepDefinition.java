package ai.driftkit.chat.framework.model;

import ai.driftkit.chat.framework.ai.domain.AIFunctionSchema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepDefinition {
    private int index;
    private String id;
    private String action;
    private boolean userInputRequired;

    @Builder.Default
    private List<AIFunctionSchema> inputSchemas = new ArrayList<>();
    
    @Builder.Default
    private List<AIFunctionSchema> outputSchemas = new ArrayList<>();
    
    private boolean asyncExecution;
    
    @Builder.Default
    private List<String> nextStepIds = new ArrayList<>();

    public void addNextStepId(String nextStepId) {
        if (nextStepId != null && !this.nextStepIds.contains(nextStepId)) {
            this.nextStepIds.add(nextStepId);
        }
    }
}
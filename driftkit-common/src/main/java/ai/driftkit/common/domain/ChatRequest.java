package ai.driftkit.common.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {
    private String id;
    @NotNull
    private String name;
    private String systemMessage;
    @NotNull
    private Language language;
    @NotNull
    private int memoryLength;
}
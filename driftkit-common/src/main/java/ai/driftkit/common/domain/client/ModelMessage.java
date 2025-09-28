package ai.driftkit.common.domain.client;

import ai.driftkit.common.tools.ToolCall;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ModelMessage {
    private Role role;
    private String content;
    private List<ToolCall> toolCalls;

    // Helper factory methods
    public static ModelMessage user(String content) {
        return ModelMessage.builder()
                .role(Role.user)
                .content(content)
                .build();
    }

    public static ModelMessage assistant(String content) {
        return ModelMessage.builder()
                .role(Role.assistant)
                .content(content)
                .build();
    }

    public static ModelMessage system(String content) {
        return ModelMessage.builder()
                .role(Role.system)
                .content(content)
                .build();
    }
}

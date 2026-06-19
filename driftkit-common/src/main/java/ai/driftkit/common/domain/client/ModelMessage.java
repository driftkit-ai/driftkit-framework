package ai.driftkit.common.domain.client;

import ai.driftkit.common.tools.ToolCall;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModelMessage {
    private Role role;
    private String content;
    private List<ToolCall> toolCalls;
    private String reasoningContent;
    private String toolCallId;

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

    /**
     * Tool result message: pairs with the assistant tool call identified by toolCallId.
     */
    public static ModelMessage tool(String content, String toolCallId) {
        return ModelMessage.builder()
                .role(Role.tool)
                .content(content)
                .toolCallId(toolCallId)
                .build();
    }

    /**
     * Assistant message that requested tool calls. Must be echoed back to the model
     * before the matching tool result messages.
     */
    public static ModelMessage assistantToolCalls(String content, List<ToolCall> toolCalls) {
        return ModelMessage.builder()
                .role(Role.assistant)
                .content(content)
                .toolCalls(toolCalls)
                .build();
    }
}

package ai.driftkit.common.domain.client;

import ai.driftkit.common.domain.ModelTrace;
import ai.driftkit.common.domain.client.ModelContentMessage.ModelContentElement.ImageData;
import ai.driftkit.common.tools.ToolCall;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelImageResponse {
    private Long createdTime;
    private String model;

    private String revisedPrompt;
    private List<ImageData> bytes;
    private ModelTrace trace;

}
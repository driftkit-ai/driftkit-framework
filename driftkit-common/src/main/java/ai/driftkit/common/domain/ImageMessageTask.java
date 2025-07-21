package ai.driftkit.common.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(Include.NON_NULL)
@EqualsAndHashCode(callSuper = true)
public class ImageMessageTask extends AITask {
    List<GeneratedImage> images;

    @Builder
    public ImageMessageTask(
            String messageId,
            String chatId,
            String message,
            String systemMessage,
            String gradeComment,
            Grade grade,
            long createdTime,
            long responseTime,
            String modelId,
            List<String> promptIds,
            boolean jsonRequest,
            boolean jsonResponse,
            Map<String, Object> variables
    ) {
        super(messageId, chatId, message, systemMessage, gradeComment, grade, createdTime, responseTime, null, null, null, Language.GENERAL, jsonRequest, jsonResponse, modelId, promptIds, variables, null, null, null, null, null);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(Include.NON_NULL)
    public static class GeneratedImage {
        String url;
        byte[] data;
        String mimeType;
        String revisedPrompt;
    }
}
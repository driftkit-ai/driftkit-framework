package ai.driftkit.common.domain.client;

import ai.driftkit.common.domain.ModelTrace;
import ai.driftkit.common.domain.client.ModelImageResponse.ModelContentMessage.ModelContentElement.ImageData;
import ai.driftkit.common.domain.client.ModelTextRequest.MessageType;
import ai.driftkit.common.tools.ToolCall;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ModelContentMessage {
        private List<ModelContentElement> content;
        private Role role;
        private String name;

        @Builder
        public ModelContentMessage(Role role, String name, List<ModelContentElement> content) {
            this.role = role;
            this.name = name;
            this.content = content;
        }

        public static ModelContentMessage create(Role role, String str) {
            return create(role, List.of(str), Collections.emptyList());
        }

        public static ModelContentMessage create(Role role, String str, ImageData image) {
            return create(role, List.of(str), List.of(image));
        }

        public static ModelContentMessage create(Role role, List<String> str, List<ImageData> images) {
            return ModelContentMessage.builder()
                    .role(role)
                    .content(Stream.concat(
                            str.stream().map(ModelContentElement::create),
                            images.stream().map(e -> ModelContentElement.create(e.getImage(), e.getMimeType()))
                    ).toList())
                    .build();
        }

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        @JsonInclude(JsonInclude.Include.NON_NULL)  // Exclude null fields for cleaner serialization
        public static class ModelContentElement {
            private MessageType type;
            private ImageData image;
            private String text;

            public static ModelContentElement create(byte[] image, String mimeType) {
                return new ModelContentElement(
                        MessageType.image,
                        new ImageData(image, mimeType),
                        null
                );
            }

            public static ModelContentElement create(String str) {
                return new ModelContentElement(
                        MessageType.text,
                        null,
                        str
                );
            }

            @Data
            @Builder
            @NoArgsConstructor
            @AllArgsConstructor
            @JsonInclude(JsonInclude.Include.NON_NULL)  // Exclude null fields for cleaner serialization
            public static class ImageData {
                private byte[] image;
                private String mimeType;
            }
        }

    }

    @Data
    @Builder
    public static class ModelMessage {
        private Role role;
        private String content;
        private List<ToolCall> toolCalls;
    }
}
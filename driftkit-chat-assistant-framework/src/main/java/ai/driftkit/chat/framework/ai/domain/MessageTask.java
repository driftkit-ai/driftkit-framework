package ai.driftkit.chat.framework.ai.domain;

import ai.driftkit.common.domain.RestResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MessageTask {
    private String messageId;
    private String result;
    private String checkerPromptId;
    private String imageTaskId;
    private CheckerResponse checkerResponse;

    public static class MessageRestResponse extends RestResponse<MessageTask> {
        public MessageRestResponse() {
            super();
        }
        
        public MessageRestResponse(boolean success, MessageTask data) {
            super(success, data);
        }
    }

    public static class MessageIdResponse extends RestResponse<MessageTask.MessageId> {
        public MessageIdResponse() {
            super();
        }
        
        public MessageIdResponse(boolean success, MessageTask.MessageId data) {
            super(success, data);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageId {
        private String messageId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(Include.NON_NULL)
    public static class CheckerResponse {
        String id;
        String checkerMessage;
        String checkerPromptId;
        String messageTaskId;
        Object correctMessage;
        List<Fix> fixes;
        long createdTime;
        long resultTime;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Fix {
            String wrongStatement;
            String fixedStatement;
        }
    }
}
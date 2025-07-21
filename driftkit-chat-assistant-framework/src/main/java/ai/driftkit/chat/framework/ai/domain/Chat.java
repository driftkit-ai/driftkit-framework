package ai.driftkit.chat.framework.ai.domain;

import ai.driftkit.common.domain.RestResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Chat {
    private String chatId;

    public static class ChatRestResponse extends RestResponse<Chat> {
        public ChatRestResponse() {
            super();
        }
        
        public ChatRestResponse(boolean success, Chat data) {
            super(success, data);
        }
    }
}
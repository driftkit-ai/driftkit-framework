package ai.driftkit.chat.framework.ai.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatRequest {
    private String id;
    private String name;
    private String systemMessage;
    private MaterialLanguage language;
    private int memoryLength;
}
package ai.driftkit.common.domain.chat;

import ai.driftkit.common.domain.Language;
import ai.driftkit.common.domain.chat.ChatMessage.PropertyType;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.*;

/**
 * Chat response message from AI.
 * Ported from workflow-engine-core to be shared across modules.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ChatResponse extends ChatMessage {
    private String workflowId;
    private NextSchema nextSchema;
    private boolean completed = true;
    private Integer percentComplete;
    private boolean required = true;

    public ChatResponse(String responseId, String chatId, String workflowId, Language language, 
                        boolean completed, Integer percentComplete, String userId, Map<String, String> props) {
        this(responseId, chatId, workflowId, language, completed, percentComplete, true, userId, props);
    }
    
    public ChatResponse(String responseId, String chatId, String workflowId, Language language, 
                        boolean completed, Integer percentComplete, boolean required, String userId, Map<String, String> props) {
        super(responseId, chatId, MessageType.AI, userId);
        this.workflowId = workflowId;
        this.language = language;
        this.completed = completed;
        this.percentComplete = percentComplete != null ? percentComplete : 100;
        this.required = required;
        
        if (props != null) {
            setPropertiesMap(props);
        }
    }

    public ChatResponse(String responseId, String chatId, String workflowId, Language language, String userId, Map<String, String> props) {
        this(responseId, chatId, workflowId, language, true, 100, userId, props);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NextSchema implements Serializable {
        String schemaName;
        List<NextProperties> properties;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(Include.NON_DEFAULT)
    public static class NextProperties implements Serializable {
        private String name;
        private String nameId;
        private PropertyType type;
        private List<String> values;
        private boolean isMultiSelect;
    }
}
package ai.driftkit.workflow.engine.chat;

import ai.driftkit.workflow.engine.chat.ChatDomain.ChatResponse.NextSchema;
import ai.driftkit.workflow.engine.chat.ChatDomain.DataProperty;
import ai.driftkit.workflow.engine.chat.ChatDomain.MessageType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents a task in the chat conversation UI.
 * Used for displaying message tasks in the frontend with progress tracking.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
public class ChatMessageTask {
    private String id;
    private String nameId;
    private MessageType type;
    private List<DataProperty> properties;
    private NextSchema nextSchema;
    private long timestamp;
    private Boolean completed;
    private Integer percentComplete;
    private Boolean required;
}
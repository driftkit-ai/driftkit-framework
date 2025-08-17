package ai.driftkit.common.domain.chat;

import ai.driftkit.common.domain.Language;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Chat request message from user.
 * Ported from workflow-engine-core to be shared across modules.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Slf4j
public class ChatRequest extends ChatMessage {
    private String requestSchemaName;
    private String workflowId;
    private Boolean composable;

    public ChatRequest(String chatId, Map<String, String> properties, Language language, String workflowId) {
        this(chatId, properties, language, workflowId, null);
    }

    public ChatRequest(String chatId, Map<String, String> properties, Language language, String workflowId, String requestSchemaName) {
        super(UUID.randomUUID().toString(), chatId, MessageType.USER);
        this.language = language;
        this.workflowId = workflowId;
        this.requestSchemaName = requestSchemaName;
        
        if (properties != null) {
            setPropertiesMap(properties);
        }
    }

    @JsonIgnore
    public String getMessage() {
        Map<String, String> propsMap = getPropertiesMap();
        return propsMap.get("message");
    }
    
    @JsonSetter("properties")
    public void setPropertiesFromJson(JsonNode node) {
        if (node == null) {
            return;
        }
        
        if (node.isArray()) {
            // Handle array format (default)
            this.properties = new ArrayList<>();
            for (JsonNode propNode : node) {
                DataProperty prop = new DataProperty();
                if (propNode.has("name")) prop.setName(propNode.get("name").asText());
                if (propNode.has("value")) prop.setValue(propNode.get("value").asText());
                if (propNode.has("nameId")) prop.setNameId(propNode.get("nameId").asText());
                if (propNode.has("dataNameId")) prop.setDataNameId(propNode.get("dataNameId").asText());
                if (propNode.has("data")) prop.setData(propNode.get("data").asText());
                if (propNode.has("multiSelect")) prop.setMultiSelect(propNode.get("multiSelect").asBoolean());
                if (propNode.has("type")) prop.setType(PropertyType.valueOf(propNode.get("type").asText()));
                if (propNode.has("valueAsNameId")) prop.setValueAsNameId(propNode.get("valueAsNameId").asBoolean());
                this.properties.add(prop);
            }
        } else if (node.isObject()) {
            // Handle object format (backward compatibility)
            this.properties = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                DataProperty prop = new DataProperty();
                prop.setName(field.getKey());
                prop.setValue(field.getValue().asText());
                prop.setType(PropertyType.STRING);
                this.properties.add(prop);
            }
        }
    }
    
    public void resolveDataNameIdReferences(List<ChatMessage> previousMessages) {
        if (properties == null || properties.isEmpty() || previousMessages == null || previousMessages.isEmpty()) {
            return;
        }
        
        for (DataProperty property : properties) {
            if (property.getDataNameId() == null) {
                continue;
            }
            
            String dataNameId = property.getDataNameId();
            
            for (ChatMessage message : previousMessages) {
                if (id.equals(message.getId())) {
                    continue;
                }
                
                for (DataProperty historicalProp : message.getProperties()) {
                    if (historicalProp.getNameId() == null || historicalProp.getValue() == null) {
                        continue;
                    }
                    
                    if (!dataNameId.equals(historicalProp.getNameId())) {
                        continue;
                    }

                    property.setData(historicalProp.getValue());
                    log.debug("Resolved dataNameId '{}' to value '{}' from message {}", 
                            dataNameId, historicalProp.getValue(), message.getId());
                    break;
                }
            }
        }
    }
}
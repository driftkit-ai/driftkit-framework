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
import java.util.function.Consumer;

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
        return propsMap.get(ChatMessage.PROPERTY_MESSAGE);
    }
    
    private static final Map<String, Consumer<PropertyContext>> PROPERTY_HANDLERS = Map.of(
        "name", ctx -> ctx.prop.setName(ctx.node.asText()),
        "value", ctx -> ctx.prop.setValue(ctx.node.asText()),
        "nameId", ctx -> ctx.prop.setNameId(ctx.node.asText()),
        "dataNameId", ctx -> ctx.prop.setDataNameId(ctx.node.asText()),
        "data", ctx -> ctx.prop.setData(ctx.node.asText()),
        "multiSelect", ctx -> ctx.prop.setMultiSelect(ctx.node.asBoolean()),
        "type", ctx -> ctx.prop.setType(PropertyType.valueOf(ctx.node.asText())),
        "valueAsNameId", ctx -> ctx.prop.setValueAsNameId(ctx.node.asBoolean())
    );
    
    private record PropertyContext(DataProperty prop, JsonNode node) {}
    
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
                
                PROPERTY_HANDLERS.forEach((fieldName, handler) -> {
                    if (propNode.has(fieldName)) {
                        handler.accept(new PropertyContext(prop, propNode.get(fieldName)));
                    }
                });
                
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
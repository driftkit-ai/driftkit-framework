package ai.driftkit.common.domain.chat;

import ai.driftkit.common.domain.Language;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base chat message class.
 * Ported from workflow-engine-core to be shared across modules.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ChatRequest.class, name = "USER"),
        @JsonSubTypes.Type(value = ChatResponse.class, name = "AI"),
})
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_DEFAULT)
@Slf4j
public class ChatMessage implements Serializable {
    
    /**
     * Standard property key for message text content.
     */
    public static final String PROPERTY_MESSAGE = "message";
    
    protected String id;
    protected String chatId;
    protected MessageType type;
    protected Language language;
    protected Long timestamp;
    protected List<DataProperty> properties = new ArrayList<>();
    protected String userId;

    public ChatMessage(String id, String chatId, MessageType type) {
        this.id = id;
        this.chatId = chatId;
        this.type = type;
        this.timestamp = System.currentTimeMillis();
    }

    public ChatMessage(String id, String chatId, MessageType type, String userId) {
        this(id, chatId, type);
        this.userId = userId;
    }
    
    @JsonIgnore
    public Map<String, String> getPropertiesMap() {
        Map<String, String> propsMap = new HashMap<>();
        for (DataProperty prop : properties) {
            if (prop.getValue() == null) {
                continue;
            }
            propsMap.put(prop.getName(), prop.getValue());
        }
        return propsMap;
    }
    
    @JsonIgnore
    public void setPropertiesMap(Map<String, String> map) {
        if (map == null) {
            return;
        }
        
        for (Map.Entry<String, String> entry : map.entrySet()) {
            updateOrAddProperty(entry.getKey(), entry.getValue());
        }
    }
    
    public void updateOrAddProperty(String name, String value) {
        if (name == null) {
            return;
        }
        
        for (DataProperty prop : properties) {
            if (name.equals(prop.getName())) {
                prop.setValue(value);
                return;
            }
        }
        
        DataProperty newProp = new DataProperty();
        newProp.setName(name);
        newProp.setValue(value);
        newProp.setType(PropertyType.STRING);
        properties.add(newProp);
    }

    public enum PropertyType {
        STRING,
        INTEGER,
        DOUBLE,
        BOOLEAN,
        LITERAL,
        ENUM,
        OBJECT,
        ARRAY_OBJECT,
        ARRAY,
        MAP
    }

    /**
     * Message type enumeration.
     */
    public enum MessageType {
        USER,
        AI,
        CONTEXT,
        SYSTEM
    }
    
    /**
     * Data property for flexible message content.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(Include.NON_DEFAULT)
    public static class DataProperty implements Serializable {
        private String name;
        private String nameId;
        private String dataNameId;
        private String value;
        private String data;
        private Boolean multiSelect;
        private PropertyType type;
        private boolean valueAsNameId;

        public DataProperty(String name, String value, String nameId, PropertyType type) {
            this(name, value, type);
            this.nameId = nameId;
        }

        public DataProperty(String name, String value, PropertyType type) {
            this.name = name;
            this.value = value;
            this.type = type;
        }
        
        public boolean isValueAsNameId() {
            return valueAsNameId;
        }
    }
}
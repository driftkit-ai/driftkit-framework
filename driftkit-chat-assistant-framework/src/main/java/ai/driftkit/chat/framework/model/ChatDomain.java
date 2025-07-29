package ai.driftkit.chat.framework.model;

import ai.driftkit.chat.framework.ai.domain.AIFunctionSchema;
import ai.driftkit.chat.framework.ai.domain.AIFunctionSchema.AIFunctionProperty;
import ai.driftkit.chat.framework.ai.domain.AIFunctionSchema.PropertyType;
import ai.driftkit.common.domain.Language;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class ChatDomain {

    public enum MessageType {
        USER,
        AI,
        CONTEXT,
        SYSTEM
    }

    public enum SessionState {
        WAITING_FOR_USER_INPUT,
        EXECUTING_STEP
    }

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
    public static class ChatMessage implements Serializable {
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

        public void fillCurrentSchema(AIFunctionSchema schema) {
            if (schema == null) {
                return;
            }
            fillCurrentSchema(List.of(schema));
        }

        public void fillCurrentSchema(List<AIFunctionSchema> schemas) {
            if (CollectionUtils.isEmpty(schemas)) {
                return;
            }
            Map<String, DataProperty> propertiesMap = this.properties.stream()
                    .collect(Collectors.toMap(DataProperty::getName, p -> p, (p1, p2) -> p1));

            for (AIFunctionProperty property : schemas.stream().flatMap(e -> e.getProperties().stream()).toList()) {
                DataProperty data = propertiesMap.get(property.getName());

                if (data == null) {
                    log.warn("Schema [{}] is not filled with property [{}]", schemas.get(0).getSchemaName(), property);
                    continue;
                }

                data.setNameId(property.getNameId());
                data.setDataNameId(property.getDataNameId());
                if (property.isMultiSelect()) {
                    data.setMultiSelect(true);
                }
                data.setType(property.getType());
                data.setValueAsNameId(property.isValueAsNameId());
            }
        }
    }
    
    @Data
    @NoArgsConstructor
    public static class ChatRequest extends ChatMessage {
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
        
        public static ChatRequest fromSession(
                WorkflowContext session, 
                String workflowId, 
                Map<String, String> props) {
            ChatRequest request = new ChatRequest();
            request.setId(UUID.randomUUID().toString());
            request.setChatId(session.getContextId());
            request.setType(MessageType.USER);
            request.setLanguage(session.getLanguage());
            request.setWorkflowId(workflowId);
            request.setTimestamp(System.currentTimeMillis());
            request.setUserId(session.getUserId());
            
            if (props != null) {
                request.setPropertiesMap(props);
            }
            
            return request;
        }
        
        public static ChatRequest fromSessionWithMessage(
                WorkflowContext session, 
                String workflowId, 
                String message) {
            Map<String, String> props = new HashMap<>();
            if (message != null) {
                props.put("message", message);
            }
            return fromSession(session, workflowId, props);
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
                    }
                }
            }
        }
    }
    
    @Data
    @NoArgsConstructor
    public static class ChatResponse extends ChatMessage {
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

        public ChatResponse(String chatId, String workflowId, Language language,
                            AIFunctionSchema nextRequestSchema,
                            String responseId, boolean completed, Integer percentComplete, String userId) {
            this(responseId, chatId, workflowId, language, completed, percentComplete, userId, null);
        }

        public ChatResponse(String responseId, String chatId, String workflowId, Language language, AIFunctionSchema nextRequestSchema,
                            boolean completed, Integer percentComplete, String userId) {
            this(responseId, chatId, workflowId, language, completed, percentComplete, userId, null);
        }

        public void setNextSchemaAsSchema(AIFunctionSchema schema) {
            if (schema == null) {
                return;
            }
            
            NextSchema nextSchema = new NextSchema();
            nextSchema.setSchemaName(schema.getSchemaName());
            
            if (schema.getProperties() != null) {
                List<NextProperties> nextProps = new ArrayList<>();
                for (AIFunctionSchema.AIFunctionProperty prop : schema.getProperties()) {
                    NextProperties nextProp = new NextProperties();
                    nextProp.setName(prop.getName());
                    nextProp.setNameId(prop.getNameId());
                    nextProp.setType(prop.getType());
                    if (prop.getValues() != null) {
                        nextProp.setValues(prop.getValues());
                    }
                    nextProp.setMultiSelect(prop.isMultiSelect());
                    nextProps.add(nextProp);
                }
                nextSchema.setProperties(nextProps);
            }
            
            this.nextSchema = nextSchema;
        }

        public static ChatResponse fromSession(
                WorkflowContext session, 
                String workflowId, 
                Map<String, String> props) {
            return new ChatResponse(
                    session.getCurrentResponseId(),
                    session.getContextId(),
                    workflowId,
                    session.getLanguage(),
                    session.getUserId(),
                    props
            );
        }
        
        public static ChatResponse fromSessionWithMessage(
                WorkflowContext session, 
                String workflowId, 
                String message) {
            Map<String, String> props = new HashMap<>();
            if (message != null) {
                props.put("message", message);
            }
            return fromSession(session, workflowId, props);
        }
        
        public static ChatResponse fromSessionWithError(
                WorkflowContext session,
                String workflowId,
                String errorMessage) {
            Map<String, String> props = new HashMap<>();
            if (errorMessage != null) {
                props.put("error", errorMessage);
            }
            return fromSession(session, workflowId, props);
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
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Message {
        private String content;
        private MessageType type;
    }
}
package ai.driftkit.chat.framework.model;

import ai.driftkit.chat.framework.ai.domain.AIFunctionSchema;
import ai.driftkit.chat.framework.ai.domain.MaterialLanguage;
import ai.driftkit.chat.framework.ai.utils.AIUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.type.CollectionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class WorkflowContext {
    private String contextId;
    private String currentResponseId;
    private String userId;
    private MaterialLanguage language;
    private String workflowId;
    private String currentStepId;
    private WorkflowSessionState state;
    private AIFunctionSchema currentSchema;
    private AIFunctionSchema nextInputSchema;
    
    @Builder.Default
    private Map<String, String> properties = new HashMap<>();
    
    @Builder.Default
    private List<StepExecutionRecord> executionHistory = new ArrayList<>();
    
    @Builder.Default
    private Map<String, Object> context = new HashMap<>();
    
    private Long createdTime;
    private Long updatedTime;
    
    public synchronized void saveStepExecution(String stepId, Map<String, String> stepProperties) {
        if (executionHistory == null) {
            executionHistory = new ArrayList<>();
        }
        
        StepExecutionRecord record = new StepExecutionRecord();
        record.setStepId(stepId);
        record.setTimestamp(System.currentTimeMillis());
        record.setProperties(new HashMap<>(stepProperties));
        
        executionHistory.add(record);
        this.updatedTime = System.currentTimeMillis();
    }

    public synchronized void putAll(Map<String, String> map) {
        if (properties == null) {
            properties = new HashMap<>();
        }
        properties.putAll(map);
        this.updatedTime = System.currentTimeMillis();
    }

    public synchronized void putProperty(String key, String value) {
        if (properties == null) {
            properties = new HashMap<>();
        }
        properties.put(key, value);
        this.updatedTime = System.currentTimeMillis();
    }

    public synchronized void setContextValue(String key, Object value) {
        if (context == null) {
            context = new HashMap<>();
        }
        
        try {
            if (value instanceof String) {
                context.put(key, value);
            } else if (value != null) {
                String jsonValue = AIUtils.OBJECT_MAPPER.writeValueAsString(value);
                context.put(key + "_type", value.getClass().getName());
                context.put(key, jsonValue);
            } else {
                context.put(key, null);
            }
            this.updatedTime = System.currentTimeMillis();
        } catch (JsonProcessingException e) {
            log.error("Error serializing object for key [{}]: {}", key, e.getMessage());
        }
    }
    
    @SuppressWarnings("unchecked")
    public synchronized <T> T getContextValue(String key, Class<T> type) {
        if (context == null || !context.containsKey(key)) {
            return null;
        }
        
        Object value = context.get(key);
        
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        
        if (type == String.class && value instanceof String) {
            return type.cast(value);
        }
        
        try {
            if (value instanceof String) {
                String jsonValue = (String)value;
                return AIUtils.OBJECT_MAPPER.readValue(jsonValue, type);
            }
        } catch (JsonProcessingException e) {
            log.error("Error deserializing JSON for key [{}] to type [{}]: {}", key, type.getName(), e.getMessage());
        }
        
        return null;
    }
    
    public synchronized <T> List<T> getContextValueAsList(String key, Class<T> elementType) {
        if (context == null || !context.containsKey(key)) {
            return null;
        }
        
        Object value = context.get(key);
        
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            if (list.isEmpty() || elementType.isInstance(list.get(0))) {
                return (List<T>) list;
            }
        }
        
        try {
            if (value instanceof String) {
                String jsonValue = (String)value;
                CollectionType listType = AIUtils.OBJECT_MAPPER.getTypeFactory()
                    .constructCollectionType(List.class, elementType);
                return AIUtils.OBJECT_MAPPER.readValue(jsonValue, listType);
            }
        } catch (JsonProcessingException e) {
            log.error("Error deserializing JSON for key [{}] to List<{}>: {}", 
                    key, elementType.getName(), e.getMessage());
        }
        
        return null;
    }

    public synchronized String getProperty(String param) {
        return properties.get(param);
    }

    public enum WorkflowSessionState {
        NEW,
        WAITING_FOR_USER_INPUT,
        PROCESSING,
        EXECUTING_STEP,
        COMPLETED,
        ERROR
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StepExecutionRecord {
        private String stepId;
        private Long timestamp;
        private Map<String, String> properties;
    }
}
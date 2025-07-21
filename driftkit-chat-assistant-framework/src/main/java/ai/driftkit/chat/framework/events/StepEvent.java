package ai.driftkit.chat.framework.events;

import ai.driftkit.chat.framework.ai.domain.AIFunctionSchema;
import ai.driftkit.chat.framework.util.SchemaUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StepEvent {
    @Accessors(chain = true)
    private String nextStepId;
    
    @Accessors(chain = true)
    private List<String> possibleNextStepIds = new ArrayList<>();
    
    @Accessors(chain = true)
    private Map<String, String> properties;
    
    @Accessors(chain = true)
    private AIFunctionSchema currentSchema;
    
    @Accessors(chain = true)
    private AIFunctionSchema nextInputSchema;
    
    @Accessors(chain = true)
    private boolean completed;
    
    @Accessors(chain = true)
    private int percentComplete;
    
    @Accessors(chain = true)
    @Builder.Default
    private boolean required = true;
    
    public static StepEvent nextStep(String nextStepId) {
        return StepEvent.builder()
                .nextStepId(nextStepId)
                .completed(true)
                .percentComplete(100)
                .build();
    }

    public static StepEvent of(Object schemaObject, AIFunctionSchema nextInputSchema) {
        return StepEvent.builder()
                .properties(SchemaUtils.extractProperties(schemaObject))
                .currentSchema(SchemaUtils.getSchemaFromClass(schemaObject.getClass()))
                .nextInputSchema(nextInputSchema)
                .completed(true)
                .percentComplete(100)
                .build();
    }

    public static StepEvent completed(Map<String, String> properties, 
                                     AIFunctionSchema currentSchema, 
                                     AIFunctionSchema nextInputSchema) {
        return StepEvent.builder()
                .properties(properties)
                .currentSchema(currentSchema)
                .nextInputSchema(nextInputSchema)
                .completed(true)
                .percentComplete(100)
                .build();
    }
    
    public static StepEvent completed(Map<String, String> properties, 
                                     Class<?> currentSchemaClass, 
                                     Class<?> nextInputSchemaClass) {
        return StepEvent.builder()
                .properties(properties)
                .currentSchema(SchemaUtils.getSchemaFromClass(currentSchemaClass))
                .nextInputSchema(SchemaUtils.getSchemaFromClass(nextInputSchemaClass))
                .completed(true)
                .percentComplete(100)
                .build();
    }
    
    public static StepEvent fromObject(Object schemaObject, Class<?> nextInputSchemaClass) {
        return StepEvent.builder()
                .properties(SchemaUtils.extractProperties(schemaObject))
                .currentSchema(SchemaUtils.getSchemaFromClass(schemaObject.getClass()))
                .nextInputSchema(SchemaUtils.getSchemaFromClass(nextInputSchemaClass))
                .completed(true)
                .percentComplete(100)
                .build();
    }
    
    public static StepEvent fromObjectWithMultipleNextInputs(Object schemaObject, Class<?>[] nextInputSchemaClasses) {
        StepEvent event = StepEvent.builder()
                .properties(SchemaUtils.extractProperties(schemaObject))
                .currentSchema(SchemaUtils.getSchemaFromClass(schemaObject.getClass()))
                .completed(true)
                .percentComplete(100)
                .build();
        
        if (nextInputSchemaClasses != null && nextInputSchemaClasses.length > 0) {
            event.setNextInputSchema(SchemaUtils.getSchemaFromClass(nextInputSchemaClasses[0]));
        }
        
        return event;
    }
    
    public static StepEvent of(Object outputObject, Class<?> nextInputClass) {
        return fromObject(outputObject, nextInputClass);
    }
    
    public static StepEvent of(Object outputObject, Class<?>... nextInputClasses) {
        return fromObjectWithMultipleNextInputs(outputObject, nextInputClasses);
    }
    
    public static StepEvent of(Object outputObject) {
        return fromObject(outputObject, null);
    }
    
    public StepEvent withCurrentSchemaClass(Class<?> schemaClass) {
        this.currentSchema = SchemaUtils.getSchemaFromClass(schemaClass);
        return this;
    }
    
    public StepEvent withNextInputSchemaClass(Class<?> schemaClass) {
        this.nextInputSchema = SchemaUtils.getSchemaFromClass(schemaClass);
        return this;
    }
    
    public StepEvent withSchemaObject(Object schemaObject) {
        this.properties = SchemaUtils.extractProperties(schemaObject);
        this.currentSchema = SchemaUtils.getSchemaFromClass(schemaObject.getClass());
        return this;
    }
    
    public static StepEvent withProperties(Map<String, String> properties) {
        return StepEvent.builder()
                .properties(properties)
                .completed(true)
                .percentComplete(100)
                .build();
    }
    
    public static StepEvent withProperty(String key, String value) {
        Map<String, String> props = new HashMap<>();
        props.put(key, value);
        return withProperties(props);
    }

    public static StepEvent withMessageId(String value) {
        Map<String, String> props = new HashMap<>();
        props.put("messageId", value);
        return withProperties(props);
    }
    
    public static StepEvent withMessageId(String value, boolean required) {
        Map<String, String> props = new HashMap<>();
        props.put("messageId", value);
        return withProperties(props).setRequired(required);
    }

    public static StepEvent withMessage(String message) {
        return withProperty("message", message);
    }
    
    public static StepEvent withError(String errorMessage) {
        return withProperty("error", errorMessage);
    }
    
    public StepEvent setNextStepId(String nextStepId) {
        this.nextStepId = nextStepId;
        addPossibleNextStep(nextStepId);
        return this;
    }
    
    public StepEvent addPossibleNextStep(String stepId) {
        if (possibleNextStepIds == null) {
            possibleNextStepIds = new CopyOnWriteArrayList<>();
        }

        if (stepId != null && !this.possibleNextStepIds.contains(stepId)) {
            this.possibleNextStepIds.add(stepId);
        }
        return this;
    }
    
    public StepEvent addPossibleNextSteps(String... stepIds) {
        if (stepIds != null) {
            for (String stepId : stepIds) {
                addPossibleNextStep(stepId);
            }
        }
        return this;
    }
    
    public static StepEvent withPossibleNextSteps(Map<String, String> properties, String... nextStepIds) {
        StepEvent event = StepEvent.builder()
                .properties(properties)
                .completed(true)
                .percentComplete(100)
                .build();
        
        if (nextStepIds != null) {
            for (String stepId : nextStepIds) {
                event.addPossibleNextStep(stepId);
            }
            
            if (nextStepIds.length > 0) {
                event.setNextStepId(nextStepIds[0]);
            }
        }
        
        return event;
    }
    
    public static StepEvent withPossibleNextSteps(Object outputObject, String... nextStepIds) {
        StepEvent event = of(outputObject);
        
        if (nextStepIds != null) {
            for (String stepId : nextStepIds) {
                event.addPossibleNextStep(stepId);
            }
            
            if (nextStepIds.length > 0) {
                event.setNextStepId(nextStepIds[0]);
            }
        }
        
        return event;
    }
}
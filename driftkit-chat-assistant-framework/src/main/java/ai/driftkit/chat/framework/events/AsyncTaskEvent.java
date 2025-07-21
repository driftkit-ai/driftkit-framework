package ai.driftkit.chat.framework.events;

import ai.driftkit.chat.framework.ai.domain.AIFunctionSchema;
import ai.driftkit.chat.framework.util.SchemaUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AsyncTaskEvent extends StepEvent {
    private String taskName;
    private Map<String, Object> taskArgs;

    @Builder(builderMethodName = "asyncBuilder")
    public AsyncTaskEvent(String nextStepId, List<String> possibleNextStepIds, Map<String, String> properties, AIFunctionSchema currentSchema, AIFunctionSchema nextInputSchema, boolean completed, int percentComplete, String taskName, Map<String, Object> taskArgs) {
        super(nextStepId, possibleNextStepIds, properties, currentSchema, nextInputSchema, completed, percentComplete, true);
        this.taskName = taskName;
        this.taskArgs = taskArgs;
    }

    public static AsyncTaskEvent create(String taskName, 
                                     Map<String, Object> taskArgs,
                                     Map<String, String> responseProperties,
                                     AIFunctionSchema currentSchema,
                                     AIFunctionSchema nextInputSchema) {
        return AsyncTaskEvent.asyncBuilder()
                .taskName(taskName)
                .taskArgs(taskArgs)
                .properties(responseProperties)
                .currentSchema(currentSchema)
                .nextInputSchema(nextInputSchema)
                .completed(false)
                .percentComplete(50)
                .build();
    }
    
    public static AsyncTaskEvent withMessageId(String taskName,
                                           Map<String, Object> taskArgs,
                                           String messageId,
                                           AIFunctionSchema nextInputSchema) {
        Map<String, String> props = new HashMap<>();
        props.put("messageId", messageId);
        
        return AsyncTaskEvent.asyncBuilder()
                .taskName(taskName)
                .taskArgs(taskArgs)
                .properties(props)
                .nextInputSchema(nextInputSchema)
                .completed(false)
                .percentComplete(50)
                .build();
    }
    
    public static AsyncTaskEvent createWithSchemaClasses(String taskName, 
                                                    Map<String, Object> taskArgs,
                                                    Map<String, String> responseProperties,
                                                    Class<?> currentSchemaClass,
                                                    Class<?> nextInputSchemaClass) {
        AIFunctionSchema currentSchema = currentSchemaClass != null ? 
                SchemaUtils.getSchemaFromClass(currentSchemaClass) : null;
        
        AIFunctionSchema nextInputSchema = nextInputSchemaClass != null ? 
                SchemaUtils.getSchemaFromClass(nextInputSchemaClass) : null;
        
        return AsyncTaskEvent.asyncBuilder()
                .taskName(taskName)
                .taskArgs(taskArgs)
                .properties(responseProperties)
                .currentSchema(currentSchema)
                .nextInputSchema(nextInputSchema)
                .completed(false)
                .percentComplete(50)
                .build();
    }
    
    public static AsyncTaskEvent createWithObjects(String taskName, 
                                              Map<String, Object> taskArgs,
                                              Object outputObject,
                                              Class<?> nextInputSchemaClass) {
        Map<String, String> properties = SchemaUtils.extractProperties(outputObject);
        
        AIFunctionSchema currentSchema = outputObject != null ? 
                SchemaUtils.getSchemaFromClass(outputObject.getClass()) : null;
                
        AIFunctionSchema nextInputSchema = nextInputSchemaClass != null ? 
                SchemaUtils.getSchemaFromClass(nextInputSchemaClass) : null;
        
        return AsyncTaskEvent.asyncBuilder()
                .taskName(taskName)
                .taskArgs(taskArgs)
                .properties(properties)
                .currentSchema(currentSchema)
                .nextInputSchema(nextInputSchema)
                .completed(false)
                .percentComplete(50)
                .build();
    }

    public static AsyncTaskEvent withMessageId(String taskName,
                                          Map<String, Object> taskArgs,
                                          String messageId,
                                          Class<?> nextInputSchemaClass) {
        Map<String, String> props = new HashMap<>();
        props.put("messageId", messageId);
        
        AIFunctionSchema nextInputSchema = nextInputSchemaClass != null ? 
                SchemaUtils.getSchemaFromClass(nextInputSchemaClass) : null;
        
        return AsyncTaskEvent.asyncBuilder()
                .taskName(taskName)
                .taskArgs(taskArgs)
                .properties(props)
                .nextInputSchema(nextInputSchema)
                .completed(false)
                .percentComplete(50)
                .build();
    }
}
package ai.driftkit.workflow.engine.chat.converter;

import ai.driftkit.common.domain.chat.ChatMessage;
import ai.driftkit.common.domain.chat.ChatMessage.DataProperty;
import ai.driftkit.common.domain.chat.ChatMessage.MessageType;
import ai.driftkit.common.domain.chat.ChatRequest;
import ai.driftkit.common.domain.chat.ChatResponse;
import ai.driftkit.workflow.engine.chat.ChatMessageTask;
import ai.driftkit.workflow.engine.schema.AIFunctionSchema;
import ai.driftkit.workflow.engine.schema.SchemaUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Converts ChatRequest and ChatResponse objects to ChatMessageTask format.
 */
@Slf4j
public class ChatMessageTaskConverter {

    /**
     * Converts a ChatMessage to one or more ChatMessageTask objects.
     * For composable ChatRequests, creates a separate ChatMessageTask for each nameId-value pair.
     * For other messages, creates a single ChatMessageTask.
     * 
     * @param message The message to convert
     * @return List of converted ChatMessageTask objects
     */
    public static List<ChatMessageTask> convert(ChatMessage message) {
        if (message instanceof ChatRequest) {
            return convertRequest((ChatRequest) message);
        } else if (message instanceof ChatResponse) {
            List<ChatMessageTask> tasks = new ArrayList<>();
            tasks.add(convertResponse((ChatResponse) message));
            return tasks;
        } else {
            throw new IllegalArgumentException("Unsupported message type: " + message.getClass().getName());
        }
    }

    /**
     * Converts a ChatRequest to one or more ChatMessageTask objects.
     * 
     * 1. If ChatRequest is composable=true, creates a separate ChatMessageTask for each nameId-value pair
     * 2. Otherwise creates a single ChatMessageTask with all properties
     * 
     * @param request The ChatRequest to convert
     * @return List of converted ChatMessageTask objects
     */
    private static List<ChatMessageTask> convertRequest(ChatRequest request) {
        List<ChatMessageTask> tasks = new ArrayList<>();
        
        // If it's a composable request (composable=true), create a task for each property
        if (request.getComposable() != null && request.getComposable()) {
            long timestamp = request.getTimestamp();

            for (DataProperty prop : request.getProperties()) {
                // Skip properties with no nameId or value
                if (prop.getNameId() == null || prop.getValue() == null) {
                    continue;
                }

                ChatMessageTask task = new ChatMessageTask();
                task.setId(request.getId() + "_" + prop.getNameId() + "_AI");
                task.setType(MessageType.AI);
                task.setNameId(prop.getNameId());
                task.setTimestamp(timestamp++);
                task.setRequired(true);
                tasks.add(task);


                task = new ChatMessageTask();
                task.setId(request.getId() + "_" + prop.getNameId() + "_USER");
                task.setType(request.getType());
                task.setTimestamp(timestamp++);
                List<DataProperty> properties = new ArrayList<>();
                DataProperty valueProp = getDataProperty(prop);
                properties.add(valueProp);

                // Check if property has valueAsNameId=true and set nameId to property value
                if (prop.isValueAsNameId()) {
                    task.setNameId(prop.getValue());
                    log.debug("Using field value as nameId: {} -> {}", prop.getNameId(), prop.getValue());
                }

                task.setProperties(properties);
                tasks.add(task);
            }
        } else {
            // For non-composable requests, create a single task with all properties
            ChatMessageTask task = new ChatMessageTask();
            task.setId(request.getId());
            task.setType(request.getType());
            task.setTimestamp(request.getTimestamp());
            
            // Use requestSchemaName as messageNameId
            task.setNameId(request.getRequestSchemaName());

            if (request.getProperties().size() == 1) {
                DataProperty valueProp = getDataProperty(request.getProperties().getFirst());
                task.setProperties(List.of(valueProp));
                
                // Check if property has valueAsNameId=true and set nameId to property value
                DataProperty prop = request.getProperties().getFirst();
                if (prop.isValueAsNameId()) {
                    task.setNameId(prop.getValue());
                    log.debug("Using field value as nameId: {} -> {}", prop.getNameId(), prop.getValue());
                }
            } else {
                task.setProperties(new ArrayList<>(request.getProperties()));
                
                // Check if any property has valueAsNameId=true, and use its value as nameId
                for (DataProperty prop : request.getProperties()) {
                    if (prop.isValueAsNameId()) {
                        task.setNameId(prop.getValue());
                        log.debug("Using field value as nameId: {} -> {}", prop.getNameId(), prop.getValue());
                        break; // Use the first match only
                    }
                }
            }

            task.setType(request.getType());

            tasks.add(task);
        }

        return tasks;
    }

    private static DataProperty getDataProperty(DataProperty prop) {
        DataProperty valueProp = new DataProperty();
        valueProp.setName(prop.getName());
        valueProp.setValue(prop.getValue());
        valueProp.setNameId(prop.getNameId());
        valueProp.setData(prop.getData());
        valueProp.setType(prop.getType());
        valueProp.setMultiSelect(prop.getMultiSelect());
        return valueProp;
    }

    /**
     * Converts a ChatResponse to a ChatMessageTask.
     * 
     * 1. Transfers properties to ChatMessageTask properties
     * 2. Uses nextSchema.properties.nameId for the first element with a defined nameId as messageNameId
     * 3. Sets the nextSchema in the ChatMessageTask
     * 
     * @param response The ChatResponse to convert
     * @return The converted ChatMessageTask
     */
    private static ChatMessageTask convertResponse(ChatResponse response) {
        ChatMessageTask task = new ChatMessageTask();
        task.setId(response.getId());
        task.setType(response.getType());
        task.setCompleted(response.isCompleted());
        task.setPercentComplete(response.getPercentComplete());
        task.setRequired(response.isRequired());

        // Set properties
        List<DataProperty> properties = response.getProperties();

        if (response.getNextSchema() != null && response.getNextSchema().getProperties() != null) {
            Optional<String> messageNameId = response.getNextSchema().getProperties().stream()
                    .filter(prop -> StringUtils.isNotBlank(prop.getNameId()))
                    .map(ChatResponse.NextProperties::getNameId)
                    .findFirst();

            task.setNameId(messageNameId.orElse(null));
        }

        if (properties.size() == 1) {
            DataProperty prop = properties.get(0);
            if (prop.isValueAsNameId()) {
                task.setNameId(prop.getValue());
            }
        }

        task.setProperties(properties);
        task.setTimestamp(response.getTimestamp());

        task.setNextSchema(response.getNextSchema());

        task.setType(response.getType());
        
        // Check if the schema has system flag - only set if true
        if (response.getNextSchema() != null && response.getNextSchema().getSchemaName() != null) {
            Class<?> schemaClass = SchemaUtils.getSchemaClass(response.getNextSchema().getSchemaName());
            if (schemaClass != null) {
                AIFunctionSchema schema = SchemaUtils.getSchemaFromClass(schemaClass);
                if (schema != null && schema.isSystem()) {
                    task.setSystem(true);
                }
            }
        }

        return task;
    }
    
    /**
     * Converts a list of ChatMessage objects to ChatMessageTask objects.
     * For composable ChatRequests, creates multiple ChatMessageTask objects.
     * 
     * @param messages The messages to convert
     * @return The converted ChatMessageTask objects
     */
    public static List<ChatMessageTask> convertAll(List<ChatMessage> messages) {
        List<ChatMessageTask> tasks = new ArrayList<>();
        
        for (ChatMessage message : messages) {
            try {
                // Convert message to one or more tasks
                List<ChatMessageTask> messageTasks = convert(message);
                tasks.addAll(messageTasks);
            } catch (Exception e) {
                log.error("Failed to convert message to task: {}", e.getMessage(), e);
            }
        }
        
        return tasks.stream()
                .sorted(Comparator.comparing(ChatMessageTask::getTimestamp))
                .collect(Collectors.toList());
    }
}
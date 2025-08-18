package ai.driftkit.workflow.engine.chat;

import ai.driftkit.common.domain.chat.ChatMessage;
import ai.driftkit.common.domain.chat.ChatResponse;
import ai.driftkit.workflow.engine.schema.AIFunctionSchema;
import ai.driftkit.workflow.engine.schema.AIFunctionSchema.AIFunctionProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Extension methods for ChatResponse to work with AIFunctionSchema.
 * These are in workflow-engine-core because AIFunctionSchema is part of this module.
 */
public class ChatResponseExtensions {
    
    /**
     * Sets the NextSchema from an AIFunctionSchema.
     * This method is here because common module cannot depend on workflow-engine-core.
     */
    public static void setNextSchemaAsSchema(ChatResponse response, AIFunctionSchema schema) {
        if (schema == null) {
            response.setNextSchema(null);
            return;
        }
        
        ChatResponse.NextSchema nextSchema = new ChatResponse.NextSchema();
        nextSchema.setSchemaName(schema.getSchemaName());
        
        if (schema.getProperties() != null) {
            List<ChatResponse.NextProperties> nextProps = new ArrayList<>();
            for (AIFunctionProperty prop : schema.getProperties()) {
                ChatResponse.NextProperties nextProp = new ChatResponse.NextProperties();
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
        
        response.setNextSchema(nextSchema);
    }
}
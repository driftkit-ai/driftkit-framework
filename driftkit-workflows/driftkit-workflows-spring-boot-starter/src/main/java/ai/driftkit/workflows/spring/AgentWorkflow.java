package ai.driftkit.workflows.spring;

import ai.driftkit.common.domain.Language;
import ai.driftkit.common.domain.client.ModelClient;
import ai.driftkit.common.service.ChatMemory;
import ai.driftkit.common.service.TokenWindowChatMemory;
import ai.driftkit.common.utils.SimpleTokenizer;
import ai.driftkit.context.core.service.PromptService;
import ai.driftkit.workflows.core.agent.LLMAgent;
import ai.driftkit.workflows.core.agent.AgentResponse;
import ai.driftkit.workflows.core.agent.RequestTracingProvider;
import ai.driftkit.workflows.core.domain.ExecutableWorkflow;
import ai.driftkit.workflows.core.domain.StartEvent;
import ai.driftkit.workflows.core.domain.WorkflowContext;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

/**
 * Base workflow class that uses LLMAgent for model interactions.
 * Provides simplified API while maintaining full tracing support through RequestTracingProvider.
 */
public abstract class AgentWorkflow<I extends StartEvent, O> extends ExecutableWorkflow<I, O> {
    
    @Getter
    protected final LLMAgent agent;
    protected final PromptService promptService;
    
    public AgentWorkflow(ModelClient modelClient, PromptService promptService) {
        this(modelClient, promptService, 4000);
    }
    
    public AgentWorkflow(ModelClient modelClient, PromptService promptService, int memoryTokens) {
        this.promptService = promptService;
        
        // Create chat memory with configurable token window
        ChatMemory chatMemory = TokenWindowChatMemory.withMaxTokens(memoryTokens, new SimpleTokenizer());
        
        // Create LLMAgent without any hardcoded values
        // Temperature, maxTokens, and systemMessage will be set per request
        this.agent = LLMAgent.builder()
            .modelClient(modelClient)
            .chatMemory(chatMemory)
            .promptService(promptService)
            .build();
    }
    
    /**
     * Create a custom agent with specific configuration
     */
    protected LLMAgent createCustomAgent(String name, String description, String systemMessage, 
                                       Double temperature, Integer maxTokens) {
        ChatMemory chatMemory = TokenWindowChatMemory.withMaxTokens(maxTokens, new SimpleTokenizer());
        
        LLMAgent.CustomLLMAgentBuilder builder = LLMAgent.builder()
            .modelClient(agent.getModelClient())
            .chatMemory(chatMemory)
            .promptService(promptService);
        
        if (StringUtils.isNotBlank(name)) {
            builder.name(name);
        }
        
        if (StringUtils.isNotBlank(description)) {
            builder.description(description);
        }
        
        if (StringUtils.isNotBlank(systemMessage)) {
            builder.systemMessage(systemMessage);
        }
        
        if (temperature != null) {
            builder.temperature(temperature);
        }
        
        if (maxTokens != null) {
            builder.maxTokens(maxTokens);
        }
        
        return builder.build();
    }
    
    /**
     * Build tracing context from workflow context
     */
    protected RequestTracingProvider.RequestContext buildTracingContext(WorkflowContext context, 
                                                                      String promptId, 
                                                                      Map<String, Object> variables) {
        String workflowId = context.getWorkflowId();
        String workflowType = this.getClass().getSimpleName();
        String workflowStep = context.getCurrentStep();
        
        RequestTracingProvider.RequestContext.RequestContextBuilder builder = 
            RequestTracingProvider.RequestContext.builder()
                .contextId(agent.getAgentId())
                .contextType(workflowType + "_" + workflowStep)
                .workflowId(workflowId)
                .workflowType(workflowType)
                .workflowStep(workflowStep);
        
        if (StringUtils.isNotBlank(promptId)) {
            builder.promptId(promptId);
        }
        
        if (variables != null) {
            builder.variables(variables);
        }
        
        if (context.getTask() != null) {
            builder.chatId(context.getTask().getChatId());
        }
        
        return builder.build();
    }
    
    protected Language getLanguageFromContext(WorkflowContext context) {
        Language language = context.get("language");
        return language != null ? language : Language.GENERAL;
    }
}
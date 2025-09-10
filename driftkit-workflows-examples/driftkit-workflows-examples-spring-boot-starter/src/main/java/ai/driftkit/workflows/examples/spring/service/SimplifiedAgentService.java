package ai.driftkit.workflows.examples.spring.service;

import ai.driftkit.common.domain.client.ModelClient;
import ai.driftkit.workflows.core.chat.ChatMemory;
import ai.driftkit.workflows.core.chat.TokenWindowChatMemory;
import ai.driftkit.workflows.core.chat.SimpleTokenizer;
import ai.driftkit.common.tools.Tool;
import ai.driftkit.common.tools.ToolCall;
import ai.driftkit.workflows.core.agent.AgentResponse;
import ai.driftkit.workflows.core.agent.LLMAgent;
import ai.driftkit.workflows.core.agent.ToolExecutionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Example service demonstrating the simplified LLMAgent API with
 * tool calling and structured output support.
 */
@Slf4j
@Service
public class SimplifiedAgentService {
    
    private final LLMAgent agent;
    
    public SimplifiedAgentService(ModelClient modelClient, SimpleTokenizer tokenizer) {
        // Create chat memory with 4000 token window
        ChatMemory chatMemory = TokenWindowChatMemory.withMaxTokens(4000, tokenizer);
        
        this.agent = LLMAgent.builder()
            .modelClient(modelClient)
            .systemMessage("You are a helpful assistant")
            .temperature(0.7)
            .maxTokens(1000)
            .chatMemory(chatMemory)
            .build();
        
        // Register example tools
        registerExampleTools();
    }
    
    /**
     * Simple chat - returns text response
     */
    public String chat(String message) {
        AgentResponse<String> response = agent.executeText(message);
        return response.getText();
    }
    
    /**
     * Chat with variables - returns text response
     */
    public String chatWithVariables(String message, Map<String, Object> variables) {
        AgentResponse<String> response = agent.executeText(message, variables);
        return response.getText();
    }
    
    /**
     * Get tool calls without execution - for manual tool execution
     */
    public List<ToolCall> getToolCalls(String message) {
        AgentResponse<List<ToolCall>> response = agent.executeForToolCalls(message);
        return response.getToolCalls();
    }
    
    /**
     * Execute a specific tool call manually
     */
    public ToolExecutionResult executeToolCall(ToolCall toolCall) {
        return agent.executeToolCall(toolCall);
    }
    
    /**
     * Chat with automatic tool execution - returns typed tool results
     */
    public List<ToolExecutionResult> chatWithTools(String message) {
        AgentResponse<List<ToolExecutionResult>> response = agent.executeWithTools(message);
        
        // Get typed results
        List<ToolExecutionResult> results = response.getToolResults();
        
        // Example of accessing typed tool results
        for (ToolExecutionResult result : results) {
            if (result.isSuccess()) {
                log.info("Tool {} returned: {} (type: {})", 
                    result.getToolName(), 
                    result.getResult(), 
                    result.getResultType().getSimpleName());
                
                // Get typed result based on tool name
                switch (result.getToolName()) {
                    case "getCurrentWeather" -> {
                        WeatherInfo weather = result.getTypedResult();
                        log.info("Weather in {}: {} degrees", weather.location(), weather.temperature());
                    }
                    case "searchDatabase" -> {
                        List<DatabaseRecord> records = result.getTypedResult();
                        log.info("Found {} database records", records.size());
                    }
                }
            }
        }
        
        return results;
    }
    
    /**
     * Extract structured data from text
     */
    public Person extractPersonInfo(String text) {
        AgentResponse<Person> response = agent.executeStructured(text, Person.class);
        return response.getStructuredData();
    }
    
    /**
     * Extract structured data with user-controlled prompt
     */
    public Company extractCompanyInfo(String text) {
        String userMessage = "Extract company information including name, founded year, and CEO from the following text:\n\n" + text;
        AgentResponse<Company> response = agent.executeStructured(userMessage, Company.class);
        return response.getStructuredData();
    }
    
    /**
     * Clear conversation history
     */
    public void clearHistory() {
        agent.clearHistory();
    }
    
    // Register example tools
    private void registerExampleTools() {
        agent.registerTool("getCurrentWeather", this, "Get current weather for a location")
             .registerTool("searchDatabase", this, "Search database for records")
             .registerTool("getCurrentTime", this, "Get current time in a specific timezone");
    }
    
    // Example tool methods
    
    @Tool(name = "getCurrentWeather", description = "Get current weather for a location")
    public WeatherInfo getCurrentWeather(String location) {
        log.info("Getting weather for: {}", location);
        // Simulate weather API call
        return new WeatherInfo(location, 22.5, "Partly cloudy", 65);
    }
    
    @Tool(name = "searchDatabase", description = "Search database for records")
    public List<DatabaseRecord> searchDatabase(String query, int limit) {
        log.info("Searching database for: {} (limit: {})", query, limit);
        // Simulate database search
        return List.of(
            new DatabaseRecord("1", "Record 1", "Description 1"),
            new DatabaseRecord("2", "Record 2", "Description 2")
        );
    }
    
    @Tool(name = "getCurrentTime", description = "Get current time in a specific timezone")
    public String getCurrentTime(String timezone) {
        log.info("Getting time for timezone: {}", timezone);
        try {
            return LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " " + timezone;
        } catch (Exception e) {
            return "Invalid timezone: " + timezone;
        }
    }
    
    // Data classes for structured output and tool results
    
    public record Person(String name, Integer age, String occupation, String email) {}
    
    public record Company(String name, Integer foundedYear, String ceo, List<String> products) {}
    
    public record WeatherInfo(String location, Double temperature, String description, Integer humidity) {}
    
    public record DatabaseRecord(String id, String title, String description) {}
}
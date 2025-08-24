package ai.driftkit.workflows.examples.agent;

import ai.driftkit.common.domain.client.ModelClient;
import ai.driftkit.common.tools.ToolInfo;
import ai.driftkit.workflows.core.agent.*;
import ai.driftkit.workflows.core.agent.tool.AgentAsTool;
import lombok.extern.slf4j.Slf4j;

/**
 * Examples demonstrating the simplified agent API usage.
 * These examples show how to use the new simplified interfaces compared to complex workflows.
 */
@Slf4j
public class SimplifiedAgentExamples {
    
    private final ModelClient llm;
    
    public SimplifiedAgentExamples(ModelClient llm) {
        this.llm = llm;
    }
    
    /**
     * Example 1: Simple Loop Agent for Travel Planning
     * Equivalent to the example from your request.
     */
    public String travelPlanningLoop() {
        // Агент, который выполняет основную работу
        Agent workerAgent = LLMAgent.builder()
                .modelClient(llm)
                .systemMessage("Generate a travel plan for a 3-day trip to Paris.")
                .build();

        Agent evaluatorAgent = LLMAgent.builder()
                .modelClient(llm)
                .systemMessage(
                    "Analyze the provided travel plan. Check if it includes all required elements: " +
                    "1. Visit to the Louvre Museum, " +
                    "2. Visit to the Eiffel Tower, " +
                    "3. A boat trip on the Seine. " +
                    "Determine whether the plan is COMPLETE or needs REVISE status.")
                .build();

        // LoopAgent, который управляет циклом
        LoopAgent planningLoop = LoopAgent.builder()
                .worker(workerAgent)
                .evaluator(evaluatorAgent)
                .stopCondition(LoopStatus.COMPLETE) // Условие для выхода из цикла
                .build();

        // Запускаем цикл
        return planningLoop.execute("Create a plan for me.");
    }
    
    /**
     * Example 2: Sequential Agent for Research and Writing
     * Equivalent to the workflow example from your request.
     */
    public String researchAndWriteWorkflow() {
        // Агент-исследователь: ищет информацию
        Agent researcherAgent = LLMAgent.builder()
                .modelClient(llm)
                .systemMessage("You are a researcher. Find detailed information on the given topic.")
                .build();

        // Агент-писатель: пишет краткую заметку на основе полученной информации
        Agent writerAgent = LLMAgent.builder()
                .modelClient(llm)
                .systemMessage("You are a writer. Summarize the provided text into a concise paragraph.")
                .build();

        // Создаём SequentialAgent, который будет управлять workflow
        SequentialAgent researchAndWriteWorkflow = SequentialAgent.builder()
                .agent(researcherAgent) // Шаг 1
                .agent(writerAgent)     // Шаг 2
                .build();

        // Запускаем весь рабочий процесс с начальным запросом
        String topic = "The history of the Eiffel Tower";
        return researchAndWriteWorkflow.execute(topic);
    }
    
    /**
     * Example 3: Agent Composition with Tools
     * Shows how agents can be used as tools for other agents.
     */
    public String travelOrchestratorExample() {
        // Создаём агентов для каждого шага
        Agent flightAgent = LLMAgent.builder()
                .modelClient(llm)
                .systemMessage("Search for flights. Return flight options with prices and times.")
                .name("FlightSearchAgent")
                .build();
                
        Agent hotelAgent = LLMAgent.builder()
                .modelClient(llm)
                .systemMessage("Book hotels for given dates. Return hotel options with availability.")
                .name("HotelBookingAgent")
                .build();

        // Оборачиваем агентов в инструменты
        ToolInfo flightTool = AgentAsTool.create("flightSearch", "Searches for flights.", flightAgent);
        ToolInfo hotelTool = AgentAsTool.create("hotelBooking", "Books hotels for given dates.", hotelAgent);

        // Создаём главного агента-оркестратора
        Agent travelOrchestrator = LLMAgent.builder()
                .modelClient(llm)
                .systemMessage(
                    "You are a travel planner. First, find flights. Then, book a hotel. " +
                    "If hotel booking fails, try to find flights for different dates and repeat the process.")
                .addTool(flightTool)  // Регистрируем агентов как инструменты
                .addTool(hotelTool)
                .build();

        // Запускаем оркестратор
        return travelOrchestrator.execute("Plan a trip to Rome for next week.");
    }
    
    /**
     * Example 4: Complex Multi-Agent System
     * Combines sequential processing with loop validation.
     */
    public String complexAgentSystem() {
        // Анализатор требований
        Agent requirementAnalyzer = LLMAgent.builder()
                .modelClient(llm)
                .systemMessage("Analyze user requirements and extract key components.")
                .build();
                
        // Генератор решения
        Agent solutionGenerator = LLMAgent.builder()
                .modelClient(llm)
                .systemMessage("Generate a solution based on analyzed requirements.")
                .build();
                
        // Валидатор качества
        Agent qualityValidator = LLMAgent.builder()
                .modelClient(llm)
                .systemMessage(
                    "Validate the solution quality. Return JSON with status: " +
                    "{\"status\": \"COMPLETE\"} if good, {\"status\": \"REVISE\", \"feedback\": \"issues\"} if needs work.")
                .build();
        
        // Создаем последовательный workflow для анализа и генерации
        SequentialAgent analysisAndGeneration = SequentialAgent.builder()
                .agent(requirementAnalyzer)
                .agent(solutionGenerator)
                .build();
        
        // Создаем loop для итеративного улучшения
        LoopAgent qualityLoop = LoopAgent.builder()
                .worker(analysisAndGeneration)
                .evaluator(qualityValidator)
                .stopCondition(LoopStatus.COMPLETE)
                .maxIterations(5)
                .build();
        
        return qualityLoop.execute("I need a comprehensive project management solution for a remote team of 20 developers.");
    }
    
    /**
     * Example 5: Multimodal Agent Usage
     * Shows how to work with images and text.
     */
    public String multimodalExample(byte[] imageData) {
        Agent imageAnalyzer = LLMAgent.builder()
                .modelClient(llm)
                .systemMessage("Analyze the provided image and describe what you see in detail.")
                .build();
        
        Agent contentWriter = LLMAgent.builder()
                .modelClient(llm)
                .systemMessage("Write engaging content based on the image analysis.")
                .build();
        
        // First analyze the image
        String imageAnalysis = imageAnalyzer.execute("Describe this image:", imageData);
        
        // Then generate content based on analysis
        return contentWriter.execute("Create marketing content based on this analysis: " + imageAnalysis);
    }
}
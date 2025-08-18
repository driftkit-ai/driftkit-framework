package ai.driftkit.workflow.engine.builder;

import ai.driftkit.workflow.engine.core.*;
import ai.driftkit.workflow.engine.domain.WorkflowEngineConfig;
import ai.driftkit.workflow.engine.graph.WorkflowGraph;
import ai.driftkit.workflow.engine.persistence.inmemory.*;
import ai.driftkit.common.service.ChatStore;
import ai.driftkit.common.service.TextTokenizer;
import ai.driftkit.common.service.impl.InMemoryChatStore;
import ai.driftkit.common.service.impl.SimpleTextTokenizer;
import ai.driftkit.workflow.engine.async.InMemoryProgressTracker;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for FluentAPI async functionality.
 */
@Slf4j
public class FluentApiAsyncTest {
    
    private WorkflowEngine engine;
    
    @BeforeEach
    public void setUp() {
        WorkflowEngineConfig config = WorkflowEngineConfig.builder()
            .stateRepository(new InMemoryWorkflowStateRepository())
            .progressTracker(new InMemoryProgressTracker())
            .chatSessionRepository(new InMemoryChatSessionRepository())
            .chatStore(new InMemoryChatStore(new SimpleTextTokenizer()))
            .asyncStepStateRepository(new InMemoryAsyncStepStateRepository())
            .suspensionDataRepository(new InMemorySuspensionDataRepository())
            .build();
        
        engine = new WorkflowEngine(config);
    }
    
    @Test
    @DisplayName("Test async handler registration with TriFunction")
    public void testAsyncHandlerWithTriFunction() throws Exception {
        // Create workflow with direct async handler registration
        WorkflowGraph<DataRequest, DataResult> workflow = WorkflowBuilder
            .define("data-processing", DataRequest.class, DataResult.class)
            .withAsyncHandler("process-*", this::processDataAsync)
            .then(this::startProcessing)
            .build();
        
        engine.register(workflow);
        
        // Execute workflow
        DataRequest request = new DataRequest();
        request.setDataId("test-123");
        request.setProcessingType("analysis");
        
        WorkflowEngine.WorkflowExecution<DataResult> execution = 
            engine.execute("data-processing", request);
        
        DataResult result = execution.get(5, TimeUnit.SECONDS);
        
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("Processed: test-123", result.getProcessedData());
        assertEquals("analysis", result.getProcessingType());
        assertTrue(result.getProcessingTimeMs() > 0);
    }
    
    @Test
    @DisplayName("Test async handler with pattern matching")
    public void testAsyncHandlerPatternMatching() throws Exception {
        // Create workflow with multiple async handlers
        WorkflowGraph<MultiTaskRequest, MultiTaskResult> workflow = WorkflowBuilder
            .define("multi-task", MultiTaskRequest.class, MultiTaskResult.class)
            .withAsyncHandler("analyze-*", this::analyzeAsync)
            .withAsyncHandler("transform-*", this::transformAsync)
            .withAsyncHandler("*", this::defaultAsync) // Fallback handler
            .then(this::routeTask)
            .build();
        
        engine.register(workflow);
        
        // Test analyze task
        MultiTaskRequest analyzeRequest = new MultiTaskRequest();
        analyzeRequest.setTaskType("analyze");
        analyzeRequest.setData("test-data");
        
        WorkflowEngine.WorkflowExecution<MultiTaskResult> execution1 = 
            engine.execute("multi-task", analyzeRequest);
        
        MultiTaskResult result1 = execution1.get(5, TimeUnit.SECONDS);
        assertEquals("ANALYZED: test-data", result1.getResult());
        
        // Test transform task
        MultiTaskRequest transformRequest = new MultiTaskRequest();
        transformRequest.setTaskType("transform");
        transformRequest.setData("test-data");
        
        WorkflowEngine.WorkflowExecution<MultiTaskResult> execution2 = 
            engine.execute("multi-task", transformRequest);
        
        MultiTaskResult result2 = execution2.get(5, TimeUnit.SECONDS);
        assertEquals("TRANSFORMED: test-data", result2.getResult());
        
        // Test default task
        MultiTaskRequest defaultRequest = new MultiTaskRequest();
        defaultRequest.setTaskType("other");
        defaultRequest.setData("test-data");
        
        WorkflowEngine.WorkflowExecution<MultiTaskResult> execution3 = 
            engine.execute("multi-task", defaultRequest);
        
        MultiTaskResult result3 = execution3.get(5, TimeUnit.SECONDS);
        assertEquals("DEFAULT: test-data", result3.getResult());
    }
    
    // Step methods
    private StepResult<ProcessingTask> startProcessing(DataRequest request) {
        log.info("Starting processing for: {}", request.getDataId());
        
        // Simulate async task initiation
        CompletableFuture<DataResult> future = CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(1000); // Simulate work
                DataResult result = new DataResult();
                result.setSuccess(true);
                result.setProcessedData("Processed: " + request.getDataId());
                result.setProcessingType(request.getProcessingType());
                result.setProcessingTimeMs(1000);
                return result;
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        
        return StepResult.async(
            "process-" + request.getDataId(),
            2000L,
            Map.of("future", future, "request", request),
            new ProcessingTask(request.getDataId(), "STARTED")
        );
    }
    
    // Async handler using TriFunction
    private StepResult<DataResult> processDataAsync(Map<String, Object> taskArgs, 
                                                   WorkflowContext context, 
                                                   AsyncProgressReporter progress) {
        @SuppressWarnings("unchecked")
        CompletableFuture<DataResult> future = (CompletableFuture<DataResult>) taskArgs.get("future");
        
        try {
            progress.updateProgress(50, "Processing data...");
            DataResult result = future.get(1500, TimeUnit.MILLISECONDS);
            progress.updateProgress(100, "Processing complete");
            return StepResult.finish(result);
        } catch (Exception e) {
            return StepResult.fail(e);
        }
    }
    
    // Multi-task routing
    private StepResult<Object> routeTask(MultiTaskRequest request) {
        String taskId = request.getTaskType() + "-" + System.currentTimeMillis();
        
        Map<String, Object> taskArgs = Map.of(
            "data", request.getData(),
            "type", request.getTaskType()
        );
        
        return StepResult.async(
            taskId,
            3000L,
            taskArgs,
            Map.of("status", "PROCESSING")
        );
    }
    
    // Async handlers for different patterns
    private StepResult<MultiTaskResult> analyzeAsync(Map<String, Object> taskArgs,
                                                     WorkflowContext context,
                                                     AsyncProgressReporter progress) {
        String data = (String) taskArgs.get("data");
        progress.updateProgress(100, "Analysis complete");
        
        MultiTaskResult result = new MultiTaskResult();
        result.setResult("ANALYZED: " + data);
        result.setTaskType("analyze");
        return StepResult.finish(result);
    }
    
    private StepResult<MultiTaskResult> transformAsync(Map<String, Object> taskArgs,
                                                       WorkflowContext context,
                                                       AsyncProgressReporter progress) {
        String data = (String) taskArgs.get("data");
        progress.updateProgress(100, "Transform complete");
        
        MultiTaskResult result = new MultiTaskResult();
        result.setResult("TRANSFORMED: " + data);
        result.setTaskType("transform");
        return StepResult.finish(result);
    }
    
    private StepResult<MultiTaskResult> defaultAsync(Map<String, Object> taskArgs,
                                                     WorkflowContext context,
                                                     AsyncProgressReporter progress) {
        String data = (String) taskArgs.get("data");
        progress.updateProgress(100, "Default processing complete");
        
        MultiTaskResult result = new MultiTaskResult();
        result.setResult("DEFAULT: " + data);
        result.setTaskType("default");
        return StepResult.finish(result);
    }
    
    // Domain objects
    @Data
    public static class DataRequest {
        private String dataId;
        private String processingType;
    }
    
    @Data
    public static class DataResult {
        private boolean success;
        private String processedData;
        private String processingType;
        private long processingTimeMs;
    }
    
    @Data
    public static class ProcessingTask {
        private final String taskId;
        private final String status;
    }
    
    @Data
    public static class MultiTaskRequest {
        private String taskType;
        private String data;
    }
    
    @Data
    public static class MultiTaskResult {
        private String result;
        private String taskType;
    }
}
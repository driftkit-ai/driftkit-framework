package ai.driftkit.workflow.engine.examples;

import ai.driftkit.workflow.engine.async.InMemoryProgressTracker;
import ai.driftkit.workflow.engine.core.WorkflowEngine;
import ai.driftkit.workflow.engine.domain.WorkflowEngineConfig;
import ai.driftkit.workflow.engine.domain.WorkflowException;
import ai.driftkit.workflow.engine.graph.WorkflowGraph;
import ai.driftkit.workflow.engine.core.WorkflowAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the async workflow example.
 */
class AsyncWorkflowExampleTest {

    private WorkflowEngine engine;
    private AsyncWorkflowExample workflow;

    @BeforeEach
    void setUp() {
        // Create engine with progress tracker
        WorkflowEngineConfig config = WorkflowEngineConfig.builder()
                .progressTracker(new InMemoryProgressTracker())
                .build();

        engine = new WorkflowEngine(config);
        workflow = new AsyncWorkflowExample();

        // Register the workflow
        WorkflowGraph<?, ?> graph = WorkflowAnalyzer.analyze(workflow);
        engine.register(graph);
    }

    @Test
    void testAsyncDocumentProcessing() throws Exception {
        // Create request
        AsyncWorkflowExample.DocumentRequest request = new AsyncWorkflowExample.DocumentRequest();
        request.setDocumentId("DOC-123");
        request.setDocumentUrl("https://example.com/document.pdf");
        request.setAnalysisType("summary");

        // Execute workflow
        WorkflowEngine.WorkflowExecution<AsyncWorkflowExample.ProcessingReport> execution =
                engine.execute("document-processing", request);

        // The workflow should complete asynchronously
        assertNotNull(execution);
        assertFalse(execution.isDone());

        // Wait for completion (with timeout)
        AsyncWorkflowExample.ProcessingReport report = execution.get(3000, TimeUnit.SECONDS);

        // Verify results
        assertNotNull(report);
        assertEquals("DOC-123", report.getDocumentId());
        assertNotNull(report.getSummary());
        assertNotNull(report.getAnalysis());
        assertEquals("summary", report.getAnalysis().getAnalysisType());
        assertTrue(report.getAnalysis().getConfidence() > 0.8);
        assertNotNull(report.getReportUrl());
        assertTrue(report.getProcessingTimeMs() > 0);

        System.out.println("Report: " + report.getSummary());
        System.out.println("Processing time: " + report.getProcessingTimeMs() + "ms");
        System.out.println("Confidence: " + report.getAnalysis().getConfidence());
    }

    @Test
    void testAsyncWorkflowWithSentimentAnalysis() throws Exception {
        // Create request for sentiment analysis
        AsyncWorkflowExample.DocumentRequest request = new AsyncWorkflowExample.DocumentRequest();
        request.setDocumentId("DOC-456");
        request.setDocumentUrl("https://example.com/review.txt");
        request.setAnalysisType("sentiment");

        // Execute workflow
        WorkflowEngine.WorkflowExecution<AsyncWorkflowExample.ProcessingReport> execution =
                engine.execute("document-processing", request);

        // Wait for completion
        AsyncWorkflowExample.ProcessingReport report = execution.get(30, TimeUnit.SECONDS);

        // Verify sentiment analysis results
        assertNotNull(report);
        assertEquals("sentiment", report.getAnalysis().getAnalysisType());
        assertNotNull(report.getAnalysis().getResults());
        assertTrue(report.getAnalysis().getResults().containsKey("overall"));
        assertTrue(report.getAnalysis().getResults().containsKey("score"));
        assertTrue(report.getAnalysis().getResults().containsKey("breakdown"));
    }

    @Test
    void testAsyncWorkflowWithInvalidUrl() throws Exception {
        // Create request with invalid URL
        AsyncWorkflowExample.DocumentRequest request = new AsyncWorkflowExample.DocumentRequest();
        request.setDocumentId("DOC-789");
        request.setDocumentUrl(""); // Invalid
        request.setAnalysisType("entities");

        // Execute workflow
        WorkflowEngine.WorkflowExecution<AsyncWorkflowExample.ProcessingReport> execution =
                engine.execute("document-processing", request);

        // Should fail quickly
        ExecutionException exception = assertThrows(ExecutionException.class,
                () -> execution.get(5, TimeUnit.SECONDS));

        // Check the wrapped exception
        Throwable cause = exception.getCause();
        assertNotNull(cause);
        assertTrue(cause instanceof WorkflowException);

        WorkflowException workflowException = (WorkflowException) cause;
        assertTrue(workflowException.getMessage().contains("Invalid document URL"));
    }
}
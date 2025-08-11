package ai.driftkit.workflow.engine.examples;

import ai.driftkit.workflow.engine.annotations.AsyncStep;
import ai.driftkit.workflow.engine.annotations.InitialStep;
import ai.driftkit.workflow.engine.annotations.Step;
import ai.driftkit.workflow.engine.annotations.Workflow;
import ai.driftkit.workflow.engine.core.*;
import ai.driftkit.workflow.engine.domain.WorkflowEvent;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Example workflow demonstrating asynchronous step execution.
 * This workflow simulates document processing with async operations.
 */
@Slf4j
@Workflow(
    id = "document-processing",
    version = "1.0",
    description = "Async document processing workflow"
)
public class AsyncWorkflowExample {
    
    /**
     * Input for document processing workflow
     */
    @Data
    public static class DocumentRequest {
        private String documentId;
        private String documentUrl;
        private String analysisType; // "summary", "sentiment", "entities"
    }
    
    /**
     * Result of text extraction
     */
    @Data
    public static class ExtractedText {
        private String documentId;
        private String text;
        private int wordCount;
        private String language;
    }
    
    /**
     * Result of AI analysis
     */
    @Data
    public static class AnalysisResult {
        private String documentId;
        private String analysisType;
        private Map<String, Object> results;
        private double confidence;
    }
    
    /**
     * Final report
     */
    @Data
    public static class ProcessingReport {
        private String documentId;
        private String summary;
        private AnalysisResult analysis;
        private long processingTimeMs;
        private String reportUrl;
    }
    
    /**
     * Initial step: Validate request and start async text extraction
     * Returns StepResult.Async to indicate async processing
     */
    @InitialStep(description = "Validate document request and start extraction")
    public StepResult<ExtractedText> validateAndExtract(DocumentRequest request) {
        log.info("Starting document processing for: {}", request.getDocumentId());
        
        // Validate request
        if (request.getDocumentUrl() == null || request.getDocumentUrl().isEmpty()) {
            return new StepResult.Fail<>("Invalid document URL");
        }
        
        // Create immediate data for user response
        Map<String, Object> immediateData = Map.of(
            "documentId", request.getDocumentId(),
            "status", "EXTRACTING_TEXT",
            "message", "Document processing started",
            "percentComplete", 10
        );
        
        // Prepare async task arguments
        Map<String, Object> taskArgs = new HashMap<>();
        taskArgs.put("documentUrl", request.getDocumentUrl());
        taskArgs.put("documentId", request.getDocumentId());
        
        // Return async result - the workflow engine will:
        // 1. Return immediateData to the user
        // 2. Execute extractTextAsync asynchronously
        // 3. Continue workflow when async completes
        return new StepResult.Async<ExtractedText>(
            "extractTextAsync",  // This matches the @AsyncStep forStep value
            30000L,  // 30 seconds estimate
            taskArgs,
            immediateData
        );
    }
    
    /**
     * Async handler for text extraction
     * This is called asynchronously by the workflow engine
     */
    @AsyncStep(
        value = "extractTextAsync",  // Matches the taskId in StepResult.Async
        description = "Extract text from document asynchronously",
        inputClass = Map.class
    )
    public StepResult<ExtractedText> extractTextAsync(Map<String, Object> taskArgs, 
                                                      WorkflowContext context,
                                                      AsyncProgressReporter progress) {
        String documentUrl = (String) taskArgs.get("documentUrl");
        String documentId = (String) taskArgs.get("documentId");
        
        log.info("Extracting text from: {}", documentUrl);
        
        try {
            // Update progress during extraction
            progress.updateProgress(10, "Downloading document");
            Thread.sleep(500);
            
            // Check if cancelled
            if (progress.isCancelled()) {
                return new StepResult.Fail<>("Text extraction cancelled");
            }
            
            progress.updateProgress(30, "Parsing document format");
            Thread.sleep(500);
            
            progress.updateProgress(60, "Extracting text content");
            Thread.sleep(ThreadLocalRandom.current().nextInt(1000, 2000));
            
            // Create extracted text result
            ExtractedText result = new ExtractedText();
            result.setDocumentId(documentId);
            result.setText("This is sample extracted text from the document. " +
                          "It contains multiple sentences for analysis. " +
                          "The document discusses various topics of interest.");
            result.setWordCount(ThreadLocalRandom.current().nextInt(100, 1000));
            result.setLanguage("en");
            
            progress.updateProgress(90, "Finalizing extraction");
            Thread.sleep(200);
            
            progress.updateProgress(100, "Text extraction completed");
            log.info("Text extraction completed for document: {} ({} words)", 
                documentId, result.getWordCount());
            
            // Continue to next step with the extracted text
            return new StepResult.Continue<>(result);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new StepResult.Fail<>(e);
        }
    }
    
    /**
     * Analyze the extracted text using AI
     * This step also runs asynchronously
     */
    @Step(
        id = "analyzeText",
        description = "Perform AI analysis on extracted text",
        inputClass = ExtractedText.class,
        nextClasses = {AnalysisResult.class}
    )
    public StepResult<AnalysisResult> analyzeText(ExtractedText text, WorkflowContext context) {
        log.info("Starting AI analysis for document: {}", text.getDocumentId());
        
        // Get analysis type from original request
        String analysisType = context.getTriggerData(DocumentRequest.class).getAnalysisType();
        
        // Create immediate data for progress update
        Map<String, Object> immediateData = Map.of(
            "documentId", text.getDocumentId(),
            "status", "ANALYZING",
            "message", "AI analysis in progress",
            "percentComplete", 50
        );
        
        // Prepare async task
        Map<String, Object> taskArgs = new HashMap<>();
        taskArgs.put("text", text.getText());
        taskArgs.put("analysisType", analysisType);
        taskArgs.put("documentId", text.getDocumentId());
        taskArgs.put("language", text.getLanguage());
        
        // Return async result
        return new StepResult.Async<AnalysisResult>(
            "performAnalysisAsync",
            60000L,  // 60 seconds estimate
            taskArgs,
            immediateData
        );
    }
    
    /**
     * Async handler for AI analysis
     */
    @AsyncStep(
        value = "performAnalysisAsync",  // Matches the taskId in StepResult.Async
        description = "Perform AI analysis asynchronously",
        inputClass = Map.class
    )
    public StepResult<AnalysisResult> performAnalysisAsync(Map<String, Object> taskArgs, 
                                                           WorkflowContext context,
                                                           AsyncProgressReporter progress) {
        String text = (String) taskArgs.get("text");
        String analysisType = (String) taskArgs.get("analysisType");
        String documentId = (String) taskArgs.get("documentId");
        
        log.info("Performing {} analysis on text", analysisType);
        
        try {
            // Update progress during analysis
            progress.updateProgress(5, "Preparing AI model");
            Thread.sleep(500);
            
            if (progress.isCancelled()) {
                return new StepResult.Fail<>("Analysis cancelled");
            }
            
            progress.updateProgress(20, "Tokenizing text");
            Thread.sleep(500);
            
            progress.updateProgress(40, "Running " + analysisType + " analysis");
            Thread.sleep(ThreadLocalRandom.current().nextInt(2000, 3000));
            
            progress.updateProgress(70, "Processing AI results");
            Thread.sleep(500);
            
            // Create analysis result
            AnalysisResult result = new AnalysisResult();
            result.setDocumentId(documentId);
            result.setAnalysisType(analysisType);
            result.setConfidence(0.85 + ThreadLocalRandom.current().nextDouble(0.15));
            
            Map<String, Object> analysisData = new HashMap<>();
            switch (analysisType) {
                case "summary":
                    analysisData.put("summary", "Document discusses key topics with focus on main themes.");
                    analysisData.put("keyPoints", new String[]{"Point 1", "Point 2", "Point 3"});
                    break;
                    
                case "sentiment":
                    analysisData.put("overall", "positive");
                    analysisData.put("score", 0.75);
                    analysisData.put("breakdown", Map.of("positive", 0.75, "neutral", 0.20, "negative", 0.05));
                    break;
                    
                case "entities":
                    analysisData.put("people", new String[]{"John Doe", "Jane Smith"});
                    analysisData.put("organizations", new String[]{"Acme Corp", "Tech Inc"});
                    analysisData.put("locations", new String[]{"New York", "San Francisco"});
                    break;
                    
                default:
                    analysisData.put("result", "Analysis completed");
            }
            
            result.setResults(analysisData);
            
            progress.updateProgress(95, "Finalizing analysis results");
            Thread.sleep(200);
            
            progress.updateProgress(100, "Analysis completed");
            log.info("Analysis completed for document: {} (confidence: {})", 
                documentId, result.getConfidence());
            
            // Continue to report generation
            return new StepResult.Continue<>(result);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new StepResult.Fail<>(e);
        }
    }
    
    /**
     * Generate final report
     */
    @Step(
        id = "generateReport",
        description = "Generate final processing report",
        inputClass = AnalysisResult.class,
        nextClasses = {ProcessingReport.class}
    )
    public StepResult<ProcessingReport> generateReport(AnalysisResult analysis, WorkflowContext context) {
        log.info("Generating report for document: {}", analysis.getDocumentId());
        
        // Get original request
        DocumentRequest request = context.getTriggerData(DocumentRequest.class);
        
        // Calculate processing time
        long processingTime = 5000; // Default 5 seconds for demo
        
        // Create final report
        ProcessingReport report = new ProcessingReport();
        report.setDocumentId(analysis.getDocumentId());
        report.setSummary("Document processed successfully using " + analysis.getAnalysisType());
        report.setAnalysis(analysis);
        report.setProcessingTimeMs(processingTime);
        report.setReportUrl("https://reports.example.com/" + analysis.getDocumentId());
        
        log.info("Document processing completed in {}ms", processingTime);
        
        // Workflow complete
        return new StepResult.Finish<>(report);
    }
}
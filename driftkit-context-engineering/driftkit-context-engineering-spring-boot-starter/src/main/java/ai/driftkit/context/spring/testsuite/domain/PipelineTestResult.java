package ai.driftkit.context.spring.testsuite.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * Result of a single test case within a pipeline test run.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Document(collection = "pipeline_test_results")
public class PipelineTestResult {

    @Id
    private String id;
    private String runId;
    private String testCaseId;
    private String status; // PASSED, FAILED, ERROR
    private String input;
    private String actualOutput;
    private String expectedOutput;
    private int totalSteps;
    private int totalLLMCalls;
    private int totalTokens;
    private double latencyMs;
    private double estimatedCost;
    private List<PipelineStepTrace> stepTraces;
    private String errorMessage;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PipelineStepTrace {
        private String stepId;
        private String promptMethod;
        private int promptVersion;
        private String input;
        private String output;
        private int tokens;
        private double latencyMs;
        private String status; // SUCCESS, ERROR, SKIPPED
        private String errorMessage;
        private int loopIteration;
    }
}

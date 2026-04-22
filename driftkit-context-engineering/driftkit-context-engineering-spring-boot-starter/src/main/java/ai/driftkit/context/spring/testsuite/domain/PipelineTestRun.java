package ai.driftkit.context.spring.testsuite.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

/**
 * Represents a pipeline-level test run: executes a full pipeline for each test case
 * in a dataset, with optional prompt overrides per step.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Document(collection = "pipeline_test_runs")
public class PipelineTestRun {

    @Id
    private String id;
    private String pipelineId;
    private String datasetId;
    private Map<String, String> promptOverrides;
    private RunStatus status;
    private int totalCases;
    private int passedCases;
    private int failedCases;
    private double avgLatencyMs;
    private int totalTokens;
    private double estimatedCost;
    private long createdAt;
    private long completedAt;

    public enum RunStatus {
        QUEUED, RUNNING, COMPLETED, FAILED
    }
}

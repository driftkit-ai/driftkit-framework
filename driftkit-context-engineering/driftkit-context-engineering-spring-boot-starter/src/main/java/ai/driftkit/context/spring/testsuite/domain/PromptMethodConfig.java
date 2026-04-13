package ai.driftkit.context.spring.testsuite.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Per-method configuration for prompt testing requirements.
 * Controls whether a prompt must pass tests before being published.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "prompt_method_configs")
public class PromptMethodConfig {

    @Id
    private String method;

    /** If true, prompt must pass test set before publish. */
    @Builder.Default
    private boolean requireTesting = false;

    /** If true, manual review required after automated tests pass. */
    @Builder.Default
    private boolean requireManualReview = false;

    /** Default test set to run when submitting for testing. */
    private String defaultTestSetId;

    /** Minimum pass rate (0-100) required to promote. Default 100%. */
    @Builder.Default
    private int minPassRate = 100;
}

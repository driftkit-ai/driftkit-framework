package ai.driftkit.context.spring.testsuite.domain;

import ai.driftkit.common.domain.Language;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Environment label pointing to a specific prompt version.
 * Enables dev/staging/production deployment workflow.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "prompt_environments")
public class PromptEnvironment {

    @Id
    private String id;
    private String method;
    private Language language;
    private String environment; // "dev", "staging", "production"
    private int version;
    private String updatedBy;
    private long updatedAt;
}

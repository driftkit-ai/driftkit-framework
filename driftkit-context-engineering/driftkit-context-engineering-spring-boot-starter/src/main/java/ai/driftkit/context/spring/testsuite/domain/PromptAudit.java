package ai.driftkit.context.spring.testsuite.domain;

import ai.driftkit.common.domain.Language;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Audit trail entry for prompt lifecycle changes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Document(collection = "prompt_audit")
public class PromptAudit {

    @Id
    private String id;
    private String promptMethod;
    private Language language;
    private int fromVersion;
    private int toVersion;
    private Action action;
    private String performedBy;
    private String reason;
    private String linkedRunId;
    private long timestamp;

    public enum Action {
        CREATED,
        SAVED,
        PUBLISHED,
        SUBMITTED_FOR_TESTING,
        APPROVED,
        REJECTED,
        RESTORED,
        DELETED,
        MOVED,
        FOLDER_RENAMED,
        FOLDER_DELETED
    }
}

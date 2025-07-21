package ai.driftkit.vector.spring.domain;

import ai.driftkit.vector.spring.parser.UnifiedParser.ParserInput;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "index_tasks")
public class IndexTask {
    @Id
    private String taskId;

    private String indexId;
    private ParserInput parserInput;

    private TaskStatus status;

    private DocumentSaveResult result;

    private Map<String, Object> metadata;

    private long createdTime;
    private long completedTime;

    private String errorMessage;

    public enum TaskStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DocumentSaveResult {
        private int saved;
        private int failed;
        private String errorMessage;
    }
}
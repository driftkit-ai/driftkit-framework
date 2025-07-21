package ai.driftkit.context.spring.testsuite.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "testsets")
public class TestSet {
    @Id
    private String id;
    private String name;
    private String description;
    private String folderId;
    private Long createdAt;
    private Long updatedAt;
}

package ai.driftkit.context.spring.testsuite.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "folders")
public class Folder {
    @Id
    private String id;
    private String name;
    private String description;
    private long createdAt;
    
    @Builder.Default
    private FolderType type = FolderType.TEST_SET;
}
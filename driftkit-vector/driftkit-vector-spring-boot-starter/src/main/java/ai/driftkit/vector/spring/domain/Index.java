package ai.driftkit.vector.spring.domain;

import ai.driftkit.common.domain.Language;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "index")
public class Index {
    @Id
    private String id;

    private String indexName;

    private String description;

    private boolean disabled;

    private Language language;

    private long createdTime;
}
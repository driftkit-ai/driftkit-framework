package ai.driftkit.vector.spring.domain;

import ai.driftkit.vector.spring.parser.UnifiedParser.ParserInput;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document
@NoArgsConstructor
@JsonInclude(Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ParsedContent {
    @Id
    private String id;

    private ParserInput input;
    private String parsedContent;
    private Object metadata;

    private long parsingStatedTime;
    private long parsingEndTime;
    private long createdTime;
}
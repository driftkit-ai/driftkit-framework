package ai.driftkit.context.spring.testsuite.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Base Evaluation class for TestSets
 * Defines the common properties for all types of evaluations
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Document(collection = "evaluations")
public class Evaluation {
    @Id
    private String id;
    private String testSetId;
    private String name;
    private String description;
    private EvaluationType type;
    private Long createdAt;
    private Long updatedAt;

    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.PROPERTY,
            property = "configType")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = JsonSchemaEvalConfig.class, name = "JSON_SCHEMA"),
            @JsonSubTypes.Type(value = ContainsKeywordsEvalConfig.class, name = "CONTAINS_KEYWORDS"),
            @JsonSubTypes.Type(value = ExactMatchEvalConfig.class, name = "EXACT_MATCH"),
            @JsonSubTypes.Type(value = LlmEvalConfig.class, name = "LLM_EVALUATION"),
            @JsonSubTypes.Type(value = WordCountEvalConfig.class, name = "WORD_COUNT"),
            @JsonSubTypes.Type(value = ArrayLengthEvalConfig.class, name = "ARRAY_LENGTH"),
            @JsonSubTypes.Type(value = FieldValueEvalConfig.class, name = "FIELD_VALUE_CHECK"),
            @JsonSubTypes.Type(value = RegexMatchEvalConfig.class, name = "REGEX_MATCH"),
            @JsonSubTypes.Type(value = ManualEvalConfig.class, name = "MANUAL_EVALUATION")
    })
    private EvaluationConfig config;

    /**
     * Types of evaluations that can be applied to test sets
     */
    public enum EvaluationType {
        JSON_SCHEMA,        // Validate response against a JSON schema
        CONTAINS_KEYWORDS,  // Check for specific keywords in response
        EXACT_MATCH,        // Exact string matching
        LLM_EVALUATION,     // Use an LLM to evaluate the response
        WORD_COUNT,         // Count occurrences of words
        ARRAY_LENGTH,       // Check array length in JSON
        FIELD_VALUE_CHECK,  // Check specific field value in JSON
        REGEX_MATCH,        // Match response against a regex pattern
        MANUAL_EVALUATION   // Manual review by a human evaluator
    }
}
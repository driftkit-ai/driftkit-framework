package ai.driftkit.context.spring.testsuite.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Base configuration class for evaluations
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
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
        @JsonSubTypes.Type(value = ImageEvalConfig.class, name = "IMAGE_EVALUATION"),
        @JsonSubTypes.Type(value = ManualEvalConfig.class, name = "MANUAL_EVALUATION")
})
public abstract class EvaluationConfig {
    // Common properties for all eval configs
    private boolean negateResult; // If true, negates the evaluation result (PASS becomes FAIL and vice versa)
    
    /**
     * Evaluate a result against this configuration
     * 
     * @param context The evaluation context containing test set item, original and actual result
     * @return The evaluation result
     */
    public abstract EvaluationResult.EvaluationOutput evaluate(EvaluationContext context);
    
    protected EvaluationResult.EvaluationOutput applyNegation(EvaluationResult.EvaluationOutput output) {
        if (isNegateResult() && output != null) {
            // Flip pass/fail status but keep the same message
            EvaluationResult.EvaluationStatus newStatus = output.getStatus() == EvaluationResult.EvaluationStatus.PASSED
                    ? EvaluationResult.EvaluationStatus.FAILED
                    : output.getStatus() == EvaluationResult.EvaluationStatus.FAILED
                        ? EvaluationResult.EvaluationStatus.PASSED
                        : output.getStatus();
            
            return EvaluationResult.EvaluationOutput.builder()
                    .status(newStatus)
                    .message("Negated: " + output.getMessage())
                    .details(output.getDetails())
                    .build();
        }
        return output;
    }
}
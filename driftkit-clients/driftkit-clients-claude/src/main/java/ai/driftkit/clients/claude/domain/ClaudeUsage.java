package ai.driftkit.clients.claude.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClaudeUsage {
    
    @JsonProperty("input_tokens")
    private Integer inputTokens;
    
    @JsonProperty("output_tokens")
    private Integer outputTokens;
    
    @JsonProperty("cache_creation_input_tokens")
    private Integer cacheCreationInputTokens;
    
    @JsonProperty("cache_read_input_tokens")
    private Integer cacheReadInputTokens;
}
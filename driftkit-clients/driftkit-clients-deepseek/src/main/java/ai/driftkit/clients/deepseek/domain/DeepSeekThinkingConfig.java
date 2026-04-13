package ai.driftkit.clients.deepseek.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DeepSeek thinking/reasoning configuration.
 * <p>
 * {@code {"type": "enabled"}} or {@code {"type": "disabled"}}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeepSeekThinkingConfig {
    private String type;

    public static DeepSeekThinkingConfig enabled() {
        return new DeepSeekThinkingConfig("enabled");
    }

    public static DeepSeekThinkingConfig disabled() {
        return new DeepSeekThinkingConfig("disabled");
    }
}

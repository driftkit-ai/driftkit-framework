package ai.driftkit.common.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Unified DictionaryItem domain object used across all DriftKit modules.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DictionaryItem {
    private String id;
    private int index;
    private String groupId;
    private String name;
    private Language language;
    private List<String> markers;
    private List<String> samples;
    private Long createdAt;
    private Long updatedAt;
}
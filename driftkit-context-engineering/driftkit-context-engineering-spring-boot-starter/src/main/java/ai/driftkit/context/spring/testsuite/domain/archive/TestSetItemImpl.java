package ai.driftkit.context.spring.testsuite.domain.archive;

import ai.driftkit.context.spring.testsuite.domain.TestSetItem;
import ai.driftkit.common.domain.Language;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@Document(collection = "testsetitems")
public class TestSetItemImpl implements TestSetItem {
    @Id
    private String id;
    private String testSetId;
    private String message;
    private String systemMessage;
    private Language language;
    private String workflowType;
    private String workflow;
    private boolean jsonRequest;
    private boolean jsonResponse;
    private Integer logprobs;
    private Integer topLogprobs;
    private Double temperature;
    private String model;
    private Map<String, String> variables;
    private String result;
    private boolean isImageTask;
    private String originalImageTaskId;
    private String originalMessageTaskId;
    private String originalTraceId;
    private String promptId;
    private Long createdAt;
    private Long updatedAt;

    @Override
    public Map<String, Object> getVariablesAsObjectMap() {
        return variables != null ? new HashMap<>(variables) : null;
    }
}
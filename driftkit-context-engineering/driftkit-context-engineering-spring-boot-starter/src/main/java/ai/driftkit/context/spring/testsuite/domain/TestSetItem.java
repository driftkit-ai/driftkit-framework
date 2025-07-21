package ai.driftkit.context.spring.testsuite.domain;

import ai.driftkit.common.domain.Language;
import java.util.Map;

/**
 * Interface for test set items
 */
public interface TestSetItem {
    String getId();
    String getTestSetId();
    String getMessage();
    String getSystemMessage();
    Language getLanguage();
    String getWorkflowType();
    String getWorkflow();
    boolean isJsonRequest();
    boolean isJsonResponse();
    Integer getLogprobs();
    Integer getTopLogprobs();
    Double getTemperature();
    String getModel();
    Map<String, Object> getVariablesAsObjectMap();
    String getResult();
    boolean isImageTask();
    String getOriginalImageTaskId();
    Long getCreatedAt();
}
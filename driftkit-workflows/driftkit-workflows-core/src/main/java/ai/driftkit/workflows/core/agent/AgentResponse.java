package ai.driftkit.workflows.core.agent;

import ai.driftkit.common.domain.client.ModelImageResponse.ModelContentMessage.ModelContentElement;
import ai.driftkit.common.tools.ToolCall;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

/**
 * Unified response wrapper for LLMAgent operations.
 * Supports text, images, tool calls, and structured data.
 */
@Data
@Builder
public class AgentResponse<T> {
    
    // Response content
    private final String text;
    private final List<ModelContentElement.ImageData> images;
    private final T structuredData;
    private final List<ToolCall> toolCalls;
    private final List<ToolExecutionResult> toolResults;
    
    // Response type
    private final ResponseType type;
    
    public enum ResponseType {
        TEXT,
        IMAGES,
        STRUCTURED_DATA,
        TOOL_CALLS,
        TOOL_RESULTS,
        MULTIMODAL
    }
    
    // Convenience methods
    public boolean hasText() {
        return text != null;
    }
    
    public boolean hasImages() {
        return CollectionUtils.isNotEmpty(images);
    }
    
    public boolean hasStructuredData() {
        return structuredData != null;
    }
    
    public boolean hasToolCalls() {
        return CollectionUtils.isNotEmpty(toolCalls);
    }
    
    public boolean hasToolResults() {
        return CollectionUtils.isNotEmpty(toolResults);
    }
    
    // Factory methods
    public static AgentResponse<String> text(String text) {
        return AgentResponse.<String>builder()
            .text(text)
            .type(ResponseType.TEXT)
            .build();
    }
    
    public static AgentResponse<List<ModelContentElement.ImageData>> images(List<ModelContentElement.ImageData> images) {
        return AgentResponse.<List<ModelContentElement.ImageData>>builder()
            .images(images)
            .type(ResponseType.IMAGES)
            .build();
    }
    
    public static <T> AgentResponse<T> structured(T data) {
        return AgentResponse.<T>builder()
            .structuredData(data)
            .type(ResponseType.STRUCTURED_DATA)
            .build();
    }
    
    public static AgentResponse<List<ToolCall>> toolCalls(List<ToolCall> toolCalls) {
        return AgentResponse.<List<ToolCall>>builder()
            .toolCalls(toolCalls)
            .type(ResponseType.TOOL_CALLS)
            .build();
    }
    
    public static AgentResponse<List<ToolExecutionResult>> toolResults(List<ToolExecutionResult> results) {
        return AgentResponse.<List<ToolExecutionResult>>builder()
            .toolResults(results)
            .type(ResponseType.TOOL_RESULTS)
            .build();
    }
    
    public static AgentResponse<String> multimodal(String text, List<ModelContentElement.ImageData> images) {
        return AgentResponse.<String>builder()
            .text(text)
            .images(images)
            .type(ResponseType.MULTIMODAL)
            .build();
    }
}
package ai.driftkit.clients.core;

import ai.driftkit.common.domain.client.*;
import ai.driftkit.config.EtlConfig.VaultConfig;
import ai.driftkit.common.domain.*;
import ai.driftkit.common.service.TextTokenizer;
import ai.driftkit.common.service.impl.SimpleTextTokenizer;
import ai.driftkit.common.domain.client.ModelTextResponse.Usage;
import lombok.Data;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

@Data
public class TraceableModelClient<T> extends ModelClient<T> {

    @Getter
    private final ModelClient<T> delegate;
    private final TextTokenizer tokenizer;
    
    public TraceableModelClient(ModelClient<T> delegate) {
        this(delegate, new SimpleTextTokenizer());
    }
    
    public TraceableModelClient(ModelClient<T> delegate, TextTokenizer tokenizer) {
        this.delegate = delegate;
        this.tokenizer = tokenizer;
    }
    
    public ModelClient<T> init(VaultConfig config) {
        ((ModelClientInit)delegate).init(config);
        return this;
    }
    
    @Override
    public Set<Capability> getCapabilities() {
        return delegate.getCapabilities();
    }
    
    @Override
    public ModelTextResponse textToText(ModelTextRequest prompt) throws UnsupportedCapabilityException {
        ModelTrace trace = ModelTrace.builder().build();
        fillTraceFromTextRequest(trace, prompt);
        
        return executeWithTracing(
            () -> delegate.textToText(prompt),
            trace,
            response -> {
                if (response != null) {
                    enhanceTraceFromTextResponse(trace, response);
                }
                return response;
            },
            () -> createErrorResponse(prompt)
        );
    }
    
    @Override
    public ModelImageResponse textToImage(ModelImageRequest prompt) throws UnsupportedCapabilityException {
        ModelTrace trace = ModelTrace.builder().build();
        fillTraceFromImageRequest(trace, prompt);
        
        return executeWithTracing(
            () -> delegate.textToImage(prompt),
            trace,
            response -> {
                if (response != null) {
                    trace.setModel(response.getModel());
                }
                return response;
            },
            () -> createErrorImageResponse(prompt)
        );
    }
    
    @Override
    public ModelTextResponse imageToText(ModelTextRequest prompt) throws UnsupportedCapabilityException {
        ModelTrace trace = ModelTrace.builder().build();
        fillTraceFromTextRequest(trace, prompt);
        
        return executeWithTracing(
            () -> delegate.imageToText(prompt),
            trace,
            response -> {
                if (response != null) {
                    enhanceTraceFromTextResponse(trace, response);
                }
                return response;
            },
            () -> createErrorResponse(prompt)
        );
    }
    
    private <R> R executeWithTracing(
            Supplier<R> operation,
            ModelTrace trace,
            Function<R, R> responseHandler,
            Supplier<R> errorResponseCreator) {
        
        Instant start = Instant.now();
        R response = null;
        
        try {
            response = operation.get();
            
            if (response != null) {
                response = responseHandler.apply(response);
            }
            
            return response;
        } catch (Exception e) {
            if (response == null) {
                response = errorResponseCreator.get();
            }
            
            trace.setHasError(true);
            trace.setErrorMessage(e.getMessage());
            
            throw e;
        } finally {
            trace.setExecutionTimeMs(Duration.between(start, Instant.now()).toMillis());
            
            if (response != null) {
                attachTraceToResponse(response, trace);
            }
        }
    }
    
    private void attachTraceToResponse(Object response, ModelTrace trace) {
        if (response instanceof ModelTextResponse) {
            ((ModelTextResponse) response).setTrace(trace);
        } else if (response instanceof ModelImageResponse) {
            ((ModelImageResponse) response).setTrace(trace);
        }
    }
    
    private void fillTraceFromTextRequest(ModelTrace trace, ModelTextRequest prompt) {
        if (prompt == null) return;
        
        trace.setModel(prompt.getModel() != null ? prompt.getModel() : delegate.getModel());
        
        if (prompt.getResponseFormat() != null) {
            trace.setResponseFormat(prompt.getResponseFormat().getType().toString());
        }
        
        trace.setTemperature(prompt.getTemperature() != null ? 
                prompt.getTemperature() : delegate.getTemperature());
        
        trace.setPromptTokens(estimatePromptTokens(prompt));
    }
    
    private void fillTraceFromImageRequest(ModelTrace trace, ModelImageRequest prompt) {
        if (prompt == null) return;
        
        trace.setModel(delegate.getModel());
        trace.setResponseFormat("image");
        trace.setTemperature(0d);
        
        if (StringUtils.isNotBlank(prompt.getPrompt())) {
            trace.setPromptTokens(estimateTextTokens(prompt.getPrompt()));
        }
        
        trace.setCompletionTokens(0);
    }
    
    private void enhanceTraceFromTextResponse(ModelTrace trace, ModelTextResponse response) {
        if (response == null) return;
        
        if (response.getModel() != null) {
            trace.setModel(response.getModel());
        }
        
        if (response.getUsage() != null) {
            Usage usage = response.getUsage();
            if (usage.getPromptTokens() != null) {
                trace.setPromptTokens(usage.getPromptTokens());
            }
            if (usage.getCompletionTokens() != null) {
                trace.setCompletionTokens(usage.getCompletionTokens());
            }
        } else {
            String responseContent = response.getResponse();
            if (StringUtils.isNotBlank(responseContent)) {
                trace.setCompletionTokens(estimateTextTokens(responseContent));
            }
        }
    }
    
    private ModelTextResponse createErrorResponse(ModelTextRequest prompt) {
        return ModelTextResponse.builder()
            .model(prompt != null && prompt.getModel() != null ? 
                  prompt.getModel() : delegate.getModel())
            .build();
    }
    
    private ModelImageResponse createErrorImageResponse(ModelImageRequest prompt) {
        return ModelImageResponse.builder()
            .model(delegate.getModel())
            .build();
    }
    
    private int estimatePromptTokens(ModelTextRequest prompt) {
        if (prompt == null || prompt.getMessages() == null) {
            return 0;
        }
        
        return prompt.getMessages().stream()
            .map(this::estimateContentMessageTokens)
            .mapToInt(Integer::intValue)
            .sum();
    }
    
    private int estimateContentMessageTokens(ModelImageResponse.ModelContentMessage message) {
        if (message == null || message.getContent() == null) {
            return 0;
        }
        
        return message.getContent().stream()
            .filter(element -> element.getType() == ModelTextRequest.MessageType.text)
            .map(element -> estimateTextTokens(element.getText()))
            .mapToInt(Integer::intValue)
            .sum();
    }
    
    private int estimateTextTokens(String text) {
        if (StringUtils.isBlank(text)) {
            return 0;
        }
        
        return tokenizer.estimateTokens(text);
    }
    
    @Override
    public T getWorkflow() {
        return delegate.getWorkflow();
    }
    
    @Override
    public void setWorkflow(T workflow) {
        delegate.setWorkflow(workflow);
    }
    
    @Override
    public String getModel() {
        return delegate.getModel();
    }
    
    @Override
    public void setModel(String model) {
        delegate.setModel(model);
    }
    
    @Override
    public List<String> getSystemMessages() {
        return delegate.getSystemMessages();
    }
    
    @Override
    public void setSystemMessages(List<String> systemMessages) {
        delegate.setSystemMessages(systemMessages);
    }
    
    @Override
    public Double getTemperature() {
        return delegate.getTemperature();
    }
    
    @Override
    public void setTemperature(Double temperature) {
        delegate.setTemperature(temperature);
    }
    
    @Override
    public Double getTopP() {
        return delegate.getTopP();
    }
    
    @Override
    public void setTopP(Double topP) {
        delegate.setTopP(topP);
    }
    
    @Override
    public List<String> getStop() {
        return delegate.getStop();
    }
    
    @Override
    public void setStop(List<String> stop) {
        delegate.setStop(stop);
    }
    
    @Override
    public boolean isJsonObjectSupport() {
        return delegate.isJsonObjectSupport();
    }
    
    @Override
    public void setJsonObjectSupport(boolean jsonObjectSupport) {
        delegate.setJsonObjectSupport(jsonObjectSupport);
    }
    
    @Override
    public Boolean getLogprobs() {
        return delegate.getLogprobs();
    }
    
    @Override
    public void setLogprobs(Boolean logprobs) {
        delegate.setLogprobs(logprobs);
    }
    
    @Override
    public Integer getTopLogprobs() {
        return delegate.getTopLogprobs();
    }
    
    @Override
    public void setTopLogprobs(Integer topLogprobs) {
        delegate.setTopLogprobs(topLogprobs);
    }
    
    @Override
    public Integer getMaxTokens() {
        return delegate.getMaxTokens();
    }
    
    @Override
    public void setMaxTokens(Integer maxTokens) {
        delegate.setMaxTokens(maxTokens);
    }
    
    @Override
    public Integer getMaxCompletionTokens() {
        return delegate.getMaxCompletionTokens();
    }
    
    @Override
    public void setMaxCompletionTokens(Integer maxCompletionTokens) {
        delegate.setMaxCompletionTokens(maxCompletionTokens);
    }
}
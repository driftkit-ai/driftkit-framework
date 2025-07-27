package ai.driftkit.clients.openai.client;

import ai.driftkit.common.domain.client.*;
import ai.driftkit.common.domain.client.ModelClient.ModelClientInit;
import ai.driftkit.common.tools.ToolCall;
import ai.driftkit.config.EtlConfig.VaultConfig;
import ai.driftkit.common.domain.client.ModelImageResponse.ModelContentMessage.ModelContentElement;
import ai.driftkit.common.domain.client.ModelTextRequest.ToolMode;
import ai.driftkit.common.domain.client.ModelTextResponse.ResponseMessage;
import ai.driftkit.common.domain.client.ModelTextResponse.Usage;
import ai.driftkit.common.utils.JsonUtils;
import ai.driftkit.common.utils.ModelUtils;
import ai.driftkit.clients.openai.domain.ChatCompletionRequest;
import ai.driftkit.clients.openai.domain.ChatCompletionRequest.ContentMessage;
import ai.driftkit.clients.openai.domain.ChatCompletionRequest.Message;
import ai.driftkit.clients.openai.domain.ChatCompletionRequest.Message.ContentElement;
import ai.driftkit.clients.openai.domain.ChatCompletionRequest.Message.ImageContentElement;
import ai.driftkit.clients.openai.domain.ChatCompletionRequest.Message.TextContentElement;
import ai.driftkit.clients.openai.domain.ChatCompletionResponse;
import ai.driftkit.clients.openai.domain.CreateImageRequest;
import ai.driftkit.clients.openai.domain.CreateImageRequest.CreateImageRequestBuilder;
import ai.driftkit.clients.openai.domain.CreateImageRequest.Quality;
import ai.driftkit.clients.openai.domain.CreateImageResponse;
import ai.driftkit.clients.openai.utils.OpenAIUtils;
import ai.driftkit.clients.openai.utils.OpenAIUtils.ImageData;
import ai.driftkit.common.domain.client.LogProbs.TokenLogProb;
import ai.driftkit.common.domain.client.LogProbs.TopLogProb;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class OpenAIModelClient extends ModelClient implements ModelClientInit {
    public static final String GPT_DEFAULT = "gpt-4o";
    public static final String GPT_SMART_DEFAULT = "o3-mini";
    public static final String GPT_MINI_DEFAULT = "gpt-4o-mini";

    public static final String OPENAI_PREFIX = "openai";
    public static final String IMAGE_MODEL = "gpt-image-1";

    OpenAIApiClient client;

    @Override
    public ModelClient init(VaultConfig config) {
        this.client = OpenAIClientFactory.createClient(
                config.getApiKey(),
                Optional.ofNullable(config.getBaseUrl()).orElse("https://api.openai.com")
        );
        this.setTemperature(config.getTemperature());
        this.setModel(config.getModel());
        this.setStop(config.getStop());
        this.jsonObjectSupport = config.isJsonObject();
        return this;
    }

    public static ModelClient create(VaultConfig config) {
        OpenAIModelClient modelClient = new OpenAIModelClient();
        modelClient.init(config);
        return modelClient;
    }

    @Override
    public Set<Capability> getCapabilities() {
        return Set.of(
                Capability.TEXT_TO_TEXT,
                Capability.TEXT_TO_IMAGE,
                Capability.FUNCTION_CALLING,
                Capability.IMAGE_TO_TEXT,
                Capability.JSON_OBJECT,
                Capability.TOOLS,
                Capability.JSON_SCHEMA
        );
    }

    @Override
    public ModelTextResponse imageToText(ModelTextRequest prompt) throws UnsupportedCapabilityException {
        super.imageToText(prompt);

        return processPrompt(prompt);
    }

    @Override
    public ModelImageResponse textToImage(ModelImageRequest prompt) {
        super.textToImage(prompt);

        String message = prompt.getPrompt();

        CreateImageRequestBuilder style = CreateImageRequest.builder()
                .prompt(message)
                .quality(Quality.valueOf(prompt.getQuality().name()))
                //.size(prompt.getSize())
                .size("auto")
                .outputFormat("jpeg")
                .compression(90)
                .n(prompt.getN())
                .model(IMAGE_MODEL);

        CreateImageResponse imageResponse = client.createImage(style.build());

        List<ModelContentElement.ImageData> image = imageResponse.getData()
                .stream()
                .map(e -> {
                    ImageData openAIImage = OpenAIUtils.base64toBytes("image/jpeg", e.getB64Json());
                    return new ModelContentElement.ImageData(openAIImage.getImage(), openAIImage.getMimeType());
                })
                .toList();

        return ModelImageResponse.builder()
                .model(IMAGE_MODEL)
                .bytes(image)
                .createdTime(imageResponse.getCreated())
                .revisedPrompt(imageResponse.getData().getFirst().getRevisedPrompt())
                .build();
    }

    @Override
    public ModelTextResponse textToText(ModelTextRequest prompt) {
        super.textToText(prompt);

        return processPrompt(prompt);
    }

    @Nullable
    private ModelTextResponse processPrompt(ModelTextRequest prompt) {
        List<Tool> tools = prompt.getToolMode() == ToolMode.none ? null : getTools();

        if (CollectionUtils.isEmpty(tools)) {
            tools = prompt.getTools();
        }

        String model = Optional.ofNullable(prompt.getModel()).orElse(getModel());
        Double temperature = Optional.ofNullable(prompt.getTemperature()).orElse(getTemperature());
        Boolean logprobs = Optional.ofNullable(prompt.getLogprobs()).orElse(getLogprobs());
        Integer topLogprobs = Optional.ofNullable(prompt.getTopLogprobs()).orElse(getTopLogprobs());

        ChatCompletionRequest.ResponseFormat responseFormat = !jsonObjectSupport || prompt.getResponseFormat() == null ? null : new ChatCompletionRequest.ResponseFormat(
            prompt.getResponseFormat().getType().getValue(),
            convertModelJsonSchema(prompt.getResponseFormat().getJsonSchema())
        );

        ChatCompletionRequest req = ChatCompletionRequest.builder()
                .model(model)
                .n(1)
                .maxTokens(getMaxTokens())
                .maxCompletionTokens(getMaxCompletionTokens())
                .temperature(temperature)
                .tools(tools)
                .responseFormat(responseFormat)
                .logprobs(logprobs)
                .topLogprobs(topLogprobs)
                .messages(
                        prompt.getMessages().stream()
                            .map(this::toMessage)
                            .toList()
                )
                .build();

        try {
            if (model != null && model.startsWith("o")) {
                req.setTemperature(null);
                req.setReasoningEffort(Optional.ofNullable(prompt.getReasoningEffort()).map(Enum::name).orElse("medium"));
            }
            if (BooleanUtils.isNotTrue(req.getLogprobs())) {
                req.setTopLogprobs(null);
            }
            ChatCompletionResponse completion = client.createChatCompletion(req);

            return mapToModelTextResponse(completion);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static ChatCompletionRequest.ResponseFormat.JsonSchema convertModelJsonSchema(ResponseFormat.JsonSchema schemaA) {
        if (schemaA == null) {
            return null;
        }

        Map<String, ChatCompletionRequest.ResponseFormat.Property> properties = schemaA.getProperties() != null
                ? schemaA.getProperties().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> convertSchemaProperty(entry.getValue())
                ))
                : null;

        ChatCompletionRequest.ResponseFormat.SchemaDefinition schemaDefinition = 
                new ChatCompletionRequest.ResponseFormat.SchemaDefinition(
                        schemaA.getType(),
                        properties,
                        schemaA.getRequired()
                );
        schemaDefinition.setAdditionalProperties(schemaA.getAdditionalProperties());

        ChatCompletionRequest.ResponseFormat.JsonSchema result = 
                new ChatCompletionRequest.ResponseFormat.JsonSchema(
                        schemaA.getTitle(),
                        schemaDefinition
                );
        
        // Set strict mode if specified in schema
        if (schemaA.getStrict() != null && schemaA.getStrict()) {
            result.setStrict(true);
        }
        
        return result;
    }

    private static ChatCompletionRequest.ResponseFormat.Property convertSchemaProperty(ResponseFormat.SchemaProperty property) {
        if (property == null) {
            return null;
        }

        Map<String, ChatCompletionRequest.ResponseFormat.Property> nestedProperties = null;
        if (property.getProperties() != null) {
            nestedProperties = property.getProperties().entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> convertSchemaProperty(entry.getValue())
                ));
        }

        ChatCompletionRequest.ResponseFormat.Property items = null;
        if (property.getItems() != null) {
            items = convertSchemaProperty(property.getItems());
        }

        ChatCompletionRequest.ResponseFormat.Property result = new ChatCompletionRequest.ResponseFormat.Property(
                property.getType(),
                property.getDescription(),
                property.getEnumValues()
        );

        if (nestedProperties != null) {
            result.setProperties(nestedProperties);
        }
        if (property.getRequired() != null) {
            result.setRequired(property.getRequired());
        }
        if (items != null) {
            result.setItems(items);
        }
        
        // For objects, always set additionalProperties to false if not explicitly set
        if (ResponseFormatType.Object.getType().equals(property.getType())) {
            result.setAdditionalProperties(property.getAdditionalProperties() != null ? 
                property.getAdditionalProperties() : false);
        } else if (property.getAdditionalProperties() != null) {
            result.setAdditionalProperties(property.getAdditionalProperties());
        }

        return result;
    }

    public static ModelTextResponse mapToModelTextResponse(ChatCompletionResponse completion) {
        if (completion == null) {
            return null;
        }

        var usage = completion.getUsage();

        List<ResponseMessage> choices = completion.getChoices() != null ?
                completion.getChoices().stream()
                        .map(OpenAIModelClient::mapChoice)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList())
                : null;

        return ModelTextResponse.builder()
                .id(completion.getId())
                .method(completion.getObject())
                .createdTime(completion.getCreated())
                .model(completion.getModel())
                .usage(usage == null ? null : new Usage(
                        usage.getPromptTokens(),
                        usage.getCompletionTokens(),
                        usage.getTotalTokens()
                ))
                .choices(choices)
                .build();
    }

    private static ModelTextResponse.ResponseMessage mapChoice(ChatCompletionResponse.Choice choice) {
        if (choice == null) {
            return null;
        }

        return ModelTextResponse.ResponseMessage.builder()
                .index(choice.getIndex())
                .message(mapMessage(choice.getMessage()))
                .finishReason(choice.getFinishReason())
                .logprobs(mapLogProbs(choice.getLogprobs()))
                .build();
    }
    
    private static LogProbs mapLogProbs(ChatCompletionResponse.LogProbs logProbs) {
        if (logProbs == null) {
            return null;
        }
        
        List<TokenLogProb> tokenLogprobs = null;
        
        if (logProbs.getContent() != null) {
            tokenLogprobs = logProbs.getContent().stream()
                .map(token -> {
                    List<TopLogProb> topLogprobs = null;
                    
                    if (token.getTopLogprobs() != null) {
                        topLogprobs = token.getTopLogprobs().stream()
                            .map(top -> TopLogProb.builder()
                                .token(top.getToken())
                                .logprob(top.getLogprob())
                                .build())
                            .toList();
                    }
                    
                    return TokenLogProb.builder()
                        .token(token.getToken())
                        .logprob(token.getLogprob())
                        .bytes(token.getBytes())
                        .topLogprobs(topLogprobs)
                        .build();
                })
                .toList();
        }
        
        return LogProbs.builder()
            .content(tokenLogprobs)
            .build();
    }

    private static ModelImageResponse.ModelMessage mapMessage(ChatCompletionResponse.Message message) {
        if (message == null) {
            return null;
        }

        String content = message.getContent();

        if (JsonUtils.isJSON(content) && !JsonUtils.isValidJSON(content)) {
            content = JsonUtils.fixIncompleteJSON(content);
        }

        List<ToolCall> toolCalls = null;
        if (message.getToolCalls() != null) {
            toolCalls = message.getToolCalls().stream()
                    .map(OpenAIModelClient::mapToolCall)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }

        return ModelImageResponse.ModelMessage.builder()
                .role(Role.valueOf(message.getRole()))
                .content(content)
                .toolCalls(toolCalls)
                .build();
    }

    private static ToolCall mapToolCall(ChatCompletionResponse.ToolCall toolCall) {
        if (toolCall == null) {
            return null;
        }

        ToolCall.FunctionCall function = null;
        if (toolCall.getFunction() != null) {
            Map<String, JsonNode> arguments = parseJsonStringToNodeMap(toolCall.getFunction().getArguments());
            function = ToolCall.FunctionCall.builder()
                    .name(toolCall.getFunction().getName())
                    .arguments(arguments)
                    .build();
        }

        return ToolCall.builder()
                .id(toolCall.getId())
                .type(toolCall.getType())
                .function(function)
                .build();
    }

    private static Map<String, JsonNode> parseJsonStringToNodeMap(String jsonString) {
        try {
            if (jsonString == null || jsonString.trim().isEmpty()) {
                return new HashMap<>();
            }
            JsonNode rootNode = ModelUtils.OBJECT_MAPPER.readTree(jsonString);
            
            if (!rootNode.isObject()) {
                throw new IllegalArgumentException("Expected JSON object but got: " + rootNode.getNodeType());
            }
            
            Map<String, JsonNode> result = new HashMap<>();
            rootNode.fields().forEachRemaining(entry -> {
                result.put(entry.getKey(), entry.getValue());
            });
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse tool call arguments: " + e.getMessage(), e);
        }
    }

    private Message toMessage(ModelImageResponse.ModelContentMessage message) {
//        if (message.getContent().size() == 1) {
//            ModelContentElement mce = message.getContent().get(0);
//
//            if (mce.getType() == MessageType.text) {
//                String text = mce.getText();
//                return new StringMessage(message.getRole().name(), message.getName(), text);
//            }
//        }

        List<ContentElement> elements = message.getContent().stream()
                .map(e -> {
                    switch (e.getType()) {
                        case image -> {
                            return new ImageContentElement(e.getImage().getImage());
                        }
                        case text -> {
                            String text = e.getText();

                            if (text.startsWith("\"")) {
                                text = text.substring(1, text.length() - 1);
                            }

                            return new TextContentElement(text);
                        }
                    }
                    return null;
                }).filter(Objects::nonNull).toList();

        return new ContentMessage(message.getRole().name(), message.getName(), elements);
    }
}

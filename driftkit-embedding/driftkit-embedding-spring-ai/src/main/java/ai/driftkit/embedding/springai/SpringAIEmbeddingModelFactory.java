package ai.driftkit.embedding.springai;

import ai.driftkit.config.EtlConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.azure.openai.AzureOpenAiEmbeddingModel;
import org.springframework.ai.azure.openai.AzureOpenAiEmbeddingOptions;
import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.web.client.RestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.ai.retry.RetryUtils;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.document.MetadataMode;

import java.util.Map;

/**
 * Factory for creating Spring AI embedding models based on provider configuration.
 * Supports multiple embedding providers including OpenAI, Azure OpenAI, and Ollama.
 */
@Slf4j
public class SpringAIEmbeddingModelFactory {

    private static final String DEFAULT_OPENAI_URL = "https://api.openai.com";
    private static final String DEFAULT_OPENAI_MODEL = "text-embedding-3-small";
    private static final String DEFAULT_OLLAMA_URL = "http://localhost:11434";
    private static final String DEFAULT_OLLAMA_MODEL = "all-minilm";

    /**
     * Creates an embedding model based on the specified provider and configuration.
     *
     * @param provider the embedding provider name (openai, azure-openai, ollama)
     * @param config configuration map containing provider-specific settings
     * @return configured EmbeddingModel instance
     * @throws IllegalArgumentException if provider is empty or unsupported
     */
    public EmbeddingModel createEmbeddingModel(String provider, Map<String, String> config) {
        if (StringUtils.isEmpty(provider)) {
            throw new IllegalArgumentException("Provider must not be empty");
        }

        log.debug("Creating Spring AI embedding model for provider: {}", provider);

        switch (provider.toLowerCase()) {
            case "openai":
                return createOpenAIModel(config);
            case "azure-openai":
                return createAzureOpenAIModel(config);
            case "ollama":
                return createOllamaModel(config);
            default:
                throw new IllegalArgumentException("Unsupported Spring AI provider: " + provider + 
                    ". Supported providers: openai, azure-openai, ollama");
        }
    }

    private EmbeddingModel createOpenAIModel(Map<String, String> config) {
        String apiKey = config.get(EtlConfig.API_KEY);
        if (StringUtils.isEmpty(apiKey)) {
            throw new IllegalArgumentException("OpenAI API key is required");
        }

        String baseUrl = config.getOrDefault(EtlConfig.HOST, DEFAULT_OPENAI_URL);
        String model = config.getOrDefault(EtlConfig.MODEL_NAME, DEFAULT_OPENAI_MODEL);

        // Validate URL format
        if (!isValidUrl(baseUrl)) {
            throw new IllegalArgumentException("Invalid OpenAI base URL: " + baseUrl);
        }

        // Create OpenAI embedding options
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(model)
                .build();

        // Create OpenAI API using the builder pattern if available
        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();

        log.info("Created OpenAI embedding model with model: {} and endpoint: {}", model, baseUrl);
        
        // Create OpenAI embedding model with correct parameters
        // The second parameter is MetadataMode, not options
        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, options);
    }

    private EmbeddingModel createAzureOpenAIModel(Map<String, String> config) {
        String apiKey = config.get(EtlConfig.API_KEY);
        String endpoint = config.get("endpoint");
        String deploymentName = config.get("deployment-name");
        String apiVersion = config.get("api-version");

        // Validate required parameters
        if (StringUtils.isEmpty(apiKey)) {
            throw new IllegalArgumentException("Azure OpenAI API key is required");
        }
        if (StringUtils.isEmpty(endpoint)) {
            throw new IllegalArgumentException("Azure OpenAI endpoint is required");
        }
        if (StringUtils.isEmpty(deploymentName)) {
            throw new IllegalArgumentException("Azure OpenAI deployment name is required");
        }

        // Validate endpoint format
        if (!isValidUrl(endpoint)) {
            throw new IllegalArgumentException("Invalid Azure OpenAI endpoint: " + endpoint);
        }

        AzureOpenAiEmbeddingOptions.Builder optionsBuilder = AzureOpenAiEmbeddingOptions.builder()
                .deploymentName(deploymentName);

        // Create Azure OpenAI client
        com.azure.ai.openai.OpenAIClient azureClient = new com.azure.ai.openai.OpenAIClientBuilder()
                .endpoint(endpoint)
                .credential(new com.azure.core.credential.AzureKeyCredential(apiKey))
                .buildClient();

        AzureOpenAiEmbeddingOptions options = AzureOpenAiEmbeddingOptions.builder()
                .deploymentName(deploymentName)
                .build();

        // Create Azure OpenAI embedding model with correct parameters
        // The second parameter is MetadataMode
        AzureOpenAiEmbeddingModel model = new AzureOpenAiEmbeddingModel(azureClient, MetadataMode.EMBED, options);

        log.info("Created Azure OpenAI embedding model with deployment: {} and endpoint: {}", 
                deploymentName, endpoint);
        return model;
    }

    private EmbeddingModel createOllamaModel(Map<String, String> config) {
        String baseUrl = config.getOrDefault(EtlConfig.HOST, DEFAULT_OLLAMA_URL);
        String model = config.getOrDefault(EtlConfig.MODEL_NAME, DEFAULT_OLLAMA_MODEL);

        // Validate URL format
        if (!isValidUrl(baseUrl)) {
            throw new IllegalArgumentException("Invalid Ollama base URL: " + baseUrl);
        }

        // Create Ollama options
        OllamaOptions options = OllamaOptions.builder()
                .model(model)
                .build();

        // Create OllamaApi using the builder pattern
        OllamaApi ollamaApi = OllamaApi.builder()
                .baseUrl(baseUrl)
                .build();

        log.info("Created Ollama embedding model with model: {} and endpoint: {}", model, baseUrl);
        
        // Create Ollama embedding model with required parameters
        // Parameters: OllamaApi, OllamaOptions, ObservationRegistry, ModelManagementOptions
        ObservationRegistry observationRegistry = ObservationRegistry.NOOP; // Use NOOP registry if not configured
        ModelManagementOptions modelManagementOptions = ModelManagementOptions.defaults(); // Default model management
        
        return new OllamaEmbeddingModel(ollamaApi, options, observationRegistry, modelManagementOptions);
    }

    /**
     * Validates if the given string is a valid URL.
     *
     * @param url the URL string to validate
     * @return true if valid, false otherwise
     */
    private boolean isValidUrl(String url) {
        if (StringUtils.isEmpty(url)) {
            return false;
        }
        
        try {
            new java.net.URL(url);
            return true;
        } catch (java.net.MalformedURLException e) {
            return false;
        }
    }
}
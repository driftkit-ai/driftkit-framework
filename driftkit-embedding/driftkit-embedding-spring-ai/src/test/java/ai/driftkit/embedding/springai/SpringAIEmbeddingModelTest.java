package ai.driftkit.embedding.springai;

import ai.driftkit.config.EtlConfig;
import ai.driftkit.config.EtlConfig.EmbeddingServiceConfig;
import ai.driftkit.embedding.core.domain.Embedding;
import ai.driftkit.embedding.core.domain.TextSegment;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SpringAIEmbeddingModelTest {

    private SpringAIEmbeddingModel embeddingModel;

    @BeforeEach
    void setUp() {
        embeddingModel = new SpringAIEmbeddingModel();
    }

    @Test
    void testSupportsName() {
        assertTrue(embeddingModel.supportsName("spring-ai"));
        assertFalse(embeddingModel.supportsName("openai"));
        assertFalse(embeddingModel.supportsName("cohere"));
    }

    @Test
    void testModelThrowsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> embeddingModel.model());
    }

    @Test
    void testConfigureWithMissingProvider() {
        Map<String, String> config = new HashMap<>();
        config.put(EtlConfig.MODEL_NAME, "text-embedding-ada-002");
        
        EmbeddingServiceConfig serviceConfig = new EmbeddingServiceConfig("spring-ai", config);
        
        assertThrows(IllegalArgumentException.class, () -> embeddingModel.configure(serviceConfig));
    }

    @Test
    void testEmbedAllWithUnconfiguredModel() {
        List<TextSegment> segments = List.of(TextSegment.from("test"));
        
        assertThrows(IllegalStateException.class, () -> embeddingModel.embedAll(segments));
    }

    @Test
    void testEstimateTokenCount() {
        assertEquals(0, embeddingModel.estimateTokenCount(null));
        assertEquals(0, embeddingModel.estimateTokenCount(""));
        assertEquals(0, embeddingModel.estimateTokenCount("   "));
        
        // Just check that it returns a positive number for non-empty text
        assertTrue(embeddingModel.estimateTokenCount("Hello world") > 0);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    void testEmbedWithOpenAI() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        
        // Configure the model
        Map<String, String> config = new HashMap<>();
        config.put("provider", "openai");
        config.put(EtlConfig.MODEL_NAME, "text-embedding-3-small");
        config.put(EtlConfig.API_KEY, apiKey);
        
        EmbeddingServiceConfig serviceConfig = new EmbeddingServiceConfig("spring-ai", config);
        embeddingModel.configure(serviceConfig);
        
        // Test single embedding
        TextSegment segment = TextSegment.from("Hello world, this is a test");
        Response<Embedding> response = embeddingModel.embed(segment);
        
        assertNotNull(response);
        assertNotNull(response.content());
        assertTrue(response.content().dimension() > 0);
        assertNotNull(response.tokenUsage());
        assertTrue(response.tokenUsage().inputTokenCount() > 0);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    void testEmbedAllWithOpenAI() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        
        // Configure the model
        Map<String, String> config = new HashMap<>();
        config.put("provider", "openai");
        config.put(EtlConfig.MODEL_NAME, "text-embedding-3-small");
        config.put(EtlConfig.API_KEY, apiKey);
        
        EmbeddingServiceConfig serviceConfig = new EmbeddingServiceConfig("spring-ai", config);
        embeddingModel.configure(serviceConfig);
        
        // Test multiple embeddings
        List<TextSegment> segments = Arrays.asList(
            TextSegment.from("First test segment"),
            TextSegment.from("Second test segment"),
            TextSegment.from("Third test segment")
        );
        
        Response<List<Embedding>> response = embeddingModel.embedAll(segments);
        
        assertNotNull(response);
        assertNotNull(response.content());
        assertEquals(3, response.content().size());
        
        for (Embedding embedding : response.content()) {
            assertNotNull(embedding);
            assertTrue(embedding.dimension() > 0);
        }
        
        assertNotNull(response.tokenUsage());
        assertTrue(response.tokenUsage().inputTokenCount() > 0);
    }

    @Test
    void testConfigureWithOllama() {
        Map<String, String> config = new HashMap<>();
        config.put("provider", "ollama");
        config.put(EtlConfig.MODEL_NAME, "all-minilm");
        config.put(EtlConfig.HOST, "http://localhost:11434");
        
        EmbeddingServiceConfig serviceConfig = new EmbeddingServiceConfig("spring-ai", config);
        
        // Should not throw exception during configuration
        assertDoesNotThrow(() -> embeddingModel.configure(serviceConfig));
    }

    @Test
    void testConfigureWithUnsupportedProvider() {
        Map<String, String> config = new HashMap<>();
        config.put("provider", "unsupported-provider");
        config.put(EtlConfig.MODEL_NAME, "some-model");
        
        EmbeddingServiceConfig serviceConfig = new EmbeddingServiceConfig("spring-ai", config);
        
        assertThrows(RuntimeException.class, () -> embeddingModel.configure(serviceConfig));
    }
}
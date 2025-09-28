package ai.driftkit.clients.gemini.client;

import ai.driftkit.clients.gemini.utils.GeminiUtils;
import ai.driftkit.common.domain.client.*;
import ai.driftkit.common.domain.client.ModelContentMessage;
import ai.driftkit.config.EtlConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
public class GeminiModelClientFactoryTest {
    
    @Test
    void testServiceLoaderFindsGeminiClient() {
        // Test that ServiceLoader can find GeminiModelClient
        ServiceLoader<ModelClient> loader = ServiceLoader.load(ModelClient.class);
        
        boolean foundGemini = false;
        for (ModelClient client : loader) {
            if (client instanceof GeminiModelClient) {
                foundGemini = true;
                break;
            }
        }
        
        assertTrue(foundGemini, "GeminiModelClient should be discoverable via ServiceLoader");
    }
    
    @Test
    void testDirectClientCreation() {
        String apiKey = System.getenv("GEMINI_API_KEY");
        
        EtlConfig.VaultConfig config = new EtlConfig.VaultConfig();
        config.setName("gemini-direct");
        config.setApiKey(apiKey);
        config.setModel(GeminiUtils.GEMINI_FLASH_2_5);
        config.setTemperature(0.5);
        
        // Test direct creation
        ModelClient client = GeminiModelClient.create(config);
        
        assertNotNull(client);
        assertTrue(client instanceof GeminiModelClient);
        assertEquals(GeminiUtils.GEMINI_FLASH_2_5, client.getModel());
        assertEquals(0.5, client.getTemperature());
        
        // Test it actually works
        ModelTextRequest request = ModelTextRequest.builder()
                .messages(List.of(
                        ModelContentMessage.create(Role.user, "Say 'test successful'")
                ))
                .build();
        
        ModelTextResponse response = client.textToText(request);
        
        assertNotNull(response);
        assertNotNull(response.getResponse());
        assertTrue(response.getResponse().toLowerCase().contains("successful") || 
                   response.getResponse().toLowerCase().contains("test"));
    }
    
    @Test
    void testConfigurationOptions() {
        String apiKey = System.getenv("GEMINI_API_KEY");
        
        EtlConfig.VaultConfig config = new EtlConfig.VaultConfig();
        config.setName("gemini-config-test");
        config.setApiKey(apiKey);
        config.setModel(GeminiUtils.GEMINI_FLASH_LITE_2_5);
        config.setTemperature(0.9);
        config.setMaxTokens(100);
        config.setStop(List.of("STOP", "END"));
        config.setJsonObject(true);
        
        GeminiModelClient client = new GeminiModelClient();
        client.init(config);
        
        // Verify configuration was applied
        assertEquals(GeminiUtils.GEMINI_FLASH_LITE_2_5, client.getModel());
        assertEquals(0.9, client.getTemperature());
        assertEquals(100, client.getMaxTokens());
        assertEquals(List.of("STOP", "END"), client.getStop());
    }
    
    @Test
    void testDefaultModels() {
        // Test default model constants
        assertEquals(GeminiUtils.GEMINI_FLASH_2_5, GeminiModelClient.GEMINI_DEFAULT);
        assertEquals(GeminiUtils.GEMINI_PRO_2_5, GeminiModelClient.GEMINI_SMART_DEFAULT);
        assertEquals(GeminiUtils.GEMINI_FLASH_LITE_2_5, GeminiModelClient.GEMINI_MINI_DEFAULT);
        assertEquals(GeminiUtils.GEMINI_IMAGE_MODEL, GeminiModelClient.GEMINI_IMAGE_DEFAULT);
    }
    
    @Test
    void testModelClientInit() {
        String apiKey = System.getenv("GEMINI_API_KEY");
        
        EtlConfig.VaultConfig config = new EtlConfig.VaultConfig();
        config.setName("gemini-init-test");
        config.setApiKey(apiKey);
        
        // Create client via ModelClientInit interface
        GeminiModelClient geminiClient = new GeminiModelClient();
        ModelClient.ModelClientInit initClient = geminiClient;
        ModelClient initializedClient = initClient.init(config);
        
        assertNotNull(initializedClient);
        assertSame(geminiClient, initializedClient);
    }
}
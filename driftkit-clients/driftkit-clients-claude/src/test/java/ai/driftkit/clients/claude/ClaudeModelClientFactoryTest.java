package ai.driftkit.clients.claude;

import ai.driftkit.clients.claude.client.ClaudeModelClient;
import ai.driftkit.clients.core.ModelClientFactory;
import ai.driftkit.common.domain.client.ModelClient;
import ai.driftkit.config.EtlConfig.VaultConfig;
import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.*;

public class ClaudeModelClientFactoryTest {
    
    @Test
    void testCreateClaudeClient() {
        VaultConfig config = VaultConfig.builder()
                .name("claude")  // Changed to match class name check
                .type("claude")
                .apiKey("test-api-key")
                .model("claude-sonnet-4-20250514")
                .temperature(0.7)
                .maxTokens(1000)
                .build();
        
        ModelClient client = ModelClientFactory.fromConfig(config);
        
        assertNotNull(client);
        assertTrue(client instanceof ClaudeModelClient);
        assertEquals("claude-sonnet-4-20250514", client.getModel());
        assertEquals(0.7, client.getTemperature());
    }
    
    @Test
    void testClaudeClientCapabilities() {
        VaultConfig config = VaultConfig.builder()
                .name("claude")  // Changed to match class name check
                .type("claude")
                .apiKey("test-api-key")
                .build();
        
        ModelClient client = ModelClientFactory.fromConfig(config);
        
        assertTrue(client.getCapabilities().contains(ModelClient.Capability.TEXT_TO_TEXT));
        assertTrue(client.getCapabilities().contains(ModelClient.Capability.IMAGE_TO_TEXT));
        assertTrue(client.getCapabilities().contains(ModelClient.Capability.FUNCTION_CALLING));
        assertTrue(client.getCapabilities().contains(ModelClient.Capability.TOOLS));
        assertFalse(client.getCapabilities().contains(ModelClient.Capability.TEXT_TO_IMAGE));
    }
    
    @Test
    void testServiceLoaderDiscovery() {
        // This test verifies that ClaudeModelClient is discoverable via ServiceLoader
        boolean foundClaude = false;
        
        for (ModelClient client : ServiceLoader.load(ModelClient.class)) {
            if (client instanceof ClaudeModelClient) {
                foundClaude = true;
                break;
            }
        }
        
        assertTrue(foundClaude, "ClaudeModelClient should be discoverable via ServiceLoader");
    }
}
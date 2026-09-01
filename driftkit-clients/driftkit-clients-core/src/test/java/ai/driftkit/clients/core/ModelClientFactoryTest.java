package ai.driftkit.clients.core;

import ai.driftkit.common.domain.client.ModelClient;
import ai.driftkit.config.EtlConfig.VaultConfig;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Uses the two fake providers registered in src/test/resources/META-INF/services:
 * {@link FakeAlphaModelClient} (id "fakealpha") and {@link FakeBetaModelClient} (id "fakebeta").
 */
public class ModelClientFactoryTest {

    @Test
    public void typeTakesPrecedenceOverName() {
        VaultConfig config = vault("primary", "fakealpha");

        assertEquals("fakealpha", ModelClientFactory.resolveClientType(config));
        assertTrue(ModelClientFactory.fromConfig(config) instanceof FakeAlphaModelClient);
    }

    @Test
    public void nameIsUsedWhenTypeIsMissing() {
        VaultConfig config = vault("fakebeta", null);

        assertEquals("fakebeta", ModelClientFactory.resolveClientType(config));
        assertTrue(ModelClientFactory.fromConfig(config) instanceof FakeBetaModelClient);
    }

    @Test
    public void blankTypeFallsBackToName() {
        assertEquals("fakealpha", ModelClientFactory.resolveClientType(vault("fakealpha", "   ")));
    }

    @Test
    public void providerIdMayBeEmbeddedInALongerName() {
        // "primary-fakealpha" contains the provider id, "fake" alone does not name a provider
        assertTrue(ModelClientFactory.fromConfig(vault("primary-fakealpha", null)) instanceof FakeAlphaModelClient);
        assertFalse(ModelClientFactory.supportsClientName(new FakeAlphaModelClient(), "fake"));
        assertFalse(ModelClientFactory.supportsClientName(new FakeAlphaModelClient(), "alpha"));
    }

    @Test
    public void sameNameDifferentTypesDoNotAlias() {
        ModelClient<?> alpha = ModelClientFactory.fromConfig(vault("shared", "fakealpha"));
        ModelClient<?> beta = ModelClientFactory.fromConfig(vault("shared", "fakebeta"));

        assertTrue(alpha instanceof FakeAlphaModelClient);
        assertTrue(beta instanceof FakeBetaModelClient);
        assertNotSame(alpha, beta);
        assertSame(alpha, ModelClientFactory.fromConfig(vault("shared", "fakealpha")));
    }

    @Test
    public void ambiguousIdFailsInsteadOfPickingTheFirstProvider() {
        try {
            ModelClientFactory.fromConfig(vault("both", "fakealpha-fakebeta"));
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Ambiguous"));
        }
    }

    @Test
    public void unknownTypeListsDiscoveredProviders() {
        try {
            ModelClientFactory.fromConfig(vault("primary", "no-such-provider"));
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("no-such-provider"));
            assertTrue(e.getMessage(), e.getMessage().contains("primary"));
            assertTrue(e.getMessage(), e.getMessage().contains("fakealpha"));
            assertTrue(e.getMessage(), e.getMessage().contains("fakebeta"));
        }
    }

    @Test
    public void initFailureIsWrappedWithProviderId() {
        VaultConfig config = vault("broken", "fakebeta");
        config.setApiKey(FakeBetaModelClient.FAIL_INIT_KEY);
        try {
            ModelClientFactory.fromConfig(config);
            fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("fakebeta"));
            assertTrue(e.getCause() instanceof IllegalStateException);
        }
    }

    private static VaultConfig vault(String name, String type) {
        VaultConfig config = new VaultConfig();
        config.setName(name);
        config.setType(type);
        return config;
    }
}

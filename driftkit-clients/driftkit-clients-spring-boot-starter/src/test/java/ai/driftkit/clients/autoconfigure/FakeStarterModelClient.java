package ai.driftkit.clients.autoconfigure;

import ai.driftkit.common.domain.client.ModelClient;
import ai.driftkit.config.EtlConfig.VaultConfig;

import java.util.Set;

/** Test-only provider registered via src/test/resources/META-INF/services; provider id "fakestarter". */
public class FakeStarterModelClient extends ModelClient<Object> implements ModelClient.ModelClientInit {

    @Override
    public ModelClient init(VaultConfig config) {
        return new FakeStarterModelClient();
    }

    @Override
    public Set<Capability> getCapabilities() {
        return Set.of(Capability.TEXT_TO_TEXT);
    }
}

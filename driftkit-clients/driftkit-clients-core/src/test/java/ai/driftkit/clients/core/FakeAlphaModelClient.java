package ai.driftkit.clients.core;

import ai.driftkit.common.domain.client.ModelClient;
import ai.driftkit.config.EtlConfig.VaultConfig;

import java.util.Set;

/** Test-only provider discovered through ServiceLoader; provider id "fakealpha". */
public class FakeAlphaModelClient extends ModelClient<Object> implements ModelClient.ModelClientInit {

    @Override
    public ModelClient init(VaultConfig config) {
        return new FakeAlphaModelClient();
    }

    @Override
    public Set<Capability> getCapabilities() {
        return Set.of(Capability.TEXT_TO_TEXT);
    }
}

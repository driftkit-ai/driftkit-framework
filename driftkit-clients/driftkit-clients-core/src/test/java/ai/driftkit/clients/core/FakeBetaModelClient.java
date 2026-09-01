package ai.driftkit.clients.core;

import ai.driftkit.common.domain.client.ModelClient;
import ai.driftkit.config.EtlConfig.VaultConfig;

import java.util.Set;

/** Test-only provider discovered through ServiceLoader; provider id "fakebeta". */
public class FakeBetaModelClient extends ModelClient<Object> implements ModelClient.ModelClientInit {

    static final String FAIL_INIT_KEY = "fail-init";

    @Override
    public ModelClient init(VaultConfig config) {
        if (FAIL_INIT_KEY.equals(config.getApiKey())) {
            throw new IllegalStateException("init failed on purpose");
        }
        return new FakeBetaModelClient();
    }

    @Override
    public Set<Capability> getCapabilities() {
        return Set.of(Capability.TEXT_TO_TEXT);
    }
}

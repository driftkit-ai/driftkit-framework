package ai.driftkit.context.core.service;

import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.ServiceLoader;

public class PromptServiceFactory {
    public static PromptServiceBase fromName(String name, Map<String, String> config) throws Exception {
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("Name must not be null");
        }

        ServiceLoader<PromptServiceBase> loader = ServiceLoader.load(PromptServiceBase.class);

        for (PromptServiceBase store : loader) {
            if (store.supportsName(name)) {
                store.configure(config);
                return store;
            }
        }

        throw new IllegalArgumentException("Unknown or unavailable prompt service: " + name);
    }
}

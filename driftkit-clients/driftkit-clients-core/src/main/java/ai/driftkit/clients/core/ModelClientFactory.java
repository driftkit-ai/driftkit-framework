package ai.driftkit.clients.core;

import ai.driftkit.config.EtlConfig.VaultConfig;
import ai.driftkit.common.domain.client.ModelClient;
import ai.driftkit.common.service.TextTokenizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This factory creates instances of model clients from config.
 * Uses ServiceLoader to dynamically discover available model client implementations.
 * <p>
 * Provider selection: the provider id is {@link VaultConfig#getType() type} when set, otherwise
 * {@link VaultConfig#getName() name}. A provider id matches an implementation whose class name is
 * {@code <ProviderId>ModelClient} (case-insensitive, non-alphanumerics ignored), either exactly
 * ({@code openai}) or as a suffix/substring of the id ({@code primary-openai}). Ambiguous ids fail fast.
 */
public class ModelClientFactory {
    private static final String CLASS_SUFFIX = "modelclient";

    private static final Map<String, ModelClient<?>> clients = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public static <T> ModelClient<T> fromConfig(VaultConfig config) {
        if (config == null || config.getName() == null) {
            throw new IllegalArgumentException("Configuration and client name must not be null");
        }

        String providerId = resolveClientType(config);
        // Two entries with the same name but different providers must not alias each other
        String cacheKey = normalize(providerId) + "|" + config.getName();

        return (ModelClient<T>) clients.computeIfAbsent(cacheKey, key -> {
            ServiceLoader<ModelClient> loader = ServiceLoader.load(ModelClient.class);

            List<ModelClient<?>> matches = new ArrayList<>();
            List<String> discovered = new ArrayList<>();
            for (ModelClient<?> client : loader) {
                if (!(client instanceof ModelClient.ModelClientInit)) {
                    continue;
                }
                discovered.add(providerIdOf(client));
                if (supportsClientName(client, providerId)) {
                    matches.add(client);
                }
            }

            if (matches.isEmpty()) {
                throw new IllegalArgumentException("Unknown or unavailable model client '" + providerId
                        + "' for vault entry '" + config.getName() + "'. Discovered providers: " + discovered
                        + ". Set 'type' (or 'name') to one of them and make sure the matching driftkit-clients-*"
                        + " module is on the classpath");
            }
            if (matches.size() > 1) {
                List<String> ids = new ArrayList<>();
                for (ModelClient<?> m : matches) {
                    ids.add(providerIdOf(m));
                }
                throw new IllegalArgumentException("Ambiguous model client id '" + providerId + "' for vault entry '"
                        + config.getName() + "': matches " + ids + ". Use an exact provider id in 'type'");
            }

            ModelClient<?> client = matches.get(0);
            try {
                ModelClient<?> configuredClient = ((ModelClient.ModelClientInit) client).init(config);
                if (config.isTracing()) {
                    return new TraceableModelClient<>(configuredClient);
                }
                return configuredClient;
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize client: " + providerId, e);
            }
        });
    }

    /**
     * Same as {@link #fromConfig(VaultConfig)} but, when tracing is enabled, the returned
     * {@link TraceableModelClient} counts tokens with the given tokenizer. A cached traceable client
     * created without a tokenizer is re-wrapped around its delegate so the tokenizer is not lost.
     */
    @SuppressWarnings("unchecked")
    public static <T> ModelClient<T> fromConfig(VaultConfig config, TextTokenizer tokenizer) {
        ModelClient<T> baseClient = fromConfig(config);

        if (!config.isTracing() || tokenizer == null) {
            return baseClient;
        }
        if (baseClient instanceof TraceableModelClient<T> traceable) {
            if (traceable.getTokenizer() == tokenizer) {
                return traceable;
            }
            return new TraceableModelClient<>(traceable.getDelegate(), tokenizer);
        }
        return new TraceableModelClient<>(baseClient, tokenizer);
    }

    public static <T> TraceableModelClient<T> createTraceable(ModelClient<T> delegate, TextTokenizer tokenizer) {
        return new TraceableModelClient<>(delegate, tokenizer);
    }

    /**
     * Resolves which provider implementation to use for a vault entry.
     * <p>
     * {@code type} is the provider identifier (openai, gemini, claude, deepseek); {@code name} is the logical
     * name of the entry. For backwards compatibility, when {@code type} is not set the {@code name} is used
     * as the provider identifier, so configs like {@code name: openai} keep working.
     */
    static String resolveClientType(VaultConfig config) {
        String type = config.getType();
        if (type != null && !type.isBlank()) {
            return type;
        }
        return config.getName();
    }

    /**
     * Provider id derived from the implementation class name: {@code OpenAIModelClient} -> {@code openai}.
     */
    static String providerIdOf(ModelClient<?> client) {
        String simpleName = normalize(client.getClass().getSimpleName());
        if (simpleName.endsWith(CLASS_SUFFIX) && simpleName.length() > CLASS_SUFFIX.length()) {
            return simpleName.substring(0, simpleName.length() - CLASS_SUFFIX.length());
        }
        return simpleName;
    }

    /**
     * Checks if the client supports the given provider id: exact match, or the id contains the provider id
     * ({@code primary-openai} selects {@code OpenAIModelClient}).
     */
    static boolean supportsClientName(ModelClient<?> client, String clientName) {
        String normalized = normalize(clientName);
        if (normalized.isEmpty()) {
            return false;
        }
        String providerId = providerIdOf(client);
        return normalized.equals(providerId) || normalized.contains(providerId);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}

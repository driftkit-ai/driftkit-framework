package ai.driftkit.context.spring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Starts DriftKit as a long-running dev server.
 * Run this test to start the backend for frontend development.
 *
 * Usage:
 *   DEEPSEEK_API_KEY=sk-... mvn test \
 *     -pl driftkit-context-engineering/driftkit-context-engineering-spring-boot-starter \
 *     -Dtest=DriftKitDevServer -am -Dsurefire.failIfNoSpecifiedTests=false
 *
 * Then start frontend:
 *   cd .../src/main/frontend && npx vite
 *   → http://localhost:8080/prompt-engineering/
 */
public class DriftKitDevServer {

    @Test
    @EnabledIfEnvironmentVariable(named = "DRIFTKIT_DEV", matches = "true")
    void startDevServer() throws Exception {
        System.out.println("Starting DriftKit dev server on port 8085...");
        DriftKitDevRunner.main(new String[]{});
        // Block forever — server runs until killed
        Thread.currentThread().join();
    }
}

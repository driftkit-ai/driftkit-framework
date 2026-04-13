package ai.driftkit.context.spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Development runner for the DriftKit Context Engineering platform.
 *
 * Starts full Spring Boot app with:
 * - All prompt management REST endpoints (/data/v1.0/admin/prompt/*)
 * - Test set & evaluation endpoints (/data/v1.0/admin/test-sets/*)
 * - Analytics & tracing endpoints (/data/v1.0/analytics/*)
 * - Pipeline endpoints (/data/v1.0/admin/pipelines/*)
 * - LLM execution endpoints (/data/v1.0/admin/llm/*)
 * - MongoDB for persistence
 *
 * Prerequisites:
 * - MongoDB running on localhost:27017
 * - Set env vars: OPENAI_API_KEY or DEEPSEEK_API_KEY for LLM calls
 *
 * Run:
 *   mvn test -pl driftkit-context-engineering/driftkit-context-engineering-spring-boot-starter \
 *     -Dtest=DriftKitDevRunner -am -Dsurefire.failIfNoSpecifiedTests=false
 *
 * Or from IDE: Run this class as Java Application.
 *
 * Frontend dev server:
 *   cd driftkit-context-engineering/driftkit-context-engineering-spring-boot-starter/src/main/frontend
 *   npx vite
 *   → http://localhost:8080/prompt-engineering/
 */
@SpringBootApplication(scanBasePackages = {
        "ai.driftkit.context.spring",
        "ai.driftkit.workflows.spring",
        "ai.driftkit.clients"
})
@EnableScheduling
public class DriftKitDevRunner {

    public static void main(String[] args) {
        SpringApplication.run(DriftKitDevRunner.class, args);
    }
}

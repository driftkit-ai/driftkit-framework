package ai.driftkit.context.spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * DriftKit Context Engineering Platform — development server.
 * Requires MongoDB on localhost:27017.
 */
@SpringBootApplication(excludeName = {
        "de.flapdoodle.embed.mongo.spring.autoconfigure.EmbeddedMongoAutoConfiguration",
        "ai.driftkit.workflow.engine.spring.autoconfigure.WorkflowMongoRepositoriesAutoConfiguration",
        "ai.driftkit.vector.autoconfigure.VectorStoreAutoConfiguration"
})
@ComponentScan(
        basePackages = "ai.driftkit",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "ai\\.driftkit\\.vector\\..*"
        )
)
@EnableScheduling
public class DriftKitDevRunner {

    public static void main(String[] args) {
        SpringApplication.run(DriftKitDevRunner.class, args);
    }
}

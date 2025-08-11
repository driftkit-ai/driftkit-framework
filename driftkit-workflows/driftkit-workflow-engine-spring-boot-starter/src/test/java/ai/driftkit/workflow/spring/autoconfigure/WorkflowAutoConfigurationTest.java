package ai.driftkit.workflow.spring.autoconfigure;

import ai.driftkit.workflow.engine.core.WorkflowEngine;
import ai.driftkit.workflow.engine.persistence.InMemoryWorkflowStateRepository;
import ai.driftkit.workflow.engine.persistence.WorkflowStateRepository;
import ai.driftkit.workflow.engine.spring.service.WorkflowService;
import ai.driftkit.workflow.engine.spring.autoconfigure.WorkflowEngineAutoConfiguration;
import ai.driftkit.workflow.engine.spring.autoconfigure.WorkflowEngineProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for WorkflowEngineAutoConfiguration
 */
public class WorkflowAutoConfigurationTest {
    
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WorkflowEngineAutoConfiguration.class));
    
    @Test
    public void testDefaultConfiguration() {
        contextRunner
            .withPropertyValues("driftkit.workflow.engine.enabled=true")
            .run(context -> {
                assertThat(context).hasSingleBean(WorkflowEngine.class);
                assertThat(context).hasSingleBean(WorkflowService.class);
                assertThat(context).hasSingleBean(WorkflowStateRepository.class);
                assertThat(context.getBean(WorkflowStateRepository.class))
                    .isInstanceOf(InMemoryWorkflowStateRepository.class);
            });
    }
    
    @Test
    public void testDisabledConfiguration() {
        contextRunner
            .withPropertyValues("driftkit.workflow.engine.enabled=false")
            .run(context -> {
                assertThat(context).doesNotHaveBean(WorkflowEngine.class);
                assertThat(context).doesNotHaveBean(WorkflowService.class);
            });
    }
    
    @Test
    public void testCustomThreadPoolConfiguration() {
        contextRunner
            .withPropertyValues(
                "driftkit.workflow.engine.enabled=true",
                "driftkit.workflow.engine.core-threads=5",
                "driftkit.workflow.engine.max-threads=20",
                "driftkit.workflow.engine.queue-capacity=500"
            )
            .run(context -> {
                assertThat(context).hasSingleBean(WorkflowEngine.class);
                WorkflowEngineProperties properties = context.getBean(WorkflowEngineProperties.class);
                assertThat(properties.getCoreThreads()).isEqualTo(5);
                assertThat(properties.getMaxThreads()).isEqualTo(20);
                assertThat(properties.getQueueCapacity()).isEqualTo(500);
            });
    }
    
    @Test
    public void testConditionalOnClass() {
        contextRunner
            .withClassLoader(new FilteredClassLoader(WorkflowEngine.class))
            .run(context -> {
                assertThat(context).doesNotHaveBean(WorkflowEngine.class);
                assertThat(context).doesNotHaveBean(WorkflowService.class);
            });
    }
}
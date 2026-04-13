package ai.driftkit.workflow.engine.core.pipeline;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PipelineRegistryTest {

    private PipelineRegistry registry;

    @BeforeEach
    void setUp() {
        registry = PipelineRegistry.getInstance();
        // Clean any leftover state from other tests
        registry.list().forEach(p -> registry.remove(p.getId()));
    }

    @Nested
    class Register {

        @Test
        void register_newPipeline_storedSuccessfully() {
            PipelineDefinition def = PipelineDefinition.builder()
                    .id("test-pipeline")
                    .name("Test Pipeline")
                    .type(PipelineDefinition.PipelineType.SEQUENTIAL_AGENT)
                    .steps(List.of(
                            PipelineStep.builder().stepId("step1").order(0).type(PipelineStep.StepType.LLM_CALL).build(),
                            PipelineStep.builder().stepId("step2").order(1).type(PipelineStep.StepType.LLM_CALL).build()
                    ))
                    .build();

            registry.register(def);

            assertTrue(registry.get("test-pipeline").isPresent());
            assertEquals("Test Pipeline", registry.get("test-pipeline").get().getName());
            assertEquals(2, registry.get("test-pipeline").get().getSteps().size());
        }

        @Test
        void register_overwrite_replacesExisting() {
            PipelineDefinition v1 = PipelineDefinition.builder()
                    .id("pipeline").name("v1").type(PipelineDefinition.PipelineType.WORKFLOW).build();
            PipelineDefinition v2 = PipelineDefinition.builder()
                    .id("pipeline").name("v2").type(PipelineDefinition.PipelineType.WORKFLOW).build();

            registry.register(v1);
            registry.register(v2);

            assertEquals("v2", registry.get("pipeline").get().getName());
        }
    }

    @Nested
    class Get {

        @Test
        void get_nonExistent_returnsEmpty() {
            assertTrue(registry.get("nonexistent").isEmpty());
        }

        @Test
        void get_existing_returnsDefinition() {
            registry.register(PipelineDefinition.builder()
                    .id("exists").name("Exists").type(PipelineDefinition.PipelineType.LOOP_AGENT).build());

            var result = registry.get("exists");

            assertTrue(result.isPresent());
            assertEquals(PipelineDefinition.PipelineType.LOOP_AGENT, result.get().getType());
        }
    }

    @Nested
    class ListPipelines {

        @Test
        void list_empty_returnsEmptyList() {
            assertTrue(registry.list().isEmpty());
        }

        @Test
        void list_multiplePipelines_returnsAll() {
            registry.register(PipelineDefinition.builder().id("a").name("A").type(PipelineDefinition.PipelineType.WORKFLOW).build());
            registry.register(PipelineDefinition.builder().id("b").name("B").type(PipelineDefinition.PipelineType.SEQUENTIAL_AGENT).build());
            registry.register(PipelineDefinition.builder().id("c").name("C").type(PipelineDefinition.PipelineType.LOOP_AGENT).build());

            assertEquals(3, registry.list().size());
        }
    }

    @Nested
    class Remove {

        @Test
        void remove_existing_removesSuccessfully() {
            registry.register(PipelineDefinition.builder().id("removable").name("R").type(PipelineDefinition.PipelineType.WORKFLOW).build());

            registry.remove("removable");

            assertTrue(registry.get("removable").isEmpty());
        }

        @Test
        void remove_nonExistent_noError() {
            assertDoesNotThrow(() -> registry.remove("nonexistent"));
        }
    }

    @Nested
    class PipelineTypes {

        @Test
        void allTypesSupported() {
            for (PipelineDefinition.PipelineType type : PipelineDefinition.PipelineType.values()) {
                String id = "type-" + type.name();
                registry.register(PipelineDefinition.builder().id(id).name(id).type(type).build());

                assertEquals(type, registry.get(id).get().getType());
            }
        }
    }

    @Nested
    class StepTypes {

        @Test
        void pipelineWithMixedStepTypes() {
            var def = PipelineDefinition.builder()
                    .id("mixed")
                    .name("Mixed Steps")
                    .type(PipelineDefinition.PipelineType.WORKFLOW)
                    .steps(List.of(
                            PipelineStep.builder().stepId("classify").order(0).type(PipelineStep.StepType.LLM_CALL).agentName("classifier").build(),
                            PipelineStep.builder().stepId("route").order(1).type(PipelineStep.StepType.BRANCH).build(),
                            PipelineStep.builder().stepId("work").order(2).type(PipelineStep.StepType.LOOP_WORKER).agentName("worker").build(),
                            PipelineStep.builder().stepId("eval").order(3).type(PipelineStep.StepType.LOOP_EVALUATOR).agentName("evaluator").build(),
                            PipelineStep.builder().stepId("async").order(4).type(PipelineStep.StepType.ASYNC).build()
                    ))
                    .build();

            registry.register(def);

            var result = registry.get("mixed").get();
            assertEquals(5, result.getSteps().size());
            assertEquals(PipelineStep.StepType.BRANCH, result.getSteps().get(1).getType());
            assertEquals("worker", result.getSteps().get(2).getAgentName());
        }
    }
}

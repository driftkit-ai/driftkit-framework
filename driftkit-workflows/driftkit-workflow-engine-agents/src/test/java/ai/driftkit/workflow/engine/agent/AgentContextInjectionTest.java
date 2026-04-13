package ai.driftkit.workflow.engine.agent;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that LLMAgent.setWorkflowContext() works correctly
 * and that SequentialAgent/LoopAgent builder flags work.
 */
class AgentContextInjectionTest {

    @Nested
    class LLMAgentWorkflowContext {

        @Test
        void setWorkflowContext_updatesFields() {
            LLMAgent agent = LLMAgent.builder()
                    .name("test-agent")
                    .systemMessage("test")
                    .build();

            assertNull(agent.getWorkflowId());
            assertNull(agent.getWorkflowStep());

            agent.setWorkflowContext("my-pipeline", "step-0-test");

            assertEquals("my-pipeline", agent.getWorkflowId());
            assertEquals("step-0-test", agent.getWorkflowStep());
        }

        @Test
        void setWorkflowContext_canBeCalledMultipleTimes() {
            LLMAgent agent = LLMAgent.builder()
                    .name("test")
                    .systemMessage("test")
                    .build();

            agent.setWorkflowContext("pipeline1", "step-0");
            assertEquals("pipeline1", agent.getWorkflowId());

            agent.setWorkflowContext("pipeline2", "step-1");
            assertEquals("pipeline2", agent.getWorkflowId());
            assertEquals("step-1", agent.getWorkflowStep());
        }
    }

    @Nested
    class SequentialAgentBuilder {

        @Test
        void defaultName_customNameIsFalse() {
            SequentialAgent agent = SequentialAgent.builder().build();

            assertEquals("SequentialAgent", agent.getName());
            assertFalse(agent.isCustomName());
        }

        @Test
        void explicitName_customNameIsTrue() {
            SequentialAgent agent = SequentialAgent.builder()
                    .name("my-pipeline")
                    .customName(true)
                    .build();

            assertEquals("my-pipeline", agent.getName());
            assertTrue(agent.isCustomName());
        }
    }

    @Nested
    class LoopAgentBuilder {

        @Test
        void defaultName_customNameIsFalse() {
            Agent dummyWorker = new DummyAgent("worker");
            Agent dummyEvaluator = new DummyAgent("evaluator");

            LoopAgent agent = LoopAgent.builder()
                    .worker(dummyWorker)
                    .evaluator(dummyEvaluator)
                    .stopCondition(LoopStatus.COMPLETE)
                    .build();

            assertEquals("LoopAgent", agent.getName());
            assertFalse(agent.isCustomName());
        }

        @Test
        void explicitName_customNameIsTrue() {
            Agent dummyWorker = new DummyAgent("worker");
            Agent dummyEvaluator = new DummyAgent("evaluator");

            LoopAgent agent = LoopAgent.builder()
                    .name("quality-loop")
                    .customName(true)
                    .worker(dummyWorker)
                    .evaluator(dummyEvaluator)
                    .stopCondition(LoopStatus.COMPLETE)
                    .build();

            assertEquals("quality-loop", agent.getName());
            assertTrue(agent.isCustomName());
        }
    }

    static class DummyAgent implements Agent {
        private final String name;
        DummyAgent(String name) { this.name = name; }
        @Override public String execute(String input) { return input; }
        @Override public String execute(String text, byte[] imageData) { return text; }
        @Override public String execute(String text, List<byte[]> imageDataList) { return text; }
        @Override public String execute(String input, Map<String, Object> variables) { return input; }
        @Override public String getName() { return name; }
        @Override public String getDescription() { return "dummy"; }
    }
}

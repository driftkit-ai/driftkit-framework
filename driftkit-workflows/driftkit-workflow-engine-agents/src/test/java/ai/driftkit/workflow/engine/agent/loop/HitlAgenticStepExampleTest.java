package ai.driftkit.workflow.engine.agent.loop;

import ai.driftkit.common.tools.Tool;
import ai.driftkit.common.tools.ToolCall;
import ai.driftkit.common.tools.ToolRegistry;
import ai.driftkit.workflow.engine.agent.LLMAgent;
import ai.driftkit.workflow.engine.core.StepResult;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * REFERENCE EXAMPLE (plan, D7): a workflow step that runs an agentic loop with an
 * approval gate, suspends through the workflow engine's HITL mechanism and resumes
 * with the operator's decision.
 *
 * <p>The flow demonstrated here is exactly what a production workflow step does:</p>
 *
 * <pre>
 *  @Step
 *  public StepResult&lt;String&gt; processRefund(RefundRequest input, WorkflowContext ctx) {
 *      AgentLoopResult result = agent.executeAgentic(input.instruction(), Map.of(), options);
 *      if (result.isSuspended()) {
 *          return AgentLoopSuspensions.toSuspend(result);   // -&gt; engine persists &amp; waits
 *      }
 *      return StepResult.finish(result.getText());
 *  }
 *
 *  // resume step (invoked by the engine with the operator's ApprovalDecision):
 *  @Step
 *  public StepResult&lt;String&gt; onApproval(ApprovalDecision decision, WorkflowContext ctx) {
 *      LoopState state = AgentLoopSuspensions.restoreState(ctx.getSuspensionMetadata());
 *      AgentLoopResult result = agent.resumeAgentic(state, decision, options);
 *      return StepResult.finish(result.getText());
 *  }
 * </pre>
 *
 * <p>This test executes that exact sequence with a scripted model client, including
 * the serialization round-trip the engine performs between the two steps.</p>
 */
class HitlAgenticStepExampleTest {

    /** Domain tools of the example: a read-only lookup and a destructive payout. */
    public static class RefundTools {
        final AtomicReference<String> refunded = new AtomicReference<>();

        @Tool(description = "Look up an order by id", readOnly = true, concurrencySafe = true)
        public String lookupOrder(String orderId) {
            return "{\"orderId\":\"" + orderId + "\",\"amount\":49.90,\"status\":\"delivered\"}";
        }

        @Tool(description = "Refund an order; irreversible", destructive = true,
                whenToUse = "Only after the order was verified")
        public String refundOrder(String orderId) {
            refunded.set(orderId);
            return "refund issued for " + orderId;
        }
    }

    /** Policy hook of the example: destructive tools require operator approval. */
    static final AgentHook APPROVAL_GATE = new AgentHook() {
        @Override
        public HookDecision preToolUse(ToolCall call, LoopState state) {
            if ("refundOrder".equals(call.getFunction().getName())) {
                return HookDecision.ask("refunds above policy threshold require operator approval");
            }
            return HookDecision.allow();
        }
    };

    @Test
    void workflowStepSuspendsAndResumesAroundOperatorApproval() {
        // --- Arrange: agent with tools, approval hook on destructive calls ---
        MockModelClient client = new MockModelClient();
        RefundTools tools = new RefundTools();
        ToolRegistry registry = new ToolRegistry();
        registry.registerClass(tools);

        LLMAgent agent = LLMAgent.builder()
                .name("refund-agent")
                .modelClient(client)
                .systemMessage("You process refund requests using the available tools.")
                .toolRegistry(registry)
                .build();

        AgenticOptions options = AgenticOptions.builder()
                .hooks(java.util.List.of(APPROVAL_GATE))
                .build();

        // Scripted model: verify the order, then refund it, then summarize
        client.enqueueToolCalls("verifying order",
                MockModelClient.toolCall("c1", "lookupOrder", Map.of("orderId", "A-42")));
        client.enqueueToolCalls("order verified, issuing refund",
                MockModelClient.toolCall("c2", "refundOrder", Map.of("orderId", "A-42")));
        client.enqueueText("Refund for order A-42 has been issued.");

        // --- Step 1: the workflow step runs the agent and hits the approval gate ---
        AgentLoopResult firstRun = agent.executeAgentic(
                "Process the refund request for order A-42", Map.of(), options);

        assertTrue(firstRun.isSuspended());
        assertNull(tools.refunded.get(), "destructive tool must not run before approval");

        // The step returns a workflow suspension; the engine shows promptToUser to the operator
        StepResult.Suspend<AgentLoopSuspensions.ApprovalRequest> suspend =
                AgentLoopSuspensions.toSuspend(firstRun);
        assertEquals("refundOrder", suspend.promptToUser().pendingCalls().get(0).toolName());
        assertEquals(ApprovalDecision.class, suspend.nextInputClass());

        // --- Between steps: the engine persists metadata (JSON round-trip happens inside) ---
        Map<String, Object> persistedMetadata = suspend.metadata();

        // --- Step 2: the resume step restores the state and applies the decision ---
        LoopState restored = AgentLoopSuspensions.restoreState(persistedMetadata);
        AgentLoopResult resumed = agent.resumeAgentic(restored, ApprovalDecision.approve(), options);

        assertEquals(StopReason.END_TURN, resumed.getStopReason());
        assertEquals("A-42", tools.refunded.get(), "approved refund must execute");
        assertTrue(resumed.getText().contains("A-42"));
    }

    @Test
    void rejectionFlowsBackIntoTheModelInsteadOfFailing() {
        MockModelClient client = new MockModelClient();
        RefundTools tools = new RefundTools();
        ToolRegistry registry = new ToolRegistry();
        registry.registerClass(tools);

        LLMAgent agent = LLMAgent.builder()
                .name("refund-agent")
                .modelClient(client)
                .toolRegistry(registry)
                .build();

        AgenticOptions options = AgenticOptions.builder()
                .hooks(java.util.List.of(APPROVAL_GATE))
                .build();

        client.enqueueToolCalls(null,
                MockModelClient.toolCall("c1", "refundOrder", Map.of("orderId", "A-42")));
        client.enqueueText("Understood — the refund was not approved; I have not issued it.");

        AgentLoopResult suspended = agent.executeAgentic("Refund order A-42 immediately", Map.of(), options);
        LoopState restored = AgentLoopSuspensions.restoreState(
                AgentLoopSuspensions.toSuspend(suspended).metadata());

        AgentLoopResult resumed = agent.resumeAgentic(restored,
                ApprovalDecision.reject("amount exceeds operator limit"), options);

        assertEquals(StopReason.END_TURN, resumed.getStopReason());
        assertNull(tools.refunded.get(), "rejected refund must never execute");
    }
}

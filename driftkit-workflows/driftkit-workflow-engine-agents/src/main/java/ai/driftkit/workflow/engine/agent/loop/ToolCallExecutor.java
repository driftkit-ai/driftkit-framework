package ai.driftkit.workflow.engine.agent.loop;

import ai.driftkit.common.tools.ToolCall;
import ai.driftkit.common.tools.ToolInfo;
import ai.driftkit.common.tools.ToolMetadata;
import ai.driftkit.common.tools.ToolRegistry;
import ai.driftkit.common.utils.JsonUtils;
import ai.driftkit.workflow.engine.agent.ToolExecutionResult;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

/**
 * Executes a batch of tool calls requested by one assistant turn.
 *
 * <p>Partitioning follows the Claude Code strategy: consecutive concurrency-safe
 * tools are batched and run in parallel on virtual threads (capped by
 * {@link LoopPolicy#getMaxToolConcurrency()}); a non-safe tool forms its own
 * serial batch. Results are returned strictly in the order of the original
 * tool calls — providers require tool results to match tool-call order.</p>
 *
 * <p>Each rendered result is truncated to the tool's {@code maxResultChars}
 * with an explicit marker, so a single tool cannot exhaust the context window.</p>
 */
@Slf4j
public class ToolCallExecutor {

    private final ToolRegistry toolRegistry;
    private final int maxConcurrency;

    public ToolCallExecutor(ToolRegistry toolRegistry, int maxConcurrency) {
        if (toolRegistry == null) {
            throw new IllegalArgumentException("toolRegistry is required");
        }
        if (maxConcurrency < 1) {
            throw new IllegalArgumentException("maxConcurrency must be >= 1");
        }
        this.toolRegistry = toolRegistry;
        this.maxConcurrency = maxConcurrency;
    }

    /**
     * One executed (or denied) tool call: the domain result plus the rendered,
     * truncated text fed back to the model as the tool message content.
     */
    @Value
    public static class ExecutedCall {
        ToolCall call;
        ToolExecutionResult result;
        String renderedContent;
    }

    /**
     * A call resolved by hooks: either runs, or is short-circuited to a denial result.
     */
    public record ResolvedCall(ToolCall call, boolean denied, String denyReason) {
        public static ResolvedCall execute(ToolCall call) {
            return new ResolvedCall(call, false, null);
        }

        public static ResolvedCall deny(ToolCall call, String reason) {
            return new ResolvedCall(call, true, reason);
        }
    }

    /**
     * Execute all resolved calls preserving input order in the returned list.
     */
    public List<ExecutedCall> executeAll(List<ResolvedCall> calls) {
        List<Batch> batches = partition(calls);
        ExecutedCall[] results = new ExecutedCall[calls.size()];

        for (Batch batch : batches) {
            if (batch.concurrent && batch.indexes.size() > 1) {
                executeConcurrently(calls, batch, results);
            } else {
                for (int idx : batch.indexes) {
                    results[idx] = executeOne(calls.get(idx));
                }
            }
        }
        return List.of(results);
    }

    private void executeConcurrently(List<ResolvedCall> calls, Batch batch, ExecutedCall[] results) {
        Semaphore permits = new Semaphore(maxConcurrency);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (int idx : batch.indexes) {
                final int i = idx;
                Callable<Void> task = () -> {
                    permits.acquire();
                    try {
                        results[i] = executeOne(calls.get(i));
                    } finally {
                        permits.release();
                    }
                    return null;
                };
                futures.add(executor.submit(task));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Tool execution interrupted", e);
        } catch (Exception e) {
            throw new RuntimeException("Concurrent tool execution failed", e);
        }
    }

    private ExecutedCall executeOne(ResolvedCall resolved) {
        ToolCall call = resolved.call();
        String toolName = call.getFunction() != null ? call.getFunction().getName() : "<unknown>";

        if (resolved.denied()) {
            String reason = resolved.denyReason() != null ? resolved.denyReason() : "denied by policy";
            ToolExecutionResult result = ToolExecutionResult.failure(toolName, "Denied: " + reason);
            // The model must see the refusal as a normal tool result so it can adapt.
            return new ExecutedCall(call, result, "Tool call denied: " + reason);
        }

        ToolMetadata metadata = metadataOf(toolName);
        ToolExecutionResult result;
        try {
            Object value = toolRegistry.executeToolCall(call);
            result = ToolExecutionResult.success(toolName, value);
        } catch (Exception e) {
            log.warn("Tool '{}' execution failed", toolName, e);
            result = ToolExecutionResult.failure(toolName, e.getMessage());
        }
        return new ExecutedCall(call, result, render(result, metadata));
    }

    private String render(ToolExecutionResult result, ToolMetadata metadata) {
        String rendered;
        if (!result.isSuccess()) {
            rendered = "Tool execution failed: " + (result.getError() != null ? result.getError() : "unknown error");
        } else if (result.getResult() == null) {
            rendered = "null";
        } else if (result.getResult() instanceof String s) {
            rendered = s;
        } else {
            try {
                rendered = JsonUtils.toJson(result.getResult());
            } catch (Exception e) {
                rendered = String.valueOf(result.getResult());
            }
        }
        return truncate(rendered, metadata.getMaxResultChars());
    }

    static String truncate(String content, int maxChars) {
        if (maxChars <= 0 || content == null || content.length() <= maxChars) {
            return content;
        }
        int cut = Math.max(0, maxChars);
        return content.substring(0, cut)
                + "\n...[truncated " + (content.length() - cut) + " chars]";
    }

    boolean isConcurrencySafe(ToolCall call) {
        String name = call.getFunction() != null ? call.getFunction().getName() : null;
        return metadataOf(name).isConcurrencySafe();
    }

    private ToolMetadata metadataOf(String toolName) {
        if (toolName != null) {
            ToolInfo info = toolRegistry.getToolInfo(toolName);
            if (info != null) {
                return info.getMetadata();
            }
        }
        return ToolMetadata.defaults();
    }

    private record Batch(boolean concurrent, List<Integer> indexes) {
    }

    /**
     * Walk calls in order; group consecutive concurrency-safe executable calls,
     * give every other call its own serial batch. Denied calls are trivially
     * safe (no side effects) and join concurrent batches.
     */
    private List<Batch> partition(List<ResolvedCall> calls) {
        List<Batch> batches = new ArrayList<>();
        List<Integer> current = null;

        for (int i = 0; i < calls.size(); i++) {
            ResolvedCall resolved = calls.get(i);
            boolean safe = resolved.denied() || isConcurrencySafe(resolved.call());
            if (safe) {
                if (current == null) {
                    current = new ArrayList<>();
                }
                current.add(i);
            } else {
                if (current != null) {
                    batches.add(new Batch(true, current));
                    current = null;
                }
                batches.add(new Batch(false, List.of(i)));
            }
        }
        if (current != null) {
            batches.add(new Batch(true, current));
        }
        return batches;
    }
}

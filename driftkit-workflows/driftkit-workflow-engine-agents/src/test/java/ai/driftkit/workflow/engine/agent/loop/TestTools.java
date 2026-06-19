package ai.driftkit.workflow.engine.agent.loop;

import ai.driftkit.common.tools.Tool;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tool fixture for loop tests. Execution order/concurrency is observable.
 */
public class TestTools {

    final ConcurrentLinkedQueue<String> invocations = new ConcurrentLinkedQueue<>();
    final AtomicInteger concurrentNow = new AtomicInteger();
    final AtomicInteger maxObservedConcurrency = new AtomicInteger();
    volatile CountDownLatch concurrencyLatch;

    @Tool(description = "Look up the weather in a city", whenToUse = "User asks about weather",
            whenNotToUse = "For historical climate data", readOnly = true, concurrencySafe = true)
    public String getWeather(String city) {
        trackConcurrent("getWeather:" + city);
        return "Sunny in " + city + ", 25C";
    }

    @Tool(description = "Search the product catalog", readOnly = true, concurrencySafe = true)
    public List<String> searchCatalog(String query) {
        trackConcurrent("searchCatalog:" + query);
        return List.of(query + "-result-1", query + "-result-2");
    }

    @Tool(description = "Write a record to the database", destructive = true)
    public String writeRecord(String key, String value) {
        invocations.add("writeRecord:" + key);
        return "written " + key + "=" + value;
    }

    @Tool(description = "Always fails")
    public String alwaysFails(String input) {
        invocations.add("alwaysFails:" + input);
        throw new IllegalStateException("boom: " + input);
    }

    @Tool(description = "Returns a huge payload", concurrencySafe = true, maxResultChars = 100)
    public String hugeResult(String seed) {
        invocations.add("hugeResult:" + seed);
        return seed.repeat(500);
    }

    private void trackConcurrent(String tag) {
        int now = concurrentNow.incrementAndGet();
        maxObservedConcurrency.accumulateAndGet(now, Math::max);
        try {
            CountDownLatch latch = concurrencyLatch;
            if (latch != null) {
                latch.countDown();
                // Hold until all expected parallel calls arrive (proves real concurrency)
                if (!latch.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Expected concurrent calls did not arrive");
                }
            }
            invocations.add(tag);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            concurrentNow.decrementAndGet();
        }
    }
}

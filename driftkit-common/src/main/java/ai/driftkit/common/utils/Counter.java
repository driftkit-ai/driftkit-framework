package ai.driftkit.common.utils;

import lombok.Data;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Data
public class Counter<T> {
    Map<T, AtomicInteger> counters;

    public static class CounterNamed extends Counter<String> {
        public CounterNamed() {
            super();
        }

        public Map<String, Integer> getMap() {
            return entrySet().stream()
                    .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().intValue()));
        }
    }

    public Counter() {
        this.counters = new ConcurrentHashMap<>();
    }

    public int increment(T name) {
        return getMutableInt(name).incrementAndGet();
    }

    public int add(T name, int add) {
        return getMutableInt(name).addAndGet(add);
    }

    public int get(T name) {
        return getMutableInt(name).intValue();
    }

    public Set<Entry<T, AtomicInteger>> entrySet() {
        return counters.entrySet();
    }

    private AtomicInteger getMutableInt(T name) {
        return counters.computeIfAbsent(name, k -> new AtomicInteger());
    }

    @Override
    public String toString() {
        return String.valueOf(counters);
    }
}
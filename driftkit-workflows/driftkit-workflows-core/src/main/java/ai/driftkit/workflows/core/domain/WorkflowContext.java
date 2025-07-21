package ai.driftkit.workflows.core.domain;

import ai.driftkit.common.domain.MessageTask;
import ai.driftkit.common.utils.Counter;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Data
@AllArgsConstructor
public class WorkflowContext {
    private MessageTask task;
    private Map<String, Object> context;
    private Counter<String> counter;
    private String workflowId;
    private String currentStep;

    public WorkflowContext() {
        this(null);
    }

    public WorkflowContext(MessageTask task) {
        this(task, task != null ? task.getMessageId() : UUID.randomUUID().toString());
    }
    
    public WorkflowContext(MessageTask task, String workflowId) {
        this.task = task;
        this.context = new ConcurrentHashMap<>();
        this.counter = new Counter<>();
        this.workflowId = workflowId;
    }

    public void addCounter(String name, int amount) {
        onCounterChange(name, amount);
        this.counter.add(name, amount);
    }

    public int getCounter(String name) {
        return this.counter.get(name);
    }

    public void onCounterChange(String name, int amount) {
    }

    public void onContextChange(String name, Object value) {
    }

    public void onStepInvocation(String method, WorkflowEvent event) {
        addCounter(getMethodInvocationsCounterName(method), 1);
        addCounter(event.getClass().getSimpleName() + "Event", 1);
        
        this.currentStep = method;
        add(method, event);
    }

    public int getStepInvocationCount(String method) {
        return counter.get(getMethodInvocationsCounterName(method));
    }

    public static String getMethodInvocationsCounterName(String method) {
        return method + "Invocation";
    }

    public void put(String name, Object value) {
        onContextChange(name, value);
        this.context.put(name, value);
    }

    public <T> T getLastContext(String name) {
        List<T> list = (List<T>) this.context.computeIfAbsent(name, e -> new CopyOnWriteArrayList<>());

        if (list.isEmpty()) {
            return null;
        }

        return list.getLast();
    }

    public <T> void add(String name, T result) {
        onContextChange(name, result);

        List<T> list = (List<T>) this.context.computeIfAbsent(name, e -> new CopyOnWriteArrayList<>());
        list.add(result);
    }

    public <T> T get(String name) {
        return (T) this.context.get(name);
    }

    public <T> T getOrDefault(String name, T def) {
        Object value = get(name);

        if (value == null) {
            return def;
        }
        return (T) value;
    }

    public String getAsString(String promptId) {
        return (String) this.context.get(promptId);
    }
}

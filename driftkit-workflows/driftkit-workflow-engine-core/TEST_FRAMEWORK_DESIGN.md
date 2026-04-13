# DriftKit Workflow Engine Core Test Framework

## Current Status

**IMPORTANT**: The test framework has been implemented as a separate module `driftkit-workflow-test-framework` but requires changes to `WorkflowEngine` to function properly. See [INTERCEPTOR_ISSUE.md](../driftkit-workflow-test-framework/INTERCEPTOR_ISSUE.md) for details.

### Implementation Status:
- ✅ Test framework module created
- ✅ All core classes implemented  
- ✅ Example tests written
- ❌ Cannot add interceptors to WorkflowEngine (blocking issue)
- ❌ Tests fail because mocking doesn't work without interceptor support

## Обзор

Этот документ описывает комплексный тестовый фреймворк для `driftkit-workflow-engine-core`, разработанный для обеспечения полного покрытия всех типов workflow, включая аннотационные workflow, fluent API workflow и AI агентов. Фреймворк обеспечивает изоляцию тестов, простоту использования и поддержку сложных сценариев тестирования.

## Архитектура тестового фреймворка

### Ключевые компоненты

1. **Базовые тестовые классы** - абстрактные классы для различных типов workflow
2. **Mock системы** - имитация AI клиентов и внешних зависимостей
3. **Тестовые утилиты** - вспомогательные классы для асинхронности, retry логики и валидации
4. **DSL для сценариев** - fluent API для создания читаемых тестовых сценариев
5. **Фабрики данных** - генерация тестовых данных и фикстур

## Базовые тестовые классы

### 1. AnnotationWorkflowTestBase

Базовый класс для тестирования аннотационных workflow с поддержкой suspend/resume циклов.

```java
package ai.driftkit.workflow.test.framework;

import ai.driftkit.workflow.engine.core.*;
import ai.driftkit.workflow.engine.repository.*;
import ai.driftkit.workflow.engine.async.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

public abstract class AnnotationWorkflowTestBase {
    
    protected WorkflowEngine engine;
    protected WorkflowEngineConfig config;
    protected TestWorkflowListener listener;
    protected InMemoryWorkflowStateRepository stateRepository;
    protected InMemoryAsyncStepStateRepository asyncStateRepository;
    protected InMemorySuspensionDataRepository suspensionRepository;
    
    @BeforeEach
    void setUpEngine() {
        // Инициализация репозиториев
        stateRepository = new InMemoryWorkflowStateRepository();
        asyncStateRepository = new InMemoryAsyncStepStateRepository();
        suspensionRepository = new InMemorySuspensionDataRepository();
        
        // Создание тестового слушателя
        listener = new TestWorkflowListener();
        
        // Конфигурация движка
        config = WorkflowEngineConfig.builder()
            .stateRepository(stateRepository)
            .asyncStepStateRepository(asyncStateRepository)
            .suspensionDataRepository(suspensionRepository)
            .coreThreads(2)
            .maxThreads(4)
            .queueCapacity(100)
            .shutdownTimeout(5000)
            .build();
        
        engine = new WorkflowEngine(config);
        engine.addListener("test-listener", listener);
    }
    
    @AfterEach
    void tearDownEngine() {
        engine.shutdown();
    }
    
    // Утилита для выполнения workflow с ожиданием приостановки
    protected <T> SuspendedWorkflow<T> executeSuspending(String workflowId, Object input) {
        var execution = engine.execute(workflowId, input);
        return new SuspendedWorkflow<>(execution, engine);
    }
    
    // Утилита для выполнения workflow с ожиданием завершения
    protected <T> T executeAndWait(String workflowId, Object input, long timeout, TimeUnit unit) {
        var execution = engine.execute(workflowId, input);
        try {
            return execution.get(timeout, unit);
        } catch (Exception e) {
            fail("Workflow execution failed: " + e.getMessage());
            return null;
        }
    }
    
    // Проверка состояния workflow
    protected void assertWorkflowSuspended(String runId, String expectedStepId) {
        var instance = engine.getWorkflowInstance(runId);
        assertTrue(instance.isPresent(), "Workflow instance not found");
        assertEquals(WorkflowInstance.WorkflowStatus.SUSPENDED, 
                    instance.get().getStatus(), 
                    "Workflow is not suspended");
        assertEquals(expectedStepId, 
                    instance.get().getCurrentStepId(), 
                    "Unexpected current step");
    }
    
    protected void assertWorkflowCompleted(String runId) {
        var instance = engine.getWorkflowInstance(runId);
        assertTrue(instance.isPresent(), "Workflow instance not found");
        assertEquals(WorkflowInstance.WorkflowStatus.COMPLETED, 
                    instance.get().getStatus(), 
                    "Workflow is not completed");
    }
    
    protected void assertWorkflowFailed(String runId) {
        var instance = engine.getWorkflowInstance(runId);
        assertTrue(instance.isPresent(), "Workflow instance not found");
        assertEquals(WorkflowInstance.WorkflowStatus.FAILED, 
                    instance.get().getStatus(), 
                    "Workflow is not failed");
    }
    
    // Помощник для создания многошаговых сценариев
    protected <T> WorkflowTestScenario<T> scenario(String workflowId) {
        return new WorkflowTestScenario<>(engine, workflowId);
    }
    
    // Регистрация workflow для тестирования
    protected void registerWorkflow(Object workflow) {
        engine.register(workflow);
    }
    
    // Доступ к событиям для проверки
    protected TestWorkflowListener.EventCapture getEvents() {
        return listener.getEvents();
    }
}

// Вспомогательный класс для работы с приостановленными workflow
class SuspendedWorkflow<T> {
    private final WorkflowEngine.WorkflowExecution<T> execution;
    private final WorkflowEngine engine;
    private String runId;
    
    public SuspendedWorkflow(WorkflowEngine.WorkflowExecution<T> execution, WorkflowEngine engine) {
        this.execution = execution;
        this.engine = engine;
        this.runId = execution.getRunId();
    }
    
    public SuspendedWorkflow<T> waitForSuspension(long timeout, TimeUnit unit) {
        long endTime = System.currentTimeMillis() + unit.toMillis(timeout);
        while (System.currentTimeMillis() < endTime) {
            var instance = engine.getWorkflowInstance(runId);
            if (instance.isPresent() && 
                instance.get().getStatus() == WorkflowInstance.WorkflowStatus.SUSPENDED) {
                return this;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for suspension", e);
            }
        }
        throw new AssertionError("Workflow did not suspend within timeout");
    }
    
    public WorkflowEngine.WorkflowExecution<T> resume(Object input) {
        return engine.resume(runId, input);
    }
    
    public String getRunId() {
        return runId;
    }
}
```

### 2. FluentWorkflowTestBase

Базовый класс для тестирования workflow, созданных через fluent API.

```java
package ai.driftkit.workflow.test.framework;

import ai.driftkit.workflow.engine.builder.*;
import ai.driftkit.workflow.engine.core.*;
import org.junit.jupiter.api.BeforeEach;
import java.util.function.Function;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class FluentWorkflowTestBase extends AnnotationWorkflowTestBase {
    
    protected WorkflowBuilder.BuilderTestHelper testHelper;
    
    @BeforeEach
    void setUpFluentTesting() {
        super.setUpEngine();
        testHelper = new WorkflowBuilder.BuilderTestHelper();
    }
    
    // Создание тестера для fluent workflow
    protected <I, O> FluentWorkflowTester<I, O> createTester(String id, Class<I> inputType, Class<O> outputType) {
        return new FluentWorkflowTester<>(engine, id, inputType, outputType);
    }
    
    // Быстрое создание простого workflow
    protected <I, O> void registerSimpleWorkflow(String id, Function<I, O> logic) {
        WorkflowBuilder.<I, O>create(id)
            .description("Test workflow " + id)
            .step("process", StepBuilder.<I, O>create()
                .handler(input -> StepResult.finish(logic.apply(input)))
                .build())
            .register(engine);
    }
}

// Fluent API для построения и тестирования workflow
class FluentWorkflowTester<I, O> {
    private final WorkflowBuilder<I, O> builder;
    private final WorkflowEngine engine;
    private final String workflowId;
    private final AtomicInteger stepCounter = new AtomicInteger(0);
    
    public FluentWorkflowTester(WorkflowEngine engine, String id, Class<I> inputType, Class<O> outputType) {
        this.engine = engine;
        this.workflowId = id;
        this.builder = WorkflowBuilder.<I, O>create(id)
            .description("Test workflow " + id);
    }
    
    // Добавление простого шага
    public FluentWorkflowTester<I, O> step(String name, Function<I, StepResult<O>> handler) {
        builder.step(name, StepBuilder.<I, O>create()
            .handler(handler)
            .build());
        stepCounter.incrementAndGet();
        return this;
    }
    
    // Добавление шага, возвращающего значение
    public FluentWorkflowTester<I, O> stepValue(String name, Function<I, O> valueHandler) {
        return step(name, input -> StepResult.finish(valueHandler.apply(input)));
    }
    
    // Добавление асинхронного шага
    public FluentWorkflowTester<I, O> asyncStep(String name, String taskId, 
                                                Function<I, CompletableFuture<StepResult<O>>> asyncHandler) {
        builder.step(name, StepBuilder.<I, O>create()
            .async()
            .handler(input -> {
                var future = asyncHandler.apply(input);
                return StepResult.async(taskId, future);
            })
            .build());
        stepCounter.incrementAndGet();
        return this;
    }
    
    // Добавление условного шага
    public FluentWorkflowTester<I, O> conditionalStep(String name, 
                                                      Function<I, Boolean> condition,
                                                      Function<I, StepResult<O>> ifTrue,
                                                      Function<I, StepResult<O>> ifFalse) {
        builder.step(name, StepBuilder.<I, O>create()
            .handler(input -> condition.apply(input) ? ifTrue.apply(input) : ifFalse.apply(input))
            .build());
        stepCounter.incrementAndGet();
        return this;
    }
    
    // Добавление retry логики
    public FluentWorkflowTester<I, O> retryableStep(String name, 
                                                    Function<I, StepResult<O>> handler,
                                                    int maxRetries,
                                                    long retryDelay) {
        builder.step(name, StepBuilder.<I, O>create()
            .handler(handler)
            .retryPolicy(RetryPolicy.builder()
                .maxRetries(maxRetries)
                .retryDelay(retryDelay)
                .build())
            .build());
        stepCounter.incrementAndGet();
        return this;
    }
    
    // Регистрация и получение workflow
    public WorkflowExecutionTester<O> build() {
        builder.register(engine);
        return new WorkflowExecutionTester<>(engine, workflowId);
    }
    
    // Валидация структуры workflow
    public void assertStepCount(int expectedCount) {
        assertEquals(expectedCount, stepCounter.get(), "Unexpected number of steps");
    }
}

// Тестер выполнения workflow
class WorkflowExecutionTester<O> {
    private final WorkflowEngine engine;
    private final String workflowId;
    
    public WorkflowExecutionTester(WorkflowEngine engine, String workflowId) {
        this.engine = engine;
        this.workflowId = workflowId;
    }
    
    public ExecutionResult<O> execute(Object input) {
        var execution = engine.execute(workflowId, input);
        return new ExecutionResult<>(execution, engine);
    }
    
    public ExecutionResult<O> executeAndWait(Object input, long timeout, TimeUnit unit) {
        var execution = engine.execute(workflowId, input);
        try {
            O result = execution.get(timeout, unit);
            return new ExecutionResult<>(execution, engine, result);
        } catch (Exception e) {
            return new ExecutionResult<>(execution, engine, e);
        }
    }
}
```

### 3. AgentWorkflowTestBase

Специализированный базовый класс для тестирования AI агентов с mock клиентами.

```java
package ai.driftkit.workflow.test.framework;

import ai.driftkit.workflow.engine.agent.*;
import ai.driftkit.workflow.engine.chat.*;
import ai.driftkit.common.domain.chat.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AgentWorkflowTestBase extends AnnotationWorkflowTestBase {
    
    protected MockAIClient mockAIClient;
    protected InMemoryChatStore mockChatStore;
    protected MockStructuredOutputParser mockParser;
    
    @BeforeEach
    void setUpAgentTesting() {
        super.setUpEngine();
        
        // Инициализация mock компонентов
        mockAIClient = new MockAIClient();
        mockChatStore = new InMemoryChatStore();
        mockParser = new MockStructuredOutputParser();
        
        // Обновление конфигурации с mock компонентами
        config = config.toBuilder()
            .chatStore(mockChatStore)
            .build();
        
        engine = new WorkflowEngine(config);
        engine.addListener("test-listener", listener);
        
        // Регистрация mock провайдеров
        registerMockProviders();
    }
    
    private void registerMockProviders() {
        // Здесь можно зарегистрировать mock провайдеры в DI контейнере
        // или настроить их через reflection/proxy
    }
    
    // Утилиты для настройки mock ответов
    protected void mockAIResponse(String promptPattern, String response) {
        mockAIClient.addResponse(promptPattern, response);
    }
    
    protected void mockStructuredAIResponse(String promptPattern, Object structuredResponse) {
        mockAIClient.addStructuredResponse(promptPattern, structuredResponse);
    }
    
    protected void mockAIResponseWithMetadata(String promptPattern, String response, Map<String, Object> metadata) {
        mockAIClient.addResponseWithMetadata(promptPattern, response, metadata);
    }
    
    // Утилита для последовательных ответов
    protected void mockAIConversation(String agentId, List<ConversationTurn> turns) {
        mockAIClient.addConversation(agentId, turns);
    }
    
    // Проверка AI взаимодействий
    protected void assertAICallMade(String expectedPromptPattern) {
        assertTrue(mockAIClient.wasPromptCalled(expectedPromptPattern),
                  "Expected AI call with prompt pattern: " + expectedPromptPattern);
    }
    
    protected void assertAICallCount(int expectedCount) {
        assertEquals(expectedCount, mockAIClient.getCallCount(),
                    "Unexpected number of AI calls");
    }
    
    protected void assertNoAICallsAfter(long timestamp) {
        assertTrue(mockAIClient.getCallsAfter(timestamp).isEmpty(),
                  "Unexpected AI calls after timestamp: " + timestamp);
    }
    
    // Проверка истории чата
    protected void assertChatHistoryContains(String chatId, String messagePattern) {
        var history = mockChatStore.getHistory(chatId);
        assertTrue(history.stream()
                         .anyMatch(msg -> msg.getContent().contains(messagePattern)),
                  "Chat history does not contain: " + messagePattern);
    }
    
    protected void assertChatHistorySize(String chatId, int expectedSize) {
        var history = mockChatStore.getHistory(chatId);
        assertEquals(expectedSize, history.size(),
                    "Unexpected chat history size");
    }
    
    // Утилита для создания тестового агента
    protected TestAgentBuilder createTestAgent(String agentId) {
        return new TestAgentBuilder(agentId, mockAIClient);
    }
    
    // Вспомогательные классы
    public static class ConversationTurn {
        public final String userMessage;
        public final String aiResponse;
        public final Map<String, Object> metadata;
        
        public ConversationTurn(String userMessage, String aiResponse) {
            this(userMessage, aiResponse, Collections.emptyMap());
        }
        
        public ConversationTurn(String userMessage, String aiResponse, Map<String, Object> metadata) {
            this.userMessage = userMessage;
            this.aiResponse = aiResponse;
            this.metadata = metadata;
        }
    }
}

// Mock AI клиент
class MockAIClient {
    private final Map<String, String> responses = new ConcurrentHashMap<>();
    private final Map<String, Object> structuredResponses = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> responseMetadata = new ConcurrentHashMap<>();
    private final List<AICall> callHistory = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Queue<ConversationTurn>> conversations = new ConcurrentHashMap<>();
    
    public void addResponse(String promptPattern, String response) {
        responses.put(promptPattern, response);
    }
    
    public void addStructuredResponse(String promptPattern, Object response) {
        structuredResponses.put(promptPattern, response);
    }
    
    public void addResponseWithMetadata(String promptPattern, String response, Map<String, Object> metadata) {
        responses.put(promptPattern, response);
        responseMetadata.put(promptPattern, metadata);
    }
    
    public void addConversation(String agentId, List<ConversationTurn> turns) {
        conversations.put(agentId, new LinkedList<>(turns));
    }
    
    public String chat(String prompt, Map<String, Object> context) {
        callHistory.add(new AICall(prompt, context, System.currentTimeMillis()));
        
        // Поиск подходящего ответа по паттерну
        for (Map.Entry<String, String> entry : responses.entrySet()) {
            if (prompt.contains(entry.getKey()) || prompt.matches(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        // Проверка последовательных разговоров
        String agentId = (String) context.get("agentId");
        if (agentId != null && conversations.containsKey(agentId)) {
            Queue<ConversationTurn> queue = conversations.get(agentId);
            if (!queue.isEmpty()) {
                ConversationTurn turn = queue.poll();
                return turn.aiResponse;
            }
        }
        
        return "Default mock response for: " + prompt;
    }
    
    @SuppressWarnings("unchecked")
    public <T> T structuredOutput(String prompt, Class<T> outputType, Map<String, Object> context) {
        callHistory.add(new AICall(prompt, context, System.currentTimeMillis()));
        
        for (Map.Entry<String, Object> entry : structuredResponses.entrySet()) {
            if (prompt.contains(entry.getKey()) || prompt.matches(entry.getKey())) {
                return (T) entry.getValue();
            }
        }
        
        // Возвращаем default объект для типа
        try {
            return outputType.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            return null;
        }
    }
    
    public boolean wasPromptCalled(String promptPattern) {
        return callHistory.stream()
            .anyMatch(call -> call.prompt.contains(promptPattern) || 
                             call.prompt.matches(promptPattern));
    }
    
    public int getCallCount() {
        return callHistory.size();
    }
    
    public List<AICall> getCallsAfter(long timestamp) {
        return callHistory.stream()
            .filter(call -> call.timestamp > timestamp)
            .collect(Collectors.toList());
    }
    
    public void reset() {
        responses.clear();
        structuredResponses.clear();
        responseMetadata.clear();
        callHistory.clear();
        conversations.clear();
    }
    
    static class AICall {
        final String prompt;
        final Map<String, Object> context;
        final long timestamp;
        
        AICall(String prompt, Map<String, Object> context, long timestamp) {
            this.prompt = prompt;
            this.context = new HashMap<>(context);
            this.timestamp = timestamp;
        }
    }
}
```

## Специализированные тестовые утилиты

### 1. AsyncTestUtils

Утилиты для тестирования асинхронных операций.

```java
package ai.driftkit.workflow.test.framework.utils;

import ai.driftkit.workflow.engine.core.*;
import ai.driftkit.workflow.engine.async.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

public class AsyncTestUtils {
    
    // Создание отложенного асинхронного результата
    public static <T> CompletableFuture<StepResult<T>> createDelayedResult(T result, long delayMs) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(delayMs);
                return StepResult.finish(result);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return StepResult.fail(e);
            }
        });
    }
    
    // Создание асинхронного результата с прогрессом
    public static <T> ProgressiveAsyncResult<T> createProgressiveResult(
            T finalResult, 
            long totalDuration, 
            int progressUpdates) {
        
        return new ProgressiveAsyncResult<>(finalResult, totalDuration, progressUpdates);
    }
    
    // Проверка прогресса асинхронной задачи
    public static void assertAsyncProgress(WorkflowEngine engine, String taskId, 
                                          int minProgress, int maxProgress) {
        var state = engine.getAsyncStepState(taskId);
        assertTrue(state.isPresent(), "Async task not found: " + taskId);
        
        int progress = state.get().getProgress();
        assertTrue(progress >= minProgress && progress <= maxProgress,
                  String.format("Progress %d not in range [%d, %d]", progress, minProgress, maxProgress));
    }
    
    // Ожидание определенного прогресса
    public static void waitForProgress(WorkflowEngine engine, String taskId, 
                                      int targetProgress, long timeout, TimeUnit unit) {
        long endTime = System.currentTimeMillis() + unit.toMillis(timeout);
        
        while (System.currentTimeMillis() < endTime) {
            var state = engine.getAsyncStepState(taskId);
            if (state.isPresent() && state.get().getProgress() >= targetProgress) {
                return;
            }
            
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for progress", e);
            }
        }
        
        throw new AssertionError("Timeout waiting for progress " + targetProgress);
    }
    
    // Создание failing async результата
    public static <T> CompletableFuture<StepResult<T>> createFailingAsyncResult(
            Exception error, long delayMs) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(delayMs);
                return StepResult.<T>fail(error);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return StepResult.<T>fail(e);
            }
        });
    }
    
    // Утилита для тестирования timeout
    public static <T> CompletableFuture<StepResult<T>> createTimeoutResult(long timeoutMs) {
        CompletableFuture<StepResult<T>> future = new CompletableFuture<>();
        
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.schedule(() -> {
            future.completeExceptionally(new TimeoutException("Async operation timed out"));
        }, timeoutMs, TimeUnit.MILLISECONDS);
        
        return future;
    }
    
    // Класс для прогрессивных результатов
    public static class ProgressiveAsyncResult<T> {
        private final CompletableFuture<StepResult<T>> future;
        private final ProgressTracker progressTracker;
        
        public ProgressiveAsyncResult(T result, long totalDuration, int updates) {
            this.progressTracker = new ProgressTracker();
            this.future = CompletableFuture.supplyAsync(() -> {
                long updateInterval = totalDuration / updates;
                
                for (int i = 1; i <= updates; i++) {
                    try {
                        Thread.sleep(updateInterval);
                        int progress = (i * 100) / updates;
                        progressTracker.updateProgress(progress);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return StepResult.fail(e);
                    }
                }
                
                return StepResult.finish(result);
            });
        }
        
        public CompletableFuture<StepResult<T>> getFuture() {
            return future;
        }
        
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }
    }
}
```

### 2. RetryTestUtils

Утилиты для тестирования retry механизмов.

```java
package ai.driftkit.workflow.test.framework.utils;

import ai.driftkit.workflow.engine.core.*;
import ai.driftkit.workflow.engine.executor.*;
import ai.driftkit.workflow.test.framework.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class RetryTestUtils {
    
    // Проверка количества попыток retry через interceptor
    public static void assertRetryAttempts(TestExecutionInterceptor interceptor, 
                                          String workflowId, String stepId, 
                                          int expectedAttempts) {
        long attempts = interceptor.getExecutionHistory().stream()
            .filter(exec -> exec.workflowId.equals(workflowId) && 
                           exec.stepId.equals(stepId))
            .count();
        
        assertEquals(expectedAttempts, attempts,
                    "Unexpected retry attempts for step: " + stepId);
    }
    
    // Создание мокированного поведения шага с ошибками
    public static void setupFailingStep(TestExecutionInterceptor interceptor,
                                       String workflowId, String stepId,
                                       int failureCount, Object successResult) {
        
        AtomicInteger attempts = new AtomicInteger(0);
        
        interceptor.addStepMock(workflowId, stepId, input -> {
            int attempt = attempts.incrementAndGet();
            if (attempt <= failureCount) {
                throw new RuntimeException("Simulated failure #" + attempt);
            }
            return StepResult.finish(successResult);
        });
    }
    
    // Альтернативный подход через контекстные переменные
    public static <I, O> Function<I, StepResult<O>> createFailingStepWithContext(
            WorkflowContext context, String attemptKey,
            int failureCount, O successResult) {
        
        return input -> {
            int attempt = context.computeIfAbsent(attemptKey, k -> 0);
            attempt++;
            context.put(attemptKey, attempt);
            
            if (attempt <= failureCount) {
                return StepResult.fail(new RuntimeException("Simulated failure #" + attempt));
            }
            return StepResult.finish(successResult);
        };
    }
    
    // Создание шага с условными ошибками
    public static <I, O> Function<I, StepResult<O>> createConditionalFailingStep(
            Function<Integer, Boolean> shouldFail,
            Function<I, O> successLogic,
            String errorMessage) {
        
        AtomicInteger attempts = new AtomicInteger(0);
        
        return input -> {
            int attempt = attempts.incrementAndGet();
            if (shouldFail.apply(attempt)) {
                return StepResult.fail(new RuntimeException(errorMessage + " (attempt " + attempt + ")"));
            }
            return StepResult.finish(successLogic.apply(input));
        };
    }
    
    // Создание шага с разными типами ошибок
    public static <I, O> Function<I, StepResult<O>> createMultiErrorStep(
            Map<Integer, Exception> attemptErrors,
            O successResult) {
        
        AtomicInteger attempts = new AtomicInteger(0);
        
        return input -> {
            int attempt = attempts.incrementAndGet();
            Exception error = attemptErrors.get(attempt);
            
            if (error != null) {
                return StepResult.fail(error);
            }
            
            return StepResult.finish(successResult);
        };
    }
    
    // Проверка retry политики
    public static void assertRetryPolicyApplied(RetryPolicy policy, RetryMetrics metrics, String stepId) {
        assertTrue(metrics.getRetryAttempts(stepId) <= policy.getMaxRetries(),
                  "Retry attempts exceeded max retries");
        
        if (metrics.getRetryAttempts(stepId) > 0) {
            assertTrue(metrics.getLastRetryDelay(stepId) >= policy.getRetryDelay(),
                      "Retry delay less than configured");
        }
    }
    
    // Создание кастомной retry политики для тестов
    public static RetryPolicy createTestRetryPolicy(int maxRetries, long delay, 
                                                    Set<Class<? extends Exception>> retryableExceptions) {
        return RetryPolicy.builder()
            .maxRetries(maxRetries)
            .retryDelay(delay)
            .retryableExceptions(retryableExceptions)
            .exponentialBackoff(false)
            .build();
    }
    
    // Метрики для анализа retry поведения
    public static class RetryMetrics {
        private final Map<String, AtomicInteger> attemptCounts = new ConcurrentHashMap<>();
        private final Map<String, Long> lastRetryDelays = new ConcurrentHashMap<>();
        private final Map<String, List<Exception>> failures = new ConcurrentHashMap<>();
        
        public void recordAttempt(String stepId) {
            attemptCounts.computeIfAbsent(stepId, k -> new AtomicInteger(0)).incrementAndGet();
        }
        
        public void recordFailure(String stepId, Exception error) {
            failures.computeIfAbsent(stepId, k -> new ArrayList<>()).add(error);
        }
        
        public void recordRetryDelay(String stepId, long delay) {
            lastRetryDelays.put(stepId, delay);
        }
        
        public int getRetryAttempts(String stepId) {
            return attemptCounts.getOrDefault(stepId, new AtomicInteger(0)).get();
        }
        
        public long getLastRetryDelay(String stepId) {
            return lastRetryDelays.getOrDefault(stepId, 0L);
        }
        
        public List<Exception> getFailures(String stepId) {
            return failures.getOrDefault(stepId, Collections.emptyList());
        }
    }
}
```

### 3. ValidationTestUtils

Утилиты для валидации схем и данных.

```java
package ai.driftkit.workflow.test.framework.utils;

import ai.driftkit.workflow.engine.agent.*;
import ai.driftkit.workflow.engine.core.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import javax.validation.Validation;
import java.util.*;

public class ValidationTestUtils {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
    private static final Validator validator = validatorFactory.getValidator();
    
    // Валидация AI function схемы
    public static void assertSchemaValid(AIFunctionSchema schema) {
        assertNotNull(schema, "Schema is null");
        assertNotNull(schema.getName(), "Schema name is null");
        assertNotNull(schema.getDescription(), "Schema description is null");
        assertNotNull(schema.getParameters(), "Schema parameters is null");
        
        // Проверка JSON схемы
        try {
            JsonNode schemaNode = objectMapper.readTree(schema.getParameters());
            assertTrue(schemaNode.has("type"), "Schema missing 'type' field");
            assertTrue(schemaNode.has("properties"), "Schema missing 'properties' field");
        } catch (Exception e) {
            fail("Invalid JSON schema: " + e.getMessage());
        }
    }
    
    // Валидация соответствия данных схеме
    public static void assertDataMatchesSchema(Object data, AIFunctionSchema schema) {
        try {
            String dataJson = objectMapper.writeValueAsString(data);
            JsonNode dataNode = objectMapper.readTree(dataJson);
            JsonNode schemaNode = objectMapper.readTree(schema.getParameters());
            
            validateNode(dataNode, schemaNode);
        } catch (Exception e) {
            fail("Data validation failed: " + e.getMessage());
        }
    }
    
    // Bean validation
    public static <T> void assertBeanValid(T bean) {
        var violations = validator.validate(bean);
        assertTrue(violations.isEmpty(), 
                  "Bean validation failed: " + violations.stream()
                      .map(v -> v.getPropertyPath() + " " + v.getMessage())
                      .collect(Collectors.joining(", ")));
    }
    
    // Валидация workflow input/output типов
    public static void assertInputOutputTypesMatch(Class<?> outputType, Class<?> expectedInputType, 
                                                   WorkflowStep<?, ?> step) {
        // Здесь можно использовать reflection для проверки совместимости типов
        assertTrue(expectedInputType.isAssignableFrom(outputType),
                  String.format("Type mismatch: output type %s is not assignable to input type %s",
                               outputType.getSimpleName(), expectedInputType.getSimpleName()));
    }
    
    // Валидация StepResult
    public static <T> void assertStepResultValid(StepResult<T> result) {
        assertNotNull(result, "StepResult is null");
        
        switch (result) {
            case StepResult.Continue<T> cont -> {
                assertNotNull(cont.data(), "Continue data is null");
                if (cont.nextStepId() != null) {
                    assertFalse(cont.nextStepId().isEmpty(), "Next step ID is empty");
                }
            }
            case StepResult.Suspend<T> susp -> {
                assertNotNull(susp.suspensionData(), "Suspension data is null");
                assertNotNull(susp.resumeStepId(), "Resume step ID is null");
            }
            case StepResult.Branch<T> branch -> {
                assertNotNull(branch.event(), "Branch event is null");
                assertNotNull(branch.data(), "Branch data is null");
            }
            case StepResult.Finish<T> finish -> {
                // Finish может иметь null data
            }
            case StepResult.Fail<T> fail -> {
                assertNotNull(fail.error(), "Fail error is null");
            }
            case StepResult.Async<T> async -> {
                assertNotNull(async.taskId(), "Async task ID is null");
                assertNotNull(async.future(), "Async future is null");
            }
        }
    }
    
    // Валидация контекста workflow
    public static void assertContextValid(WorkflowContext context) {
        assertNotNull(context, "WorkflowContext is null");
        assertNotNull(context.getWorkflowId(), "Workflow ID is null");
        assertNotNull(context.getRunId(), "Run ID is null");
        assertNotNull(context.getInput(), "Input is null");
        
        // Проверка thread-safety
        Map<String, Object> values = new HashMap<>();
        context.forEach(values::put);
        
        // Проверка что значения можно прочитать
        values.forEach((key, value) -> {
            assertEquals(value, context.get(key), 
                        "Context value mismatch for key: " + key);
        });
    }
    
    private static void validateNode(JsonNode data, JsonNode schema) {
        JsonNode properties = schema.get("properties");
        JsonNode required = schema.get("required");
        
        if (required != null && required.isArray()) {
            for (JsonNode req : required) {
                String fieldName = req.asText();
                assertTrue(data.has(fieldName), 
                          "Required field missing: " + fieldName);
            }
        }
        
        if (properties != null) {
            properties.fields().forEachRemaining(entry -> {
                String fieldName = entry.getKey();
                JsonNode fieldSchema = entry.getValue();
                
                if (data.has(fieldName)) {
                    JsonNode fieldData = data.get(fieldName);
                    validateFieldType(fieldData, fieldSchema);
                }
            });
        }
    }
    
    private static void validateFieldType(JsonNode data, JsonNode schema) {
        String type = schema.get("type").asText();
        
        switch (type) {
            case "string" -> assertTrue(data.isTextual(), "Expected string");
            case "number" -> assertTrue(data.isNumber(), "Expected number");
            case "integer" -> assertTrue(data.isIntegralNumber(), "Expected integer");
            case "boolean" -> assertTrue(data.isBoolean(), "Expected boolean");
            case "array" -> assertTrue(data.isArray(), "Expected array");
            case "object" -> assertTrue(data.isObject(), "Expected object");
        }
    }
}
```

## Тестовые сценарии и DSL

### WorkflowTestScenario

DSL для создания читаемых тестовых сценариев.

```java
package ai.driftkit.workflow.test.framework;

import ai.driftkit.workflow.engine.core.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

public class WorkflowTestScenario<T> {
    private final WorkflowEngine engine;
    private final String workflowId;
    private WorkflowEngine.WorkflowExecution<T> currentExecution;
    private final List<ScenarioStep> steps = new ArrayList<>();
    private final Map<String, Object> scenarioContext = new HashMap<>();
    
    public WorkflowTestScenario(WorkflowEngine engine, String workflowId) {
        this.engine = engine;
        this.workflowId = workflowId;
    }
    
    // Начало выполнения workflow
    public WorkflowTestScenario<T> start(Object input) {
        steps.add(new StartStep(input));
        return this;
    }
    
    // Ожидание приостановки
    public WorkflowTestScenario<T> expectSuspended(String stepId) {
        steps.add(new ExpectSuspendedStep(stepId));
        return this;
    }
    
    // Ожидание завершения
    public WorkflowTestScenario<T> expectCompleted() {
        steps.add(new ExpectCompletedStep());
        return this;
    }
    
    // Ожидание ошибки
    public WorkflowTestScenario<T> expectFailed(Class<? extends Exception> errorType) {
        steps.add(new ExpectFailedStep(errorType));
        return this;
    }
    
    // Возобновление с данными
    public WorkflowTestScenario<T> resume(Object input) {
        steps.add(new ResumeStep(input));
        return this;
    }
    
    // Проверка промежуточного состояния
    public WorkflowTestScenario<T> checkState(Consumer<WorkflowInstance> stateChecker) {
        steps.add(new CheckStateStep(stateChecker));
        return this;
    }
    
    // Проверка контекста
    public WorkflowTestScenario<T> checkContext(String key, Object expectedValue) {
        steps.add(new CheckContextStep(key, expectedValue));
        return this;
    }
    
    // Ожидание события
    public WorkflowTestScenario<T> expectEvent(String eventType, Consumer<WorkflowEvent> eventChecker) {
        steps.add(new ExpectEventStep(eventType, eventChecker));
        return this;
    }
    
    // Параллельное выполнение
    public WorkflowTestScenario<T> parallel(Consumer<ParallelBuilder> parallelConfig) {
        ParallelBuilder builder = new ParallelBuilder();
        parallelConfig.accept(builder);
        steps.add(new ParallelStep(builder.getTasks()));
        return this;
    }
    
    // Задержка
    public WorkflowTestScenario<T> delay(long millis) {
        steps.add(new DelayStep(millis));
        return this;
    }
    
    // Сохранение значения в контекст сценария
    public WorkflowTestScenario<T> capture(String key, Function<WorkflowInstance, Object> extractor) {
        steps.add(new CaptureStep(key, extractor));
        return this;
    }
    
    // Выполнение сценария
    public ScenarioResult<T> execute() {
        ScenarioResult<T> result = new ScenarioResult<>();
        
        try {
            for (ScenarioStep step : steps) {
                step.execute(this);
            }
            result.setSuccess(true);
            result.setFinalResult(getFinalResult());
        } catch (Exception e) {
            result.setSuccess(false);
            result.setError(e);
        }
        
        return result;
    }
    
    // Получение финального результата
    private T getFinalResult() {
        if (currentExecution != null) {
            try {
                return currentExecution.get(1, TimeUnit.SECONDS);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
    
    // Базовый интерфейс для шагов сценария
    private interface ScenarioStep {
        void execute(WorkflowTestScenario<?> scenario) throws Exception;
    }
    
    // Реализации шагов
    private class StartStep implements ScenarioStep {
        private final Object input;
        
        StartStep(Object input) {
            this.input = input;
        }
        
        @Override
        public void execute(WorkflowTestScenario<?> scenario) {
            scenario.currentExecution = engine.execute(workflowId, input);
        }
    }
    
    private class ExpectSuspendedStep implements ScenarioStep {
        private final String expectedStepId;
        
        ExpectSuspendedStep(String expectedStepId) {
            this.expectedStepId = expectedStepId;
        }
        
        @Override
        public void execute(WorkflowTestScenario<?> scenario) throws Exception {
            // Ждем приостановки
            Thread.sleep(500);
            
            var instance = engine.getWorkflowInstance(scenario.currentExecution.getRunId());
            assertTrue(instance.isPresent(), "Workflow instance not found");
            assertEquals(WorkflowInstance.WorkflowStatus.SUSPENDED, instance.get().getStatus());
            assertEquals(expectedStepId, instance.get().getCurrentStepId());
        }
    }
    
    private class ResumeStep implements ScenarioStep {
        private final Object input;
        
        ResumeStep(Object input) {
            this.input = input;
        }
        
        @Override
        public void execute(WorkflowTestScenario<?> scenario) {
            scenario.currentExecution = engine.resume(scenario.currentExecution.getRunId(), input);
        }
    }
    
    // Другие реализации шагов...
    
    // Builder для параллельных задач
    public static class ParallelBuilder {
        private final List<Runnable> tasks = new ArrayList<>();
        
        public ParallelBuilder task(Runnable task) {
            tasks.add(task);
            return this;
        }
        
        public ParallelBuilder workflow(WorkflowEngine engine, String workflowId, Object input) {
            tasks.add(() -> engine.execute(workflowId, input));
            return this;
        }
        
        List<Runnable> getTasks() {
            return tasks;
        }
    }
    
    // Результат выполнения сценария
    public static class ScenarioResult<T> {
        private boolean success;
        private T finalResult;
        private Exception error;
        private final Map<String, Object> capturedValues = new HashMap<>();
        
        // Getters and setters...
        
        public void assertSuccess() {
            if (!success) {
                throw new AssertionError("Scenario failed", error);
            }
        }
    }
}
```

## Тестовые фабрики и фикстуры

### WorkflowTestDataFactory

Фабрика для создания тестовых данных.

```java
package ai.driftkit.workflow.test.framework.fixtures;

import ai.driftkit.workflow.engine.core.*;
import ai.driftkit.workflow.engine.agent.*;
import ai.driftkit.common.domain.chat.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class WorkflowTestDataFactory {
    
    private static final String[] SAMPLE_PROMPTS = {
        "What is the weather today?",
        "Tell me about artificial intelligence",
        "How can I improve my coding skills?",
        "Explain quantum computing",
        "What are the best practices for microservices?"
    };
    
    private static final String[] SAMPLE_RESPONSES = {
        "The weather is sunny with a high of 75°F",
        "Artificial Intelligence is a branch of computer science...",
        "To improve coding skills, practice regularly and read code...",
        "Quantum computing uses quantum mechanical phenomena...",
        "Microservices best practices include: single responsibility..."
    };
    
    // Создание тестовых сообщений чата
    public static ChatMessage createUserMessage(String content) {
        return ChatMessage.user(content)
            .withMetadata("test", true)
            .withMetadata("timestamp", System.currentTimeMillis());
    }
    
    public static ChatMessage createAssistantMessage(String content) {
        return ChatMessage.assistant(content)
            .withMetadata("model", "test-model")
            .withMetadata("tokens", ThreadLocalRandom.current().nextInt(10, 100));
    }
    
    public static ChatMessage createSystemMessage(String content) {
        return ChatMessage.system(content);
    }
    
    // Создание истории чата
    public static List<ChatMessage> createChatHistory(int exchanges) {
        List<ChatMessage> history = new ArrayList<>();
        history.add(createSystemMessage("You are a helpful assistant"));
        
        for (int i = 0; i < exchanges; i++) {
            history.add(createUserMessage(getRandomPrompt()));
            history.add(createAssistantMessage(getRandomResponse()));
        }
        
        return history;
    }
    
    // Создание тестовых workflow конфигураций
    public static WorkflowEngineConfig createTestConfig() {
        return WorkflowEngineConfig.builder()
            .stateRepository(new InMemoryWorkflowStateRepository())
            .asyncStepStateRepository(new InMemoryAsyncStepStateRepository())
            .suspensionDataRepository(new InMemorySuspensionDataRepository())
            .chatStore(new InMemoryChatStore())
            .coreThreads(2)
            .maxThreads(4)
            .queueCapacity(50)
            .shutdownTimeout(5000)
            .metricsEnabled(true)
            .build();
    }
    
    // Создание минимальной конфигурации
    public static WorkflowEngineConfig createMinimalConfig() {
        return WorkflowEngineConfig.builder()
            .stateRepository(new InMemoryWorkflowStateRepository())
            .coreThreads(1)
            .maxThreads(1)
            .build();
    }
    
    // Создание тестовых AI функций
    public static AIFunctionSchema createTestSchema(String name, Class<?> paramType) {
        return AIFunctionSchema.builder()
            .name(name)
            .description("Test function " + name)
            .parameters(generateJsonSchema(paramType))
            .build();
    }
    
    // Создание образцов workflow
    public static SimpleChatWorkflow createSimpleChatWorkflow() {
        return new SimpleChatWorkflow();
    }
    
    public static MultiStepWorkflow createMultiStepWorkflow() {
        return new MultiStepWorkflow();
    }
    
    public static AsyncWorkflow createAsyncWorkflow() {
        return new AsyncWorkflow();
    }
    
    // Генерация случайных данных
    public static String getRandomPrompt() {
        return SAMPLE_PROMPTS[ThreadLocalRandom.current().nextInt(SAMPLE_PROMPTS.length)];
    }
    
    public static String getRandomResponse() {
        return SAMPLE_RESPONSES[ThreadLocalRandom.current().nextInt(SAMPLE_RESPONSES.length)];
    }
    
    public static Map<String, Object> createRandomMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("requestId", UUID.randomUUID().toString());
        metadata.put("timestamp", System.currentTimeMillis());
        metadata.put("source", "test");
        metadata.put("priority", ThreadLocalRandom.current().nextInt(1, 10));
        return metadata;
    }
    
    // Создание тестовых input/output объектов
    public static <T> T createTestInput(Class<T> type) {
        try {
            if (type == String.class) {
                return type.cast("Test input string");
            } else if (type == Integer.class) {
                return type.cast(42);
            } else if (type == Map.class) {
                return type.cast(createRandomMetadata());
            } else {
                // Попытка создать через конструктор по умолчанию
                return type.getDeclaredConstructor().newInstance();
            }
        } catch (Exception e) {
            throw new RuntimeException("Cannot create test input for type: " + type, e);
        }
    }
    
    private static String generateJsonSchema(Class<?> type) {
        // Упрощенная генерация JSON схемы
        return """
            {
                "type": "object",
                "properties": {
                    "value": {
                        "type": "%s"
                    }
                },
                "required": ["value"]
            }
            """.formatted(getJsonType(type));
    }
    
    private static String getJsonType(Class<?> type) {
        if (type == String.class) return "string";
        if (type == Integer.class || type == Long.class) return "integer";
        if (type == Double.class || type == Float.class) return "number";
        if (type == Boolean.class) return "boolean";
        if (type.isArray() || List.class.isAssignableFrom(type)) return "array";
        return "object";
    }
    
    // Примеры тестовых workflow
    @Workflow(id = "simple-chat", version = "1.0", description = "Simple chat workflow")
    static class SimpleChatWorkflow {
        @Step
        @InitialStep
        public StepResult<String> chat(String input) {
            return StepResult.finish("Echo: " + input);
        }
    }
    
    @Workflow(id = "multi-step", version = "1.0", description = "Multi-step workflow")
    static class MultiStepWorkflow {
        @Step
        @InitialStep
        public StepResult<String> step1(String input) {
            return StepResult.continueWith(input.toUpperCase());
        }
        
        @Step
        public StepResult<String> step2(String input) {
            return StepResult.suspend(input + "!");
        }
        
        @Step
        public StepResult<String> step3(String input) {
            return StepResult.finish("Final: " + input);
        }
    }
    
    @Workflow(id = "async-workflow", version = "1.0", description = "Async workflow")
    static class AsyncWorkflow {
        @AsyncStep(taskId = "async-task")
        @Step
        @InitialStep
        public StepResult<String> process(String input, WorkflowContext context) {
            // Для настоящей async операции используем CompletableFuture
            CompletableFuture<StepResult<String>> future = CompletableFuture.supplyAsync(() -> {
                try {
                    Thread.sleep(1000);
                    return StepResult.finish("Async result: " + input);
                } catch (InterruptedException e) {
                    return StepResult.fail(e);
                }
            });
            
            // Сохраняем future в контексте
            context.put(WorkflowContext.Keys.ASYNC_FUTURE, future);
            
            // Возвращаем async результат
            return StepResult.async("async-task", future);
        }
    }
}
```

## Тестовые аннотации и расширения

### Кастомные аннотации для упрощения тестирования

```java
package ai.driftkit.workflow.test.framework.annotations;

import java.lang.annotation.*;

// Маркер для workflow тестов
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public @interface WorkflowTest {
    String workflowId();
    Class<?>[] mocks() default {};
    boolean async() default false;
    long timeout() default 30000; // ms
}

// Аннотация для мокирования AI ответов
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(MockAIResponses.class)
public @interface MockAIResponse {
    String prompt();
    String response() default "";
    String structuredResponse() default "";
    Class<?> responseType() default String.class;
}

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface MockAIResponses {
    MockAIResponse[] value();
}

// Аннотация для настройки workflow конфигурации
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface WorkflowConfig {
    int coreThreads() default 2;
    int maxThreads() default 4;
    int queueCapacity() default 100;
    boolean enableMetrics() default true;
}

// Аннотация для данных тестирования
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface TestData {
    String value() default "";
    Class<?> type() default Object.class;
}

// Аннотация для проверки событий
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExpectEvent {
    String type();
    String stepId() default "";
    int count() default 1;
}
```

### JUnit 5 Extension для workflow тестов

```java
package ai.driftkit.workflow.test.framework.junit;

import ai.driftkit.workflow.test.framework.*;
import ai.driftkit.workflow.test.framework.annotations.*;
import org.junit.jupiter.api.extension.*;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

public class WorkflowTestExtension implements 
        BeforeEachCallback, 
        AfterEachCallback, 
        ParameterResolver,
        TestInstancePostProcessor {
    
    private static final String ENGINE_KEY = "workflow.engine";
    private static final String MOCK_CLIENT_KEY = "mock.ai.client";
    
    @Override
    public void postProcessTestInstance(Object testInstance, ExtensionContext context) {
        // Инжектирование зависимостей в тестовый класс
        if (testInstance instanceof AgentWorkflowTestBase base) {
            setupMockAIResponses(base, context);
        }
    }
    
    @Override
    public void beforeEach(ExtensionContext context) {
        Method testMethod = context.getRequiredTestMethod();
        
        // Обработка @WorkflowConfig
        WorkflowConfig config = testMethod.getAnnotation(WorkflowConfig.class);
        if (config != null) {
            configureEngine(context, config);
        }
        
        // Обработка @MockAIResponse
        MockAIResponse[] responses = testMethod.getAnnotationsByType(MockAIResponse.class);
        if (responses.length > 0) {
            setupMockResponses(context, responses);
        }
    }
    
    @Override
    public void afterEach(ExtensionContext context) {
        // Проверка ожидаемых событий
        ExpectEvent expectEvent = context.getRequiredTestMethod()
            .getAnnotation(ExpectEvent.class);
        
        if (expectEvent != null) {
            verifyEvents(context, expectEvent);
        }
    }
    
    @Override
    public boolean supportsParameter(ParameterContext parameterContext, 
                                   ExtensionContext extensionContext) {
        Parameter parameter = parameterContext.getParameter();
        return parameter.isAnnotationPresent(TestData.class) ||
               parameter.getType() == WorkflowEngine.class ||
               parameter.getType() == MockAIClient.class;
    }
    
    @Override
    public Object resolveParameter(ParameterContext parameterContext, 
                                 ExtensionContext extensionContext) {
        Parameter parameter = parameterContext.getParameter();
        
        if (parameter.getType() == WorkflowEngine.class) {
            return getStore(extensionContext).get(ENGINE_KEY, WorkflowEngine.class);
        }
        
        if (parameter.getType() == MockAIClient.class) {
            return getStore(extensionContext).get(MOCK_CLIENT_KEY, MockAIClient.class);
        }
        
        TestData testData = parameter.getAnnotation(TestData.class);
        if (testData != null) {
            return WorkflowTestDataFactory.createTestInput(
                testData.type() != Object.class ? testData.type() : parameter.getType()
            );
        }
        
        return null;
    }
    
    private void configureEngine(ExtensionContext context, WorkflowConfig config) {
        WorkflowEngineConfig engineConfig = WorkflowEngineConfig.builder()
            .stateRepository(new InMemoryWorkflowStateRepository())
            .coreThreads(config.coreThreads())
            .maxThreads(config.maxThreads())
            .queueCapacity(config.queueCapacity())
            .metricsEnabled(config.enableMetrics())
            .build();
        
        WorkflowEngine engine = new WorkflowEngine(engineConfig);
        getStore(context).put(ENGINE_KEY, engine);
    }
    
    private void setupMockResponses(ExtensionContext context, MockAIResponse[] responses) {
        MockAIClient mockClient = getStore(context)
            .getOrComputeIfAbsent(MOCK_CLIENT_KEY, 
                                 k -> new MockAIClient(), 
                                 MockAIClient.class);
        
        for (MockAIResponse response : responses) {
            if (!response.response().isEmpty()) {
                mockClient.addResponse(response.prompt(), response.response());
            } else if (!response.structuredResponse().isEmpty()) {
                Object parsed = parseStructuredResponse(
                    response.structuredResponse(), 
                    response.responseType()
                );
                mockClient.addStructuredResponse(response.prompt(), parsed);
            }
        }
    }
    
    private void setupMockAIResponses(AgentWorkflowTestBase testInstance, 
                                     ExtensionContext context) {
        Class<?> testClass = testInstance.getClass();
        MockAIResponse[] classResponses = testClass.getAnnotationsByType(MockAIResponse.class);
        
        if (classResponses.length > 0) {
            setupMockResponses(context, classResponses);
        }
    }
    
    private void verifyEvents(ExtensionContext context, ExpectEvent expectEvent) {
        // Получение listener и проверка событий
        TestWorkflowListener listener = getTestListener(context);
        
        if (listener != null) {
            var events = listener.getEvents()
                .getEventsByType(expectEvent.type());
            
            assertEquals(expectEvent.count(), events.size(),
                        "Unexpected number of events of type: " + expectEvent.type());
            
            if (!expectEvent.stepId().isEmpty()) {
                assertTrue(events.stream()
                          .anyMatch(e -> expectEvent.stepId().equals(e.getStepId())),
                          "No event found for step: " + expectEvent.stepId());
            }
        }
    }
    
    private ExtensionContext.Store getStore(ExtensionContext context) {
        return context.getStore(ExtensionContext.Namespace.create(getClass()));
    }
    
    private Object parseStructuredResponse(String json, Class<?> type) {
        try {
            return new ObjectMapper().readValue(json, type);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse structured response", e);
        }
    }
    
    private TestWorkflowListener getTestListener(ExtensionContext context) {
        // Реализация получения listener из контекста
        return null;
    }
}
```

## Примеры использования

### 1. Тестирование простого аннотационного workflow

```java
@ExtendWith(WorkflowTestExtension.class)
class SimpleWorkflowTest extends AnnotationWorkflowTestBase {
    
    @BeforeEach
    void setup() {
        registerWorkflow(new SimpleGreetingWorkflow());
    }
    
    @Test
    @WorkflowTest(workflowId = "greeting-workflow")
    void testSimpleGreeting() {
        // Arrange
        String input = "World";
        
        // Act
        String result = executeAndWait("greeting-workflow", input, 5, TimeUnit.SECONDS);
        
        // Assert
        assertEquals("Hello, World!", result);
        assertWorkflowCompleted(getEvents().getLastRunId());
    }
    
    @WorkflowDefinition(id = "greeting-workflow")
    static class SimpleGreetingWorkflow {
        @WorkflowStep("greet")
        public StepResult<String> greet(String name) {
            return StepResult.finish("Hello, " + name + "!");
        }
    }
}
```

### 2. Тестирование workflow с suspend/resume

```java
@ExtendWith(WorkflowTestExtension.class)
class SuspendResumeWorkflowTest extends AnnotationWorkflowTestBase {
    
    @Test
    @WorkflowTest(workflowId = "approval-workflow")
    void testApprovalWorkflow() {
        // Создаем сценарий
        scenario("approval-workflow")
            .start(new ApprovalRequest("Purchase", 1000))
            .expectSuspended("wait-approval")
            .checkContext("requestId", notNullValue())
            .resume(new ApprovalDecision(true, "Approved by manager"))
            .expectCompleted()
            .execute()
            .assertSuccess();
    }
    
    @Test
    void testApprovalRejection() {
        var result = scenario("approval-workflow")
            .start(new ApprovalRequest("Purchase", 5000))
            .expectSuspended("wait-approval")
            .resume(new ApprovalDecision(false, "Budget exceeded"))
            .expectCompleted()
            .execute();
        
        assertEquals("Request rejected: Budget exceeded", result.getFinalResult());
    }
}
```

### 3. Тестирование AI агента

```java
@ExtendWith(WorkflowTestExtension.class)
class AIAgentWorkflowTest extends AgentWorkflowTestBase {
    
    @Test
    @WorkflowTest(workflowId = "chat-agent", mocks = {AIClient.class})
    @MockAIResponse(prompt = "What is the weather?", response = "It's sunny and 75°F")
    @MockAIResponse(prompt = "Tell me more", response = "Perfect day for outdoor activities!")
    void testChatAgent() {
        registerWorkflow(new ChatAgentWorkflow());
        
        // Первый запрос
        var execution1 = engine.execute("chat-agent", "What is the weather?");
        var response1 = execution1.get(5, TimeUnit.SECONDS);
        
        assertAICallMade("What is the weather?");
        assertTrue(response1.contains("sunny"));
        
        // Продолжение разговора
        var execution2 = engine.execute("chat-agent", "Tell me more");
        var response2 = execution2.get(5, TimeUnit.SECONDS);
        
        assertAICallCount(2);
        assertChatHistorySize("default-chat", 4); // 2 user + 2 assistant
    }
    
    @Test
    @WorkflowConfig(coreThreads = 4, maxThreads = 8)
    void testConcurrentAgents(@TestData List<String> prompts) {
        // Тест с параллельным выполнением нескольких агентов
        var futures = prompts.stream()
            .map(prompt -> engine.execute("chat-agent", prompt))
            .collect(Collectors.toList());
        
        // Ожидание завершения всех
        var results = futures.stream()
            .map(f -> f.get(10, TimeUnit.SECONDS))
            .collect(Collectors.toList());
        
        assertEquals(prompts.size(), results.size());
        assertAICallCount(prompts.size());
    }
}
```

### 4. Тестирование Fluent API workflow

```java
class FluentWorkflowTest extends FluentWorkflowTestBase {
    
    @Test
    void testFluentWorkflowBuilder() {
        // Создаем production workflow через fluent API
        var workflow = WorkflowBuilder.define("calc-workflow", Integer.class, Integer.class)
            .thenValue("double", (Integer x) -> x * 2)
            .thenValue("addTen", (Integer x) -> x + 10)
            .then("checkRange", (Integer x) -> {
                if (x > 50) {
                    return StepResult.finish(x);
                } else {
                    return StepResult.fail(new IllegalArgumentException("Too small: " + x));
                }
            })
            .build();
        
        // Регистрируем workflow
        registerProductionWorkflow(workflow);
        
        // Проверяем структуру графа
        assertGraphTopology("calc-workflow", "double", "addTen", "checkRange");
        
        // Успешный случай
        var result1 = executeAndWait("calc-workflow", 25, 5, TimeUnit.SECONDS);
        assertEquals(60, result1); // (25 * 2) + 10 = 60
        
        // Случай с ошибкой
        assertThrows(ExecutionException.class, () -> {
            executeAndWait("calc-workflow", 10, 5, TimeUnit.SECONDS);
        });
        
        // Проверяем историю выполнения
        var history = testInterceptor.getExecutionHistory();
        assertEquals(6, history.size()); // 3 шага для успеха + 3 шага для ошибки (до fail)
    }
    
    @Test
    void testAsyncFluentWorkflow() {
        // Создаем async handler
        var asyncHandler = new AsyncDataFetcher();
        
        // Создаем workflow с async шагом
        var workflow = WorkflowBuilder.define("async-calc", String.class, String.class)
            .then("fetch", (String input, WorkflowContext ctx) -> {
                // Запускаем async операцию
                return StepResult.async("fetch-task", 
                    AsyncTestUtils.createProgressiveResult(
                        "Data for " + input, 
                        3000, // 3 секунды
                        10    // 10 обновлений прогресса
                    ).getFuture()
                );
            })
            .thenValue("process", (String data) -> data.toUpperCase())
            .finishWithValue("finalize", (String data) -> "Processed: " + data)
            .withAsyncHandler("fetch-task", asyncHandler::handleFetch)
            .build();
        
        registerProductionWorkflow(workflow);
        
        // Выполняем workflow
        var execution = engine.execute("async-calc", "test");
        
        // Проверка прогресса async операции
        AsyncTestUtils.waitForProgress(engine, "fetch-task", 50, 2, TimeUnit.SECONDS);
        AsyncTestUtils.assertAsyncProgress(engine, "fetch-task", 40, 60);
        
        // Финальный результат
        var result = execution.get(5, TimeUnit.SECONDS);
        assertEquals("Processed: DATA FOR TEST", result);
    }
    
    @Test
    void testBranchingWorkflow() {
        // Создаем workflow с ветвлением
        var workflow = WorkflowBuilder.define("branching-workflow", Integer.class, String.class)
            .then("validate", (Integer input) -> {
                if (input < 0) {
                    return StepResult.fail(new IllegalArgumentException("Negative input"));
                }
                return StepResult.continueWith(input);
            })
            .branch(
                ctx -> ctx.step("validate").getOutputAs(Integer.class) > 100,
                // True branch - большие числа
                largeNumbers -> largeNumbers
                    .then("processLarge", (Integer n) -> StepResult.continueWith("Large: " + n))
                    .finishWithValue("formatLarge", s -> s + " (processed)"),
                // False branch - маленькие числа  
                smallNumbers -> smallNumbers
                    .then("processSmall", (Integer n) -> StepResult.continueWith("Small: " + n))
                    .finishWithValue("formatSmall", s -> s + " (handled)")
            )
            .build();
        
        registerProductionWorkflow(workflow);
        
        // Тест для большого числа
        var result1 = executeAndWait("branching-workflow", 150, 5, TimeUnit.SECONDS);
        assertEquals("Large: 150 (processed)", result1);
        
        // Тест для маленького числа
        var result2 = executeAndWait("branching-workflow", 50, 5, TimeUnit.SECONDS);
        assertEquals("Small: 50 (handled)", result2);
        
        // Проверяем, что выполнились правильные ветки
        var history = testInterceptor.getExecutionHistory();
        assertTrue(history.stream().anyMatch(exec -> exec.stepId.equals("processLarge")));
        assertTrue(history.stream().anyMatch(exec -> exec.stepId.equals("processSmall")));
    }
    
    // Вспомогательный класс для async обработки
    static class AsyncDataFetcher {
        public StepResult<String> handleFetch(Map<String, Object> params, 
                                             WorkflowContext context, 
                                             TaskProgressReporter reporter) {
            // Логика обработки async задачи
            return StepResult.continueWith("Fetched data");
        }
    }
}
```

### 5. Тестирование retry логики

```java
class RetryWorkflowTest extends FluentWorkflowTestBase {
    
    @Test
    void testRetryMechanism() {
        // Создаем production workflow с retry политикой
        var processStep = StepDefinition.of("process", 
            (String input) -> StepResult.finish("Default result"))
            .withRetryPolicy(
                RetryPolicyBuilder.retry()
                    .withMaxAttempts(3)
                    .withDelay(100)
                    .build()
            );
        
        var workflow = WorkflowBuilder.define("retry-workflow", String.class, String.class)
            .then(processStep)
            .build();
        
        registerProductionWorkflow(workflow);
        
        // Настраиваем шаг, который падает 2 раза
        RetryTestUtils.setupFailingStep(testInterceptor, "retry-workflow", "process", 2, "Success!");
        
        // Выполняем workflow
        var result = executeAndWait("retry-workflow", "input", 10, TimeUnit.SECONDS);
        
        // Проверяем retry метрики (1 начальная попытка + 2 retry = 3)
        RetryTestUtils.assertRetryAttempts(testInterceptor, "retry-workflow", "process", 3);
        assertEquals("Success!", result);
    }
    
    @Test
    void testRetryExhaustion() {
        // Создаем workflow с retry политикой
        var processStep = StepDefinition.of("process",
            (String input) -> StepResult.finish("Should not reach"))
            .withRetryPolicy(
                RetryPolicyBuilder.retry()
                    .withMaxAttempts(3)
                    .withDelay(100)
                    .build()
            );
        
        var workflow = WorkflowBuilder.define("retry-workflow-fail", String.class, String.class)
            .then(processStep)
            .build();
        
        registerProductionWorkflow(workflow);
        
        // Настраиваем шаг, который всегда падает
        testInterceptor.addStepError("retry-workflow-fail", "process", 
                                     new RuntimeException("Always fails"));
        
        // Ожидаем финальную ошибку после исчерпания retry
        assertThrows(ExecutionException.class, () -> {
            executeAndWait("retry-workflow-fail", "input", 10, TimeUnit.SECONDS);
        });
        
        // Проверяем количество попыток (1 + 3 retry = 4)
        RetryTestUtils.assertRetryAttempts(testInterceptor, "retry-workflow-fail", "process", 4);
    }
    
    @Test 
    void testRetryWithExponentialBackoff() {
        // Создаем workflow с экспоненциальным backoff
        var processStep = StepDefinition.of("process", this::processWithPotentialFailure)
            .withRetryPolicy(
                RetryPolicyBuilder.retry()
                    .withMaxAttempts(5)
                    .withDelay(100)
                    .exponentialBackoff()
                    .withMaxDelay(5000)
                    .withJitterFactor(0.1)
                    .withRetryOn(IOException.class, TimeoutException.class)
                    .withAbortOn(IllegalArgumentException.class)
                    .build()
            );
        
        var workflow = WorkflowBuilder.define("backoff-retry", String.class, String.class)
            .then("validate", this::validateInput)
            .then(processStep)
            .then("finalize", this::finalizeResult)
            .build();
        
        registerProductionWorkflow(workflow);
        
        // Настраиваем специфичные ошибки для разных попыток
        var attemptErrors = Map.of(
            1, new IOException("Network error"),
            2, new TimeoutException("Timeout"),
            3, new IOException("Connection reset")
        );
        
        AtomicInteger attempts = new AtomicInteger(0);
        testInterceptor.addStepMock("backoff-retry", "process", input -> {
            int attempt = attempts.incrementAndGet();
            Exception error = attemptErrors.get(attempt);
            if (error != null) {
                throw new RuntimeException(error);
            }
            return StepResult.continueWith("Success after " + attempt + " attempts");
        });
        
        var result = executeAndWait("backoff-retry", "test", 10, TimeUnit.SECONDS);
        assertTrue(result.contains("Success after 4 attempts"));
        
        // Проверяем историю выполнения
        var history = testInterceptor.getExecutionHistory();
        var processExecutions = history.stream()
            .filter(exec -> exec.stepId.equals("process"))
            .collect(Collectors.toList());
        
        // Проверяем увеличивающиеся интервалы между попытками
        for (int i = 1; i < processExecutions.size(); i++) {
            long interval = processExecutions.get(i).timestamp - processExecutions.get(i-1).timestamp;
            assertTrue(interval >= 100 * Math.pow(2, i-1), "Backoff interval too short");
        }
    }
    
    // Вспомогательные методы для workflow
    private StepResult<String> validateInput(String input) {
        return StepResult.continueWith(input);
    }
    
    private StepResult<String> processWithPotentialFailure(String input) {
        // Будет переопределено mock'ом
        return StepResult.continueWith(input);
    }
    
    private StepResult<String> finalizeResult(String input) {
        return StepResult.finish("Finalized: " + input);
    }
}
```

## Интеграционное тестирование

### Полный сценарий интеграционного теста

```java
@SpringBootTest
@ExtendWith(WorkflowTestExtension.class)
class WorkflowIntegrationTest extends AgentWorkflowTestBase {
    
    @Autowired
    private WorkflowEngine productionEngine;
    
    @Test
    void testCompleteBusinessScenario() {
        // Регистрация всех необходимых workflow
        productionEngine.register(new OrderProcessingWorkflow());
        productionEngine.register(new PaymentWorkflow());
        productionEngine.register(new NotificationWorkflow());
        
        // Настройка mock ответов для внешних сервисов
        mockAIResponse("validate order.*", """
            {
                "valid": true,
                "availableItems": ["item1", "item2"],
                "estimatedDelivery": "2024-01-15"
            }
            """);
        
        // Запуск основного workflow
        var orderRequest = new OrderRequest(
            "customer123",
            List.of("item1", "item2"),
            new ShippingAddress("123 Main St", "City", "12345")
        );
        
        var scenario = scenario("order-processing")
            .start(orderRequest)
            .expectEvent("order.validated", event -> {
                assertEquals("customer123", event.getData().get("customerId"));
            })
            .expectSuspended("payment-approval")
            .capture("orderId", instance -> instance.getContext().get("orderId"))
            .resume(new PaymentInfo("4111111111111111", "123"))
            .expectEvent("payment.processed", event -> {
                assertTrue((Boolean) event.getData().get("success"));
            })
            .parallel(parallel -> parallel
                .workflow(productionEngine, "notification-workflow", 
                         Map.of("type", "order_confirmed", "orderId", "${orderId}"))
                .task(() -> verifyDatabaseState("${orderId}"))
            )
            .expectCompleted()
            .execute();
        
        scenario.assertSuccess();
        
        // Дополнительные проверки
        var finalOrder = (Order) scenario.getFinalResult();
        assertEquals(OrderStatus.CONFIRMED, finalOrder.getStatus());
        assertNotNull(finalOrder.getTrackingNumber());
        
        // Проверка метрик
        var metrics = productionEngine.getMetrics();
        assertTrue(metrics.getSuccessfulExecutions() > 0);
        assertTrue(metrics.getAverageExecutionTime() < 5000); // < 5 секунд
    }
    
    private void verifyDatabaseState(String orderId) {
        // Проверка состояния в БД
    }
}
```

## Best Practices

### 1. Организация тестов

- Группируйте тесты по функциональности (unit, integration, e2e)
- Используйте наследование для переиспользования настроек
- Изолируйте тесты друг от друга
- Используйте @Nested классы для логической группировки

### 2. Mock стратегии

- Мокайте только внешние зависимости
- Используйте реальные implementations где возможно
- Создавайте переиспользуемые mock конфигурации
- Документируйте mock поведение

### 3. Асинхронное тестирование

- Всегда устанавливайте разумные timeout
- Используйте polling вместо sleep
- Проверяйте промежуточные состояния
- Обрабатывайте interruption правильно

### 4. Производительность тестов

- Используйте параллельное выполнение где возможно
- Минимизируйте setup/teardown операции
- Кешируйте дорогие объекты
- Профилируйте медленные тесты

## Заключение

Данный тестовый фреймворк обеспечивает:

1. **Полное покрытие** - поддержка всех типов workflow и сценариев
2. **Простоту использования** - интуитивный API и готовые утилиты
3. **Изоляцию** - каждый тест независим и воспроизводим
4. **Расширяемость** - легко добавлять новые возможности
5. **Производительность** - оптимизация для быстрого выполнения
6. **Отладку** - подробные сообщения об ошибках и метрики

Фреймворк готов к использованию и может быть расширен по мере развития workflow engine.
# План рефакторинга DriftKit Workflow Test Framework

## Цель
Преобразовать существующий test framework из говнокода в production-ready решение с чистой архитектурой, понятным API и надёжной реализацией.

## Подход
**БЕЗ ОБРАТНОЙ СОВМЕСТИМОСТИ** - переделываем всё с нуля, как новый модуль.

## Текущие проблемы

### Критические
1. **Сломанная иерархия наследования** - FluentWorkflowTestBase наследует от AnnotationWorkflowTestBase
2. **Небезопасное приведение типов** - @SuppressWarnings("unchecked") по всему коду
3. **Переусложнённое API** - множество дублирующих методов с неочевидной разницей
4. **Отсутствие обработки ошибок** - нет валидации параметров, нет fallback поведения
5. **Thread.sleep() хаки** - костыли вместо proper synchronization
6. **Смешанная ответственность** - классы делают слишком много разных вещей

### Архитектурные
1. **Тесная связанность** - все компоненты зависят друг от друга
2. **Отсутствие паттернов** - нет Builder, Factory, Strategy где нужно
3. **Плохие абстракции** - MockAIClient extends ModelClient<Void>
4. **Нет документации** - сложная логика без объяснений

## Чек-лист выполнения

### 🔲 Фаза 1: Критические исправления (1-2 недели)
- [ ] 1.1 Исправить иерархию наследования
- [ ] 1.2 Реализовать безопасное приведение типов
- [ ] 1.3 Упростить API через Builder pattern
- [ ] 1.4 Добавить валидацию и обработку ошибок
- [ ] 1.5 Убрать Thread.sleep() хаки
- [ ] **CHECKPOINT**: Компиляция и запуск тестов

### 🔲 Фаза 2: Архитектурные улучшения (2-3 недели)
- [ ] 2.1 Разделить TestExecutionInterceptor на компоненты
- [ ] 2.2 Внедрить паттерны проектирования
- [ ] 2.3 Улучшить абстракции
- [ ] **CHECKPOINT**: Компиляция и запуск тестов

### 🔲 Фаза 3: Новая функциональность (3-4 недели)
- [ ] 3.1 Интеграция с тестовыми фреймворками
- [ ] 3.2 Поддержка параллельного тестирования
- [ ] 3.3 Performance testing capabilities
- [ ] 3.4 Улучшенная отладка и визуализация
- [ ] **CHECKPOINT**: Компиляция и запуск тестов

### 🔲 Фаза 4: Документация и примеры (1 неделя)
- [ ] 4.1 Comprehensive JavaDoc
- [ ] 4.2 Примеры использования
- [ ] 4.3 Best practices guide
- [ ] **FINAL CHECKPOINT**: Полная проверка

## План рефакторинга

### Фаза 1: Критические исправления

#### 1.1 Новая иерархия классов (БЕЗ совместимости)
```java
// Базовый класс с общей функциональностью
public abstract class WorkflowTestBase {
    protected WorkflowEngine engine;
    protected WorkflowTestContext testContext;
    
    @BeforeEach
    void setupBase() {
        this.testContext = new WorkflowTestContext();
        this.engine = createEngine();
    }
    
    protected abstract WorkflowEngine createEngine();
}

// Для аннотаций - чистая реализация
public abstract class AnnotationWorkflowTest extends WorkflowTestBase {
    @Override
    protected WorkflowEngine createEngine() {
        return WorkflowEngine.builder()
            .withAnnotationScanning(true)
            .build();
    }
}

// Для fluent API - независимая реализация  
public abstract class FluentWorkflowTest extends WorkflowTestBase {
    @Override
    protected WorkflowEngine createEngine() {
        return WorkflowEngine.builder()
            .withFluentSupport(true)
            .build();
    }
}
```

#### 1.2 Type-safe mocking system
```java
// Новый API с полной type safety
public class TypeSafeMockBuilder<I, O> {
    private final Class<I> inputType;
    private final Class<O> outputType;
    
    public MockDefinition<I, O> build(Function<I, StepResult<O>> behavior) {
        return new MockDefinition<>(inputType, outputType, behavior);
    }
}

// Использование:
mockRegistry.register(
    MockBuilder.forStep("validate", OrderRequest.class, ValidationResult.class)
        .withBehavior(order -> StepResult.continueWith(new ValidationResult(true)))
        .build()
);
```

#### 1.3 Чистый Builder API
```java
// Новый fluent API для мокирования
public class MockConfiguration {
    public static MockBuilder mock() {
        return new MockBuilder();
    }
}

// Использование:
testContext.configure(config -> config
    .mock().workflow("order-workflow").step("validate")
        .when(OrderRequest.class, order -> order.getAmount() > 1000)
        .thenReturn(ValidationResult.class, result -> result.approved())
    
    .mock().workflow("order-workflow").step("process")
        .always()
        .thenFail(new ServiceUnavailableException())
    
    .mock().workflow("payment-workflow").step("charge")
        .times(2).thenFail(new RetryableException())
        .afterwards().thenSucceed(PaymentResult.success())
);
```

#### 1.4 Proper error handling
```java
public class WorkflowTestContext {
    private final ValidationHelper validator = new ValidationHelper();
    
    public void registerMock(MockDefinition mock) {
        validator.requireNonNull(mock, "mock");
        validator.requireNonBlank(mock.getWorkflowId(), "workflowId");
        validator.requireNonBlank(mock.getStepId(), "stepId");
        
        try {
            mockRegistry.register(mock);
        } catch (Exception e) {
            throw new WorkflowTestException(
                "Failed to register mock for %s.%s".formatted(
                    mock.getWorkflowId(), 
                    mock.getStepId()
                ), e
            );
        }
    }
}
```

#### 1.5 Proper synchronization
```java
// Замена Thread.sleep на нормальные механизмы
public class WorkflowAwaiter {
    public <T> T await(String description, Supplier<T> supplier) {
        return Awaitility.await(description)
            .atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofMillis(50))
            .ignoreExceptions()
            .until(supplier::get, Objects::nonNull);
    }
    
    public WorkflowInstance awaitCompletion(String runId) {
        return await("workflow completion", 
            () -> {
                WorkflowInstance instance = repository.get(runId);
                return instance != null && instance.isTerminal() ? instance : null;
            }
        );
    }
}
```

#### CHECKPOINT после Фазы 1:
```bash
# Компиляция
mvn clean compile -pl driftkit-workflow-test-framework

# Тесты
mvn test -pl driftkit-workflow-test-framework

# Ожидаемый результат: BUILD SUCCESS
```

### Фаза 2: Архитектурные улучшения

#### 2.1 Компонентная архитектура
```java
// Разделяем монолит на специализированные компоненты

// 1. Mock management
public interface MockRegistry {
    void register(MockDefinition<?> mock);
    Optional<MockDefinition<?>> find(ExecutionContext context);
    void clear();
}

// 2. Execution tracking
public interface ExecutionTracker {
    void recordExecution(StepExecution execution);
    ExecutionHistory getHistory();
    void reset();
}

// 3. Assertion engine
public interface AssertionEngine {
    StepAssertions assertStep(String workflowId, String stepId);
    WorkflowAssertions assertWorkflow(String workflowId);
    ExecutionAssertions assertExecutions();
}

// 4. Test coordinator
public class WorkflowTestOrchestrator {
    private final MockRegistry mocks;
    private final ExecutionTracker tracker;
    private final AssertionEngine assertions;
    
    // Координирует работу всех компонентов
}
```

#### 2.2 Design patterns implementation
```java
// Strategy pattern для разных типов assertions
public interface AssertionStrategy {
    void verify(ExecutionHistory history, ExpectedBehavior expected);
}

public class StrictOrderAssertionStrategy implements AssertionStrategy {
    // Проверяет точный порядок выполнения
}

public class EventualConsistencyAssertionStrategy implements AssertionStrategy {
    // Проверяет что шаги выполнились в любом порядке
}

// Factory pattern для создания тестовых контекстов
public class TestContextFactory {
    public static WorkflowTestContext create(TestConfiguration config) {
        return switch (config.getMode()) {
            case UNIT -> new UnitTestContext(config);
            case INTEGRATION -> new IntegrationTestContext(config);
            case PERFORMANCE -> new PerformanceTestContext(config);
        };
    }
}

// Template method для тестовых сценариев
public abstract class WorkflowTestScenario {
    public final void execute() {
        setup();
        configureMocks();
        executeWorkflow();
        verify();
        cleanup();
    }
    
    protected abstract void configureMocks();
    protected abstract void executeWorkflow();
    protected abstract void verify();
}
```

#### 2.3 Clean abstractions
```java
// Чистые интерфейсы без лишнего наследования
public interface MockBehavior<I, O> {
    StepResult<O> execute(I input, ExecutionContext context);
}

public interface WorkflowTestHarness {
    void registerWorkflow(WorkflowDefinition definition);
    ExecutionResult execute(String workflowId, Object input);
    void reset();
}

// Composition over inheritance
public class SmartMockClient implements AIClient {
    private final ResponseStrategy strategy;
    private final MetricsCollector metrics;
    
    @Override
    public Response process(Request request) {
        metrics.recordRequest(request);
        Response response = strategy.generateResponse(request);
        metrics.recordResponse(response);
        return response;
    }
}
```

#### CHECKPOINT после Фазы 2:
```bash
# Полная пересборка
mvn clean install -pl driftkit-workflow-test-framework -am

# Запуск всех тестов
mvn test -pl driftkit-workflow-test-framework

# Проверка что старые тесты адаптированы
mvn test -pl driftkit-workflow-test-framework \
    -Dtest=ChatWorkflowTestExample,FluentWorkflowTestExample,RetryWorkflowTestExample
```

### Фаза 3: Новая функциональность

#### 3.1 Framework integrations
```java
// Mockito style mocking
@ExtendWith(WorkflowTestExtension.class)
class OrderWorkflowTest {
    @Mock AIClient aiClient;
    @InjectMocks OrderWorkflow workflow;
    
    @Test
    void testWithMockito() {
        when(aiClient.process(any())).thenReturn(mockResponse());
        
        var result = workflow.execute(orderRequest());
        
        assertThat(result).satisfies(order -> {
            assertThat(order.getStatus()).isEqualTo("APPROVED");
            verify(aiClient, times(1)).process(any());
        });
    }
}

// AssertJ style assertions
assertThat(workflowExecution)
    .hasExecutedSteps("validate", "process", "complete")
    .hasNoFailures()
    .completedWithin(Duration.ofSeconds(5))
    .producedResult(matching(result -> result.getStatus().equals("SUCCESS")));
```

#### 3.2 Parallel testing support
```java
@ParallelTest(threads = 10)
class ConcurrentWorkflowTest {
    @Test
    void testConcurrentExecutions() {
        var executions = IntStream.range(0, 100)
            .parallel()
            .mapToObj(i -> executeAsync("workflow", input(i)))
            .collect(toList());
            
        awaitAll(executions);
        
        assertThat(executions)
            .allMatch(ExecutionResult::isSuccessful)
            .extracting(ExecutionResult::getDuration)
            .allMatch(duration -> duration.toMillis() < 100);
    }
}
```

#### 3.3 Performance testing
```java
@PerformanceTest
class WorkflowPerformanceTest {
    @Test
    @Benchmark(warmup = 10, iterations = 100)
    void measureThroughput(BenchmarkContext context) {
        var result = executeWorkflow("fast-workflow", testInput());
        context.recordSuccess();
    }
    
    @AfterAll
    void reportMetrics(PerformanceReport report) {
        assertThat(report.getPercentile(95)).isLessThan(Duration.ofMillis(50));
        assertThat(report.getThroughput()).isGreaterThan(1000); // ops/sec
    }
}
```

#### 3.4 Enhanced debugging
```java
// Automatic workflow visualization
@Test
@VisualizeExecution
void debugComplexWorkflow() {
    var result = executeWorkflow("complex-workflow", input);
    // Автоматически генерирует:
    // - target/workflow-reports/complex-workflow-execution.html
    // - target/workflow-reports/complex-workflow-timeline.svg
}

// Execution replay
@Test
void replayFailedExecution() {
    var recording = ExecutionRecording.load("failed-execution-123.json");
    var result = replayWithMocks(recording);
    
    assertThat(result).succeeds(); // После фикса
}
```

#### CHECKPOINT после Фазы 3:
```bash
# Полный прогон с новыми features
mvn clean test -pl driftkit-workflow-test-framework

# Performance тесты
mvn test -pl driftkit-workflow-test-framework \
    -Dgroups=performance

# Проверка параллельных тестов
mvn test -pl driftkit-workflow-test-framework \
    -Dparallel=methods -DthreadCount=4
```

### Фаза 4: Документация и примеры

#### 4.1 Comprehensive documentation
- JavaDoc для всех public API
- README.md с quick start
- ARCHITECTURE.md с описанием дизайна
- BEST_PRACTICES.md с рекомендациями

#### 4.2 Примеры для всех use cases
```
examples/
├── basic/
│   ├── SimpleWorkflowTest.java
│   ├── MockingExamples.java
│   └── AssertionExamples.java
├── advanced/
│   ├── ParallelTestingExample.java
│   ├── PerformanceTestExample.java
│   └── ComplexBranchingExample.java
└── integration/
    ├── MockitoIntegrationExample.java
    ├── AssertJIntegrationExample.java
    └── SpringBootTestExample.java
```

#### 4.3 Best practices guide
- Как организовать тесты
- Когда использовать моки
- Performance testing guidelines
- Debugging techniques

#### FINAL CHECKPOINT:
```bash
# Финальная проверка всего
mvn clean install -pl driftkit-workflow-test-framework -am

# Запуск всех тестов включая примеры
mvn test -pl driftkit-workflow-test-framework -DincludeExamples=true

# Генерация отчётов
mvn site -pl driftkit-workflow-test-framework

# Проверка документации
mvn javadoc:javadoc -pl driftkit-workflow-test-framework
```

## Метрики успеха

1. **Все тесты проходят** после каждой фазы
2. **Нет @SuppressWarnings("unchecked")** в production коде
3. **100% JavaDoc** покрытие public API
4. **Cyclomatic complexity < 10** для всех методов
5. **Время написания теста** сокращено на 70%
6. **Понятность API** - новый разработчик пишет тест за 5 минут

## Timeline

- **Неделя 1-2**: Фаза 1 + checkpoint
- **Неделя 3-5**: Фаза 2 + checkpoint
- **Неделя 6-9**: Фаза 3 + checkpoint
- **Неделя 10**: Фаза 4 + final checkpoint

## Определение готовности (Definition of Done)

Каждая фаза считается завершённой когда:
1. ✅ Весь код написан и откомпилирован
2. ✅ Все тесты проходят (mvn test)
3. ✅ Нет критических SonarQube issues
4. ✅ Code review пройден
5. ✅ Документация обновлена
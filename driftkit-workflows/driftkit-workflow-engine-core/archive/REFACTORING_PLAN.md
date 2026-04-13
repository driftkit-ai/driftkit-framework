# Детальный план рефакторинга WorkflowEngine и WorkflowAnalyzer

## Обзор
Этот документ содержит подробный план рефакторинга модуля workflow-engine-core с целью улучшения архитектуры, разделения ответственности и поддержки параллельного выполнения шагов.

## Фаза 1: Простой изменяемый WorkflowContext

### Цель
Заменить неизменяемый record WorkflowContext на изменяемый класс с ConcurrentHashMap для поддержки параллельных шагов.

### Изменения в файле `WorkflowContext.java`

#### 1.1. Изменение структуры класса
```java
// БЫЛО: record с immutable полями
public record WorkflowContext(
    String runId,
    Object triggerData,
    Map<String, Object> stepOutputs,
    Object workflowInstance
)

// СТАНЕТ: обычный класс с ConcurrentHashMap
@Slf4j
@Getter
public class WorkflowContext {
    private final String runId;
    private final Object triggerData;
    private final ConcurrentHashMap<String, Object> stepOutputs;
    private final ConcurrentHashMap<String, Object> customData;
    private final Object workflowInstance;
}
```

#### 1.2. Новые методы для изменения контекста
```java
// Методы для работы со step outputs (только для внутреннего использования движком):
public void setStepOutput(String stepId, Object output)
public void setStepOutputs(Map<String, Object> outputs)

// Методы для работы с custom data (для пользователей):
public void setContextValue(String key, Object value)
public <T> T getContextValue(String key, Class<T> type)
public <T> T getContextValueOrDefault(String key, Class<T> type, T defaultValue)

// Удобные helper методы для частых типов:
public String getString(String key)
public String getStringOrDefault(String key, String defaultValue)
public Integer getInt(String key)
public Integer getIntOrDefault(String key, Integer defaultValue)
public Long getLong(String key)
public Long getLongOrDefault(String key, Long defaultValue)
public Boolean getBoolean(String key)
public Boolean getBooleanOrDefault(String key, Boolean defaultValue)
public Double getDouble(String key)
public Double getDoubleOrDefault(String key, Double defaultValue)
public <T> List<T> getList(String key, Class<T> elementType)
public <K, V> Map<K, V> getMap(String key, Class<K> keyType, Class<V> valueType)
```

#### 1.3. Удаление старых методов
```java
// УДАЛИТЬ методы withStepOutput и withStepOutputs полностью
// Больше не нужны, так как контекст теперь изменяемый

// УДАЛИТЬ метод updateContext - он создавал путаницу
```

#### 1.4. Реализация helper методов
```java
public String getString(String key) {
    return getContextValue(key, String.class);
}

public String getStringOrDefault(String key, String defaultValue) {
    String value = getString(key);
    return value != null ? value : defaultValue;
}

public Integer getInt(String key) {
    return getContextValue(key, Integer.class);
}

public Integer getIntOrDefault(String key, Integer defaultValue) {
    Integer value = getInt(key);
    return value != null ? value : defaultValue;
}

// Аналогично для других типов...

@SuppressWarnings("unchecked")
public <T> List<T> getList(String key, Class<T> elementType) {
    Object value = customData.get(key);
    if (value instanceof List) {
        return (List<T>) value;
    }
    return null;
}

@SuppressWarnings("unchecked")
public <K, V> Map<K, V> getMap(String key, Class<K> keyType, Class<V> valueType) {
    Object value = customData.get(key);
    if (value instanceof Map) {
        return (Map<K, V>) value;
    }
    return null;
}
```

### Изменения в других файлах

#### 1.5. WorkflowInstance.java
```java
// Метод updateContext теперь работает напрямую со step outputs:
public void updateContext(String key, Object value) {
    // Это внутренний метод движка, работает только со step outputs
    this.context.setStepOutput(key, value);
}

// Добавить метод для пользовательских данных:
public void setContextValue(String key, Object value) {
    this.context.setContextValue(key, value);
}
```

#### 1.6. WorkflowEngine.java (методы, использующие контекст)
```java
// В методе executeWorkflow:
// БЫЛО: instance.updateContext(currentStep.id(), cont.data());
// СТАНЕТ: instance.updateContext(currentStep.id(), cont.data()); // без изменений, это системный вызов

// В методе resume:
// БЫЛО: instance.updateContext(Keys.RESUMED_STEP_INPUT, suspensionData.originalStepInput());
// СТАНЕТ: instance.updateContext(Keys.RESUMED_STEP_INPUT, suspensionData.originalStepInput()); // без изменений

// Везде где использовался withStepOutput:
// БЫЛО: context = context.withStepOutput(key, value);
// СТАНЕТ: context.setStepOutput(key, value);
```

## Фаза 2: Интеграция Workflow Builder с WorkflowAnalyzer

### Цель
Обеспечить единый подход к определению workflow через аннотации и builder API.

### Новые классы

#### 2.1. Создать `BuilderToGraphConverter.java`
```java
package ai.driftkit.workflow.engine.core;

/**
 * Конвертирует workflow, созданный через builder API, в WorkflowGraph.
 */
public class BuilderToGraphConverter {
    public static WorkflowGraph<?, ?> convert(ai.driftkit.workflow.engine.builder.Workflow<?, ?> builderWorkflow) {
        // Логика конвертации
    }
}
```

#### 2.2. Обновить `WorkflowAnalyzer.java`
```java
// Добавить метод:
public static <T, R> WorkflowGraph<T, R> analyzeBuilder(
    ai.driftkit.workflow.engine.builder.Workflow<T, R> builderWorkflow) {
    WorkflowGraph<T, R> graph = builderWorkflow.build();
    // Дополнительный анализ и валидация
    return enhanceGraph(graph);
}
```

### Изменения в существующих классах

#### 2.3. WorkflowEngine.java
```java
// Добавить метод регистрации для builder workflows:
public void register(ai.driftkit.workflow.engine.builder.Workflow<?, ?> builderWorkflow) {
    WorkflowGraph<?, ?> graph = WorkflowAnalyzer.analyzeBuilder(builderWorkflow);
    register(graph);
}
```

#### 2.4. Workflow.java (builder)
```java
// Добавить поддержку async шагов:
public Workflow<T, R> thenAsync(String asyncTaskId, StepDefinition stepDef) {
    // Реализация
}
```

## Фаза 3: Извлечение StepRouter из WorkflowEngine ✅

**Статус**: Завершено

**Что сделано**:
1. Создан интерфейс StepRouter с методами маршрутизации
2. Создан DefaultStepRouter с реализацией логики маршрутизации
3. WorkflowEngine теперь использует StepRouter вместо встроенных методов
4. Удалены старые методы findNextStep, findBranchTarget, findStepForInputType из WorkflowEngine

**Результат**: Логика маршрутизации вынесена в отдельный компонент, что улучшает модульность и тестируемость.

### Цель
Вынести всю логику маршрутизации в отдельный компонент.

### Новые классы

#### 3.1. Создать интерфейс `StepRouter.java`
```java
package ai.driftkit.workflow.engine.core;

public interface StepRouter {
    String findNextStep(WorkflowGraph<?, ?> graph, String currentStepId, Object data);
    String findBranchTarget(WorkflowGraph<?, ?> graph, String currentStepId, Object event);
    String findStepForInputType(WorkflowGraph<?, ?> graph, Class<?> inputType, String excludeStepId);
}
```

#### 3.2. Создать `DefaultStepRouter.java`
```java
package ai.driftkit.workflow.engine.core;

@Slf4j
public class DefaultStepRouter implements StepRouter {
    // Перенести методы из WorkflowEngine:
    // - findNextStep (строки 799-870)
    // - findBranchTarget (строки 876-912)
    // - findStepForInputType (строки 917-939)
}
```

### Изменения в WorkflowEngine.java

#### 3.3. Добавить поле и конструктор
```java
private final StepRouter stepRouter;

// В конструкторе:
this.stepRouter = new DefaultStepRouter();
```

#### 3.4. Заменить вызовы методов
```java
// БЫЛО: String nextStepId = findNextStep(graph, currentStep.id(), cont.data());
// СТАНЕТ: String nextStepId = stepRouter.findNextStep(graph, currentStep.id(), cont.data());
```

## Фаза 4: Улучшение WorkflowExecutor ✅

**Статус**: Завершено

**Что сделано**:
1. Создан InputPreparer для подготовки входных данных шагов
2. Создан интерфейс ExecutionInterceptor для перехвата выполнения
3. WorkflowExecutor расширен поддержкой interceptors
4. Логика подготовки входных данных перенесена из WorkflowEngine в InputPreparer
5. WorkflowEngine теперь использует WorkflowExecutor для выполнения шагов

**Результат**: Логика выполнения шагов централизована в WorkflowExecutor с поддержкой расширений через interceptors.

### Цель
Перенести логику выполнения шагов из WorkflowEngine в специализированный компонент.

### Новые классы

#### 4.1. Создать `InputPreparer.java`
```java
package ai.driftkit.workflow.engine.core;

public class InputPreparer {
    public Object prepareStepInput(WorkflowInstance instance, StepNode step) {
        // Логика из WorkflowEngine.prepareStepInput (строки 330-463)
    }
}
```

#### 4.2. Создать интерфейс `ExecutionInterceptor.java`
```java
package ai.driftkit.workflow.engine.core;

public interface ExecutionInterceptor {
    void beforeStep(WorkflowInstance instance, StepNode step, Object input);
    void afterStep(WorkflowInstance instance, StepNode step, StepResult<?> result);
    void onStepError(WorkflowInstance instance, StepNode step, Exception error);
}
```

### Изменения в WorkflowExecutor.java

#### 4.3. Обновить класс
```java
// Текущий WorkflowExecutor почти пустой (50 строк)
// Перенести из WorkflowEngine:
// - executeStep (строки 283-324)
// - prepareStepInput (через InputPreparer)
// - Добавить поддержку interceptors

@RequiredArgsConstructor
public class WorkflowExecutor {
    private final WorkflowEngineConfig config;
    private final ProgressTracker progressTracker;
    private final InputPreparer inputPreparer;
    private final List<ExecutionInterceptor> interceptors = new ArrayList<>();
    
    public StepResult<?> executeStep(...) {
        // Полная реализация с interceptors
    }
}
```

## Фаза 5: Создание WorkflowOrchestrator ✅

**Статус**: Завершено

**Что сделано**:
1. Создан WorkflowOrchestrator, который координирует выполнение workflows
2. Перенесена основная логика выполнения из WorkflowEngine.executeWorkflow
3. Перенесен метод processStepResult из WorkflowEngine
4. Удалены дублированные методы из WorkflowEngine
5. Добавлены вспомогательные методы getFinalResult и createErrorFromInfo
6. WorkflowEngine теперь делегирует выполнение orchestrator и обрабатывает только уведомления

**Результат**: Логика выполнения централизована в WorkflowOrchestrator, устранено дублирование кода между компонентами.

### Цель
Создать центральный координатор, объединяющий все компоненты.

### Новый класс

#### 5.1. Создать `WorkflowOrchestrator.java`
```java
package ai.driftkit.workflow.engine.core;

@Slf4j
@RequiredArgsConstructor
public class WorkflowOrchestrator {
    private final WorkflowStateManager stateManager;
    private final WorkflowExecutor executor;
    private final StepRouter router;
    private final AsyncStepHandler asyncHandler;
    private final ProgressTracker progressTracker;
    
    public <R> void orchestrateExecution(
        WorkflowInstance instance,
        WorkflowGraph<?, R> graph,
        WorkflowEngine.WorkflowExecution<R> execution
    ) {
        // Основная логика из WorkflowEngine.executeWorkflow (строки 224-278)
    }
    
    public <R> void processStepResult(
        WorkflowInstance instance,
        WorkflowGraph<?, R> graph,
        StepNode currentStep,
        StepResult<?> result,
        WorkflowEngine.WorkflowExecution<R> execution
    ) {
        // Логика из WorkflowEngine.processStepResult (строки 468-550)
    }
}
```

### Изменения в WorkflowEngine.java

#### 5.2. Рефакторинг WorkflowEngine
```java
// Добавить поле:
private final WorkflowOrchestrator orchestrator;

// Метод executeWorkflow станет:
private <R> void executeWorkflow(WorkflowInstance instance,
                                WorkflowGraph<?, R> graph,
                                WorkflowExecution<R> execution) {
    orchestrator.orchestrateExecution(instance, graph, execution);
}
```

## Фаза 6: Разделение WorkflowAnalyzer ✅

**Статус**: Завершено

**Что сделано**:
1. Создан TypeAnalyzer, который делегирует проверку типов к существующим TypeMatcher и MethodAnalyzer
2. Создан WorkflowValidator для валидации графа и методов, использующий MethodAnalyzer.validateStepMethod
3. Вынесен StepInfo из внутреннего класса WorkflowAnalyzer в отдельный класс в пакете analyzer
4. Устранено дублирование StepInfo - теперь используется единый класс
5. WorkflowAnalyzer обновлен для использования новых компонентов

**Результат**: Улучшена модульность за счет разделения ответственности, устранено дублирование кода, переиспользуется существующая логика валидации.

### Цель
Разбить монолитный WorkflowAnalyzer на специализированные компоненты.

### Новые классы

#### 6.1. Создать `WorkflowValidator.java`
```java
package ai.driftkit.workflow.engine.analyzer;

public class WorkflowValidator {
    public void validateGraph(Map<String, StepNode> nodes, 
                            Map<String, List<Edge>> edges,
                            String initialStepId) {
        // Логика из WorkflowAnalyzer.validateGraph (строки 750-771)
    }
    
    public void validateStepMethod(Method method) {
        // Логика из WorkflowAnalyzer.validateStepMethod (строки 265-333)
    }
}
```

#### 6.2. Создать `TypeAnalyzer.java`
```java
package ai.driftkit.workflow.engine.analyzer;

public class TypeAnalyzer {
    public boolean isTypeCompatible(Class<?> sourceType, Class<?> targetType) {
        // Логика из WorkflowAnalyzer.isTypeCompatible (строки 796-816)
    }
    
    public Class<?> extractStepResultType(Type type) {
        // Логика из WorkflowAnalyzer.extractStepResultType (строки 273-287)
    }
}
```

### Изменения в существующих классах

#### 6.3. Обновить AnnotationScanner.java
```java
// Перенести из WorkflowAnalyzer:
// - discoverSteps (строки 133-259)
// - findInitialStep (строки 429-449)
```

#### 6.4. Обновить GraphBuilder.java
```java
// Перенести из WorkflowAnalyzer:
// - buildNodes (строки 454-488)
// - buildEdges (строки 514-567)
// - addSequentialEdges (строки 572-594)
// - addBranchEdges (строки 599-628)
```

#### 6.5. Рефакторинг WorkflowAnalyzer.java
```java
// WorkflowAnalyzer становится фасадом:
@UtilityClass
public class WorkflowAnalyzer {
    private final AnnotationScanner scanner = new AnnotationScanner();
    private final GraphBuilder graphBuilder = new GraphBuilder();
    private final WorkflowValidator validator = new WorkflowValidator();
    private final TypeAnalyzer typeAnalyzer = new TypeAnalyzer();
    
    public static <T, R> WorkflowGraph<T, R> analyze(Object workflowInstance) {
        // Координация работы компонентов
    }
}
```

## Фаза 7: Улучшение AsyncTaskManager

### Цель
Доработать управление асинхронными задачами.

### Изменения в AsyncTaskManager.java

#### 7.1. Добавить новые методы
```java
public class AsyncTaskManager {
    // Добавить:
    private final Map<String, CancellationToken> cancellationTokens = new ConcurrentHashMap<>();
    
    public CancellationToken createCancellationToken(String taskId) {
        CancellationToken token = new CancellationToken();
        cancellationTokens.put(taskId, token);
        return token;
    }
    
    public boolean cancelAsyncTasks(String instanceId) {
        // Улучшенная логика отмены с использованием tokens
    }
}
```

#### 7.2. Создать `CancellationToken.java`
```java
package ai.driftkit.workflow.engine.core;

public class CancellationToken {
    private volatile boolean cancelled = false;
    
    public void cancel() { 
        this.cancelled = true; 
    }
    
    public boolean isCancelled() { 
        return cancelled; 
    }
}
```

### Изменения в AsyncStepHandler.java

#### 7.3. Поддержка отмены
```java
// В методе handleAsyncResult добавить:
if (progressReporter instanceof AsyncProgressReporter) {
    ((AsyncProgressReporter) progressReporter).setCancellationToken(cancellationToken);
}
```

## Фаза 8: Создание WorkflowEventBus

### Цель
Централизованная обработка событий вместо прямых вызовов listeners.

### Новые классы

#### 8.1. Создать `WorkflowEventBus.java`

```java
package ai.driftkit.workflow.engine.core;

import ai.driftkit.workflow.engine.domain.WorkflowEvent;

@Slf4j
public class WorkflowEventBus {
  private final ExecutorService eventExecutor;
  private final Map<String, WorkflowExecutionListener> listeners = new ConcurrentHashMap<>();

  public void publishEvent(WorkflowEvent event) {
    eventExecutor.submit(() -> notifyListeners(event));
  }

  public void addListener(String id, WorkflowExecutionListener listener) {
    listeners.put(id, listener);
  }

  private void notifyListeners(WorkflowEvent event) {
    // Асинхронная обработка
  }
}
```

#### 8.2. Создать `WorkflowEvent.java` (события)
```java
package ai.driftkit.workflow.engine.core;

public sealed interface WorkflowEvent {
    record WorkflowStarted(WorkflowInstance instance) implements WorkflowEvent {}
    record WorkflowCompleted(WorkflowInstance instance, Object result) implements WorkflowEvent {}
    record WorkflowFailed(WorkflowInstance instance, Throwable error) implements WorkflowEvent {}
    record StepStarted(WorkflowInstance instance, String stepId) implements WorkflowEvent {}
    record StepCompleted(WorkflowInstance instance, String stepId, StepResult<?> result) implements WorkflowEvent {}
}
```

### Изменения в WorkflowEngine.java

#### 8.3. Использовать EventBus
```java
// Добавить поле:
private final WorkflowEventBus eventBus;

// Заменить:
// БЫЛО: notifyListeners(l -> l.onWorkflowStarted(instance));
// СТАНЕТ: eventBus.publishEvent(new WorkflowEvent.WorkflowStarted(instance));
```

## Итоговая структура компонентов

### Основные классы после рефакторинга:

1. **WorkflowEngine** - фасад, публичный API
2. **WorkflowOrchestrator** - координатор выполнения
3. **WorkflowExecutor** - выполнение шагов
4. **StepRouter** - маршрутизация между шагами
5. **AsyncTaskManager** - управление async операциями
6. **WorkflowStateManager** - управление состоянием
7. **WorkflowEventBus** - обработка событий
8. **WorkflowAnalyzer** - фасад для анализа
9. **InputPreparer** - подготовка входных данных
10. **WorkflowContext** - изменяемый контекст с ConcurrentHashMap

### Новая структура пакетов:
```
ai.driftkit.workflow.engine.core/
  ├── WorkflowEngine.java (уменьшится с 1234 до ~300 строк)
  ├── WorkflowOrchestrator.java (новый, ~400 строк)
  ├── WorkflowExecutor.java (расширится до ~200 строк)
  ├── StepRouter.java (новый интерфейс)
  ├── DefaultStepRouter.java (новый, ~200 строк)
  ├── InputPreparer.java (новый, ~150 строк)
  ├── WorkflowEventBus.java (новый, ~150 строк)
  ├── WorkflowContext.java (изменяемый, ~350 строк)
  └── WorkflowAnalyzer.java (фасад, ~200 строк)

ai.driftkit.workflow.engine.analyzer/
  ├── WorkflowValidator.java (новый, ~150 строк)
  ├── TypeAnalyzer.java (новый, ~100 строк)
  ├── AnnotationScanner.java (расширится)
  └── GraphBuilder.java (расширится)
```

## Важные моменты реализации

### Разделение step outputs и custom data
- **Step outputs** (`stepOutputs`) - только для внутреннего использования движком
  - Записывается через `WorkflowInstance.updateContext()` 
  - Читается через `getStepResult()` и `getStepResultOrDefault()`
  - Содержит результаты выполнения шагов
  
- **Custom data** (`customData`) - для пользовательских данных
  - Записывается через `setContextValue()`
  - Читается через `getContextValue()`, `getString()`, `getInt()` и другие helper методы
  - Позволяет workflows обмениваться произвольными данными

### Удаление deprecated методов
- Полностью удаляем `withStepOutput()` и `withStepOutputs()`
- Удаляем запутывающий метод `updateContext()` из WorkflowContext
- Все изменения контекста теперь явные и понятные

### Helper методы для удобства
- `getString()`, `getInt()`, `getLong()` и т.д. - для базовых типов
- `getList()` и `getMap()` - для коллекций
- Все helper методы работают только с `customData`
- Версии с `OrDefault` для безопасного получения значений

## Преимущества рефакторинга

1. **Разделение ответственности** - каждый компонент отвечает за одну область
2. **Поддержка параллельности** - изменяемый контекст с ConcurrentHashMap
3. **Расширяемость** - легко добавлять новые стратегии маршрутизации, interceptors
4. **Тестируемость** - компоненты можно тестировать изолированно
5. **Поддерживаемость** - меньшие классы проще понимать и изменять
6. **Чистый API** - нет deprecated методов, четкое разделение системных и пользовательских данных
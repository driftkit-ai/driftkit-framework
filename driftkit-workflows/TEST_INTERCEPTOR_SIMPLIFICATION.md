# Анализ и упрощение системы интерцепторов для тестирования Workflow Engine

## ⚠️ ВАЖНОЕ УТОЧНЕНИЕ

После детальной проверки кода выявлено:
1. **contextFactory используется в production коде** - в `WorkflowContext.newRun()` для создания контекстов
2. **Parallel шаги НЕ используют InternalStepListener** - только branch шаги
3. **Вариант 1 требует корректировки** для полноценной работы

## Текущая проблема

Система интерцепторов для тестирования branch и parallel workflows имеет следующие проблемы:

1. **Сложная инициализация** - размазана по нескольким классам
2. **Глобальное состояние** - статический `contextFactory` в `WorkflowEngine`
3. **Неявные зависимости** - сложная цепочка взаимодействий между компонентами
4. **Избыточная сложность** - для простой задачи перехвата внутренних шагов

## Архитектура текущего решения

### Компоненты и их взаимодействия

```
WorkflowEngine (core)
├── static contextFactory: WorkflowContextFactory
├── Использует contextFactory при создании WorkflowContext
└── Позволяет внешнюю установку через setContextFactory()

WorkflowContext (core)
├── internalStepListener: InternalStepListener
├── Вызывает listener при выполнении внутренних шагов в branch/parallel
└── Позволяет установку через setInternalStepListener()

TestContextFactory (test)
├── Implements WorkflowContextFactory
├── ThreadLocal<WorkflowInstance> для хранения текущего instance
├── Создает TestWorkflowContext вместо обычного WorkflowContext
└── Требует явной установки instance перед выполнением

TestWorkflowContext (test)
├── Extends WorkflowContext
├── Создает и устанавливает TestInternalStepListener
└── Передает ExecutionTracker и MockRegistry в listener

TestInternalStepListener (test)
├── Implements InternalStepListener
├── Отслеживает выполнение внутренних шагов
└── Поддерживает моки для внутренних шагов

WorkflowTestInterceptor (test)
├── Implements ExecutionInterceptor
├── Управляет TestContextFactory
├── Устанавливает текущий WorkflowInstance в factory
└── Дублирует установку InternalStepListener для надежности

WorkflowTestBase (test)
├── Создает все компоненты
├── Устанавливает статический contextFactory
└── Связывает все зависимости
```

### Места инициализации

1. **WorkflowTestBase.setupBase()** - строки 48-86
   - Создает `TestContextFactory`
   - Устанавливает статический `WorkflowEngine.setContextFactory()`
   - Связывает factory с interceptor

2. **WorkflowTestInterceptor.beforeWorkflowStart()** - строки 69-87
   - Устанавливает instance в `TestContextFactory`
   - Дублирует установку `InternalStepListener` если его нет

3. **WorkflowTestInterceptor.beforeStep()** - строки 110-126
   - Еще раз проверяет и устанавливает `InternalStepListener`

4. **TestContextFactory.create()** - строки 37-54
   - Создает `TestWorkflowContext` если есть instance

5. **TestWorkflowContext constructor** - строки 19-36
   - Создает и устанавливает `TestInternalStepListener`

## Проблемы текущего подхода

### 1. Глобальное состояние
- Статический `contextFactory` в `WorkflowEngine` - потенциальные проблемы с параллельными тестами
- ThreadLocal в `TestContextFactory` - может течь память при неправильной очистке

### 2. Сложная цепочка инициализации
- Инициализация размазана по 5+ местам
- Дублирование логики установки `InternalStepListener`
- Неочевидная последовательность вызовов

### 3. Избыточная архитектура
- Слишком много промежуточных классов для простой задачи
- TestWorkflowContext существует только для установки listener

### 4. Хрупкость
- Зависит от порядка вызовов
- Требует явного управления состоянием (setCurrentInstance/clearCurrentInstance)

## Предложения по упрощению

### Вариант 1: Прямая интеграция через ExecutionInterceptor (Требует доработки)

**Идея**: Убрать contextFactory и TestWorkflowContext, установка InternalStepListener напрямую через interceptor.

**⚠️ ПРОБЛЕМА**: contextFactory используется в production коде для создания WorkflowContext через статические методы `WorkflowContext.newRun()`. Полное удаление невозможно без изменения production кода.

```java
// Упрощенный WorkflowTestInterceptor
public class WorkflowTestInterceptor implements ExecutionInterceptor {
    private final MockRegistry mockRegistry;
    private final ExecutionTracker executionTracker;
    
    @Override
    public void beforeWorkflowStart(WorkflowInstance instance, Object input) {
        // Создаем listener один раз при старте workflow
        InternalStepListener listener = new TestInternalStepListener(
            instance, executionTracker, mockRegistry);
        
        // Устанавливаем напрямую в контекст
        instance.getContext().setInternalStepListener(listener);
        
        executionTracker.recordWorkflowStart(instance, input);
    }
}

// WorkflowTestBase упрощается
@BeforeEach
void setupBase() {
    this.testContext = new WorkflowTestContext();
    this.testInterceptor = new WorkflowTestInterceptor(
        testContext.getMockRegistry(), 
        testContext.getExecutionTracker()
    );
    
    // Больше не нужно устанавливать contextFactory!
    this.engine = new WorkflowEngine();
    this.engine.addInterceptor(testInterceptor);
}
```

**Преимущества**:
- Убираем статический contextFactory
- Убираем TestContextFactory и TestWorkflowContext
- Одно место инициализации
- Простая и понятная логика

### Вариант 2: Dependency Injection через конструктор WorkflowEngine

**Идея**: Передавать contextFactory как параметр конструктора вместо статического поля.

```java
// Изменение в WorkflowEngine
public class WorkflowEngine {
    private final WorkflowContextFactory contextFactory;
    
    public WorkflowEngine(WorkflowEngineConfig config) {
        this(config, null);
    }
    
    public WorkflowEngine(WorkflowEngineConfig config, WorkflowContextFactory contextFactory) {
        this.contextFactory = contextFactory != null ? contextFactory : 
            (runId, triggerData, instanceId) -> 
                new WorkflowContext(runId, triggerData, instanceId);
        // ... остальная инициализация
    }
}

// Использование в тестах
@BeforeEach
void setupBase() {
    TestContextFactory contextFactory = new TestContextFactory(
        testContext.getExecutionTracker(),
        testContext.getMockRegistry()
    );
    
    WorkflowEngineConfig config = WorkflowEngineConfig.defaultConfig();
    this.engine = new WorkflowEngine(config, contextFactory);
    this.engine.addInterceptor(testInterceptor);
}
```

**Преимущества**:
- Нет глобального состояния
- Явные зависимости
- Thread-safe

**Недостатки**:
- Все еще нужны TestContextFactory и TestWorkflowContext
- Требует изменения API WorkflowEngine

### Вариант 3: Listener Registry в WorkflowContext

**Идея**: Вместо одного InternalStepListener сделать список listeners.

```java
// В WorkflowContext
public class WorkflowContext {
    private final List<InternalStepListener> internalStepListeners = new CopyOnWriteArrayList<>();
    
    public void addInternalStepListener(InternalStepListener listener) {
        internalStepListeners.add(listener);
    }
    
    public void removeInternalStepListener(InternalStepListener listener) {
        internalStepListeners.remove(listener);
    }
    
    // При вызове внутренних шагов
    void notifyInternalStep(String stepId, Object input) {
        for (InternalStepListener listener : internalStepListeners) {
            listener.beforeInternalStep(stepId, input, this);
        }
    }
}
```

**Преимущества**:
- Поддержка множественных listeners
- Гибкость для разных сценариев

**Недостатки**:
- Небольшое усложнение логики в WorkflowContext
- Может быть избыточно для текущих нужд

### Вариант 4: Aspect-Oriented подход

**Идея**: Использовать AOP для перехвата вызовов без изменения core кода.

```java
// Использование Spring AOP или ByteBuddy
@Aspect
@Component
public class WorkflowTestAspect {
    @Around("execution(* ai.driftkit.workflow.engine.builder.WorkflowBuilder$BranchStepDefinition.execute(..))")
    public Object interceptBranchStep(ProceedingJoinPoint joinPoint) throws Throwable {
        // Логика перехвата
        Object[] args = joinPoint.getArgs();
        // ... tracking and mocking logic
        return joinPoint.proceed(args);
    }
}
```

**Преимущества**:
- Не требует изменений в core
- Полная изоляция тестовой логики

**Недостатки**:
- Требует AOP framework
- Сложнее в отладке
- Может быть хрупким при рефакторинге

## Рекомендуемое решение

**Выбираем модифицированный Вариант 1** с учетом реальности кода:

### Уточненный план упрощения

1. **Сохранить contextFactory, но упростить его использование**:
   - Сделать contextFactory полем экземпляра WorkflowEngine вместо статического
   - Или оставить статическим, но управлять через более явный API

2. **Упростить цепочку создания контекста**:
   - Убрать TestContextFactory и TestWorkflowContext
   - Устанавливать InternalStepListener напрямую в interceptor

3. **Консолидировать логику в одном месте**:
   - Вся логика установки listener в WorkflowTestInterceptor
   - Убрать дублирование в beforeWorkflowStart и beforeStep

### План реализации

1. **Упростить WorkflowTestInterceptor**:
   - Убрать управление TestContextFactory
   - Установка InternalStepListener напрямую в beforeWorkflowStart

2. **Удалить лишние классы**:
   - TestContextFactory
   - TestWorkflowContext

3. **Упростить WorkflowTestBase**:
   - Убрать работу с contextFactory
   - Упростить инициализацию

4. **Убрать статический contextFactory из WorkflowEngine**:
   - Сделать его полем экземпляра с дефолтным значением
   - Или вообще убрать если он используется только для тестов

### Пример реалистичного упрощенного кода

```java
// WorkflowTestInterceptor - консолидированная логика
public class WorkflowTestInterceptor implements ExecutionInterceptor {
    private final MockRegistry mockRegistry;
    private final ExecutionTracker executionTracker;
    private final Map<String, InternalStepListener> listeners = new ConcurrentHashMap<>();
    
    @Override
    public void beforeWorkflowStart(WorkflowInstance instance, Object input) {
        // Создаем listener один раз при старте workflow
        TestInternalStepListener listener = new TestInternalStepListener(
            instance, executionTracker, mockRegistry);
        
        // Сохраняем для cleanup
        listeners.put(instance.getInstanceId(), listener);
        
        // Устанавливаем в контекст (контекст уже создан через contextFactory)
        WorkflowContext context = instance.getContext();
        if (context != null) {
            context.setInternalStepListener(listener);
        }
        
        executionTracker.recordWorkflowStart(instance, input);
    }
    
    @Override
    public void beforeStep(WorkflowInstance instance, StepNode step, Object input) {
        // Убираем дублирование - listener уже установлен в beforeWorkflowStart
        StepContext stepContext = new StepContext(instance, step, input);
        executionTracker.recordStepStart(stepContext);
    }
    
    @Override
    public void afterWorkflowComplete(WorkflowInstance instance, Object result) {
        listeners.remove(instance.getInstanceId());
        executionTracker.recordWorkflowComplete(instance, result);
    }
    
    @Override
    public void onWorkflowError(WorkflowInstance instance, Throwable error) {
        listeners.remove(instance.getInstanceId());
        executionTracker.recordWorkflowError(instance, error);
    }
}

// WorkflowTestBase - минимальные изменения 
@BeforeEach
void setupBase() {
    this.testContext = new WorkflowTestContext();
    this.testInterceptor = new WorkflowTestInterceptor(
        testContext.getMockRegistry(), 
        testContext.getExecutionTracker()
    );
    
    // Статический contextFactory остается, но не используется для тестовой логики
    // Вся тестовая логика теперь в interceptor
    this.engine = createEngine();
    this.engine.addInterceptor(testInterceptor);
    
    this.orchestrator = new WorkflowTestOrchestrator(
        testContext.getMockRegistry(),
        testContext.getExecutionTracker(),
        testInterceptor,
        engine
    );
}

@AfterEach
void tearDownBase() {
    // Упрощенная очистка - не нужно сбрасывать contextFactory
    if (testContext != null) {
        testContext.clear();
    }
    if (engine != null) {
        engine.shutdown();
    }
}
```

## Альтернативный минималистичный подход

Если branch и parallel используются редко в тестах, можно рассмотреть еще более простой подход:

```java
// Специальный test helper для branch/parallel тестов
public class BranchTestHelper {
    public static void enableBranchTracking(WorkflowInstance instance, 
                                           ExecutionTracker tracker,
                                           MockRegistry mocks) {
        instance.getContext().setInternalStepListener(
            new TestInternalStepListener(instance, tracker, mocks));
    }
}

// Использование в тестах
@Test
void testBranchWorkflow() {
    // Только когда нужно тестировать branch/parallel
    WorkflowInstance instance = engine.getWorkflowInstance(runId).get();
    BranchTestHelper.enableBranchTracking(instance, 
        testContext.getExecutionTracker(), 
        testContext.getMockRegistry());
    
    // ... выполнение теста
}
```

## Выводы и окончательные рекомендации

После детальной проверки выявлено:

### Что можно упростить сейчас:
1. **Удалить TestContextFactory и TestWorkflowContext** - они избыточны
2. **Консолидировать логику в WorkflowTestInterceptor** - убрать дублирование
3. **Упростить WorkflowTestBase** - минимизировать инициализацию

### Что нужно сохранить:
1. **contextFactory в WorkflowEngine** - используется в production для `WorkflowContext.newRun()`
2. **InternalStepListener интерфейс** - нужен для branch (не для parallel!)

### Преимущества упрощения:
- **Удаление 2 классов** (TestContextFactory, TestWorkflowContext)
- **Единое место управления** - вся логика в interceptor
- **Thread-safe** - ConcurrentHashMap для listeners
- **Простота отладки** - меньше слоев абстракции

### Важные замечания:
1. **Parallel шаги НЕ используют InternalStepListener** - только branch
2. **contextFactory нельзя полностью убрать** без рефакторинга production кода
3. **Упрощение безопасно** - не ломает существующую функциональность

### Итоговая архитектура:
```
WorkflowEngine (contextFactory остается для production)
    ↓
WorkflowTestInterceptor (устанавливает InternalStepListener)
    ↓
TestInternalStepListener (tracking и mocking для branch)
```

Это решение обеспечивает баланс между упрощением и совместимостью с существующим кодом.
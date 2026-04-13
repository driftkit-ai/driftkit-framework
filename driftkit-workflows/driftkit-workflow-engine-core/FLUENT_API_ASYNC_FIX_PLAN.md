# План исправлений FluentAPI для поддержки Async в DriftKit Workflow Engine

## Фазы реализации

### Фаза 1: Добавить поддержку async в WorkflowBuilder ✅ ЗАВЕРШЕНО
- ✅ Добавить метод `withAsyncHandler()` в WorkflowBuilder
- ✅ Добавить поля для хранения async метаданных
- ✅ Обновить метод `build()` для включения async информации

**Изменение подхода после анализа**: 
- НЕ нужен workflowInstance для FluentAPI
- Async обработчики должны регистрироваться как специальные шаги через method references
- Нужен метод для регистрации async обработчиков с указанием паттерна taskId

**Что реализовано:**
1. Добавлен метод `withAsyncHandler(String pattern, TriFunction handler)` для регистрации async обработчиков
2. Добавлен метод `withAsyncHandler(Object asyncHandlerObject)` для регистрации объектов с @AsyncStep методами
3. Создан класс `FluentApiAsyncStepMetadata` для хранения async обработчиков без workflowInstance
4. Модифицирован `AsyncStepHandler` для поддержки FluentAPI обработчиков
5. Добавлен класс `FluentApiAsyncStepInfo` для обработки FluentAPI async вызовов

**Проверено:** Компиляция успешна ✅

### Фаза 2: Исправить тест testAsyncWorkflowWithSuspension ✅ ЗАВЕРШЕНО
- ✅ Убрать двойное создание графа
- ✅ Использовать новый метод `withAsyncHandler()`
- ✅ Проверить корректность работы

**Что реализовано:**
1. Убран двойной граф - теперь используется только один вызов WorkflowBuilder с `.withAsyncHandler(asyncSteps)`
2. Добавлена поддержка wildcard паттернов в AsyncStepHandler через метод `findByPattern()`
3. Async обработчики успешно регистрируются и вызываются
4. Workflow корректно переходит в состояние SUSPENDED

**Обнаруженная проблема:** Resume логика работает некорректно - после resume пытается выполнить не тот шаг.
Это отдельная проблема, не связанная с async функциональностью.

**Проверено:** Async функциональность работает ✅

### Фаза 3: Решить проблему с Object типами ✅ ЗАВЕРШЕНО
- ✅ Обновить StepDefinition для поддержки явных типов
- ✅ Исправить методы с Object параметрами

**Что реализовано:**
1. Метод `formatTaskResponse(Object)` разделен на два конкретных метода:
   - `formatSimpleTaskResponse(SimpleResult)` 
   - `formatConfirmationResponse(ConfirmationResult)`
2. Обновлены вызовы в workflow для использования конкретных методов
3. Теперь фреймворк может корректно определять типы и связи в графе

**Проверено:** Тест успешно проходит ✅

### Фаза 4: Добавить тесты
- Создать дополнительные тесты для async функциональности
- Проверить edge cases

### Фаза 5: Финальная проверка
- Запустить все тесты
- Обновить документацию

## Обзор проблем

### 1. Некорректная работа с async в FluentApiChatWorkflowTest

**Проблема**: В тесте `testAsyncWorkflowWithSuspension` используется двойное создание графа:
- Сначала создается граф через WorkflowBuilder (строки 256-293)
- Затем создается новый граф `graphWithHandler` с дублированием всей информации (строки 296-311)

**Текущий код (неправильный)**:
```java
// Строки 256-293: создание графа
WorkflowGraph<UserMessage, AssistantResponse> workflow = WorkflowBuilder
    .define("async-chat", UserMessage.class, AssistantResponse.class)
    .then(chatSteps::extractIntent)
    .branch(...)
    .build();

// Строки 296-311: создание дублирующего графа
WorkflowGraph<UserMessage, AssistantResponse> graphWithHandler = WorkflowGraph.<UserMessage, AssistantResponse>builder()
    .id(workflow.id())
    .version(workflow.version())
    // ... копирование всех полей ...
    .workflowInstance(asyncSteps)  // Добавление async handler
    .asyncStepMetadata(Map.of("*", new WorkflowAnalyzer.AsyncStepMetadata(...)))
    .build();

engine.register(graphWithHandler); // Регистрируется второй граф!
```

### 2. Нарушение принципов фреймворка

**Принципы WorkflowEngine и WorkflowInstance**:
- В аннотационном подходе async методы регистрируются через `@AsyncStep` аннотацию
- WorkflowAnalyzer автоматически находит все `@AsyncStep` методы в классе workflow
- Async обработчики связываются через `taskId` в `StepResult.Async`

**Правильный подход (из AsyncWorkflowExample)**:
```java
// Обычный шаг возвращает StepResult.Async с taskId
public StepResult<ExtractedText> validateAndExtract(DocumentRequest request) {
    return new StepResult.Async<ExtractedText>(
        "extractTextAsync",  // taskId
        30000L,
        taskArgs,
        immediateData
    );
}

// Async обработчик с matching taskId
@AsyncStep(value = "extractTextAsync")
public StepResult<ExtractedText> extractTextAsync(Map<String, Object> taskArgs, 
                                                  WorkflowContext context,
                                                  AsyncProgressReporter progress) {
    // async implementation
}
```

### 3. Проблема с типами Object

**Проблема**: Метод `formatTaskResponse(Object result)` принимает Object, что делает невозможным автоматическое определение связей в графе по типам.

**Влияние**: Фреймворк не может построить корректный граф, так как не знает какие типы могут прийти в Object.

## План исправлений

### 1. Добавить поддержку async в WorkflowBuilder

#### Вариант А: Регистрация async обработчиков через builder
```java
WorkflowGraph<UserMessage, AssistantResponse> workflow = WorkflowBuilder
    .define("async-chat", UserMessage.class, AssistantResponse.class)
    .withAsyncHandler(asyncSteps)  // Регистрация объекта с async методами
    .then(chatSteps::extractIntent)
    .branch(
        // условие
        ctx -> ...,
        
        // Complex task branch
        complexFlow -> complexFlow
            .then(asyncSteps::initiateAsyncTask)  // Возвращает StepResult.Async
            // автоматически будет искать @AsyncStep метод по taskId
            .then("confirm-result", (TaskResult taskResult, WorkflowContext ctx) -> {
                return StepResult.suspend(...);
            })
            .then(chatSteps::processConfirmation)
            .then(chatSteps::formatTaskResponse),
        
        // Simple task branch  
        simpleFlow -> simpleFlow
            .then(chatSteps::handleSimpleTask)
            .then(chatSteps::formatTaskResponse)
    )
    .build();
```

#### Вариант Б: Явная регистрация async методов
```java
WorkflowGraph<UserMessage, AssistantResponse> workflow = WorkflowBuilder
    .define("async-chat", UserMessage.class, AssistantResponse.class)
    .registerAsyncStep("task-id-pattern", asyncSteps::waitForCompletion)
    .then(chatSteps::extractIntent)
    // ... остальной граф
    .build();
```

### 2. Изменения в WorkflowBuilder

#### Добавить в WorkflowBuilder:
```java
public class WorkflowBuilder<T, R> {
    // Новые поля
    private Object workflowInstance;
    private final Map<String, AsyncStepMetadata> asyncStepMetadata = new HashMap<>();
    
    // Новый метод для регистрации объекта с async обработчиками
    public WorkflowBuilder<T, R> withAsyncHandler(Object asyncHandler) {
        this.workflowInstance = asyncHandler;
        // Анализировать @AsyncStep методы в asyncHandler
        Map<String, AsyncStepMetadata> metadata = WorkflowAnalyzer.findAsyncSteps(asyncHandler);
        this.asyncStepMetadata.putAll(metadata);
        return this;
    }
    
    // Альтернативный метод для явной регистрации
    public WorkflowBuilder<T, R> registerAsyncStep(String taskIdPattern, 
                                                   TriFunction<Map<String, Object>, WorkflowContext, AsyncProgressReporter, StepResult<?>> handler) {
        // Создать metadata для handler
        // Добавить в asyncStepMetadata
        return this;
    }
    
    // Изменить метод build()
    public WorkflowGraph<T, R> build() {
        // ... существующая логика построения графа ...
        
        return WorkflowGraph.<T, R>builder()
            .id(id)
            .version(version)
            .inputType(inputType)
            .outputType(outputType)
            .nodes(nodes)
            .edges(edges)
            .initialStepId(initialStepId)
            .workflowInstance(workflowInstance)  // Добавить если есть
            .asyncStepMetadata(asyncStepMetadata) // Добавить если есть
            .build();
    }
}
```

### 3. Исправление проблемы с Object типами

#### Вариант А: Использовать дженерики или базовый тип
```java
// Вместо Object использовать общий интерфейс
public interface TaskResult {
    String getMessage();
    boolean isSuccess();
}

public class SimpleResult implements TaskResult { ... }
public class ConfirmationResult implements TaskResult { ... }

// Тогда метод может принимать базовый тип
public StepResult<AssistantResponse> formatTaskResponse(TaskResult result) {
    // implementation
}
```

#### Вариант Б: Разделить на отдельные методы
```java
// Вместо одного метода с Object, создать специфичные методы
public StepResult<AssistantResponse> formatSimpleTaskResponse(SimpleResult result) {
    // implementation
}

public StepResult<AssistantResponse> formatConfirmationResponse(ConfirmationResult result) {
    // implementation
}
```

#### Вариант В: Использовать StepDefinition с явными типами
```java
// При использовании lambda с Object, требовать явное указание типов
.then(StepDefinition.of("formatResponse", chatSteps::formatTaskResponse)
    .withInputType(SimpleResult.class)  // Явно указать входной тип
    .withOutputType(AssistantResponse.class))
```

### 4. Обновление тестов

#### Исправленный testAsyncWorkflowWithSuspension:
```java
@Test
@DisplayName("Workflow with async steps and suspension")
public void testAsyncWorkflowWithSuspension() throws Exception {
    // Build workflow with async handler registration
    WorkflowGraph<UserMessage, AssistantResponse> workflow = WorkflowBuilder
        .define("async-chat", UserMessage.class, AssistantResponse.class)
        .withAsyncHandler(asyncSteps)  // Регистрация async обработчиков
        .then(chatSteps::extractIntent)
        .branch(
            ctx -> {
                Optional<IntentAnalysis> analysis = ctx.step("extractIntent").output(IntentAnalysis.class);
                return analysis.map(a -> a.getIntent() == Intent.COMPLEX_TASK).orElse(false);
            },
            
            // Complex task - needs async processing
            complexFlow -> complexFlow
                .then(asyncSteps::initiateAsyncTask)
                .then("confirm-result", (TaskResult taskResult, WorkflowContext ctx) -> {
                    return StepResult.suspend(
                        "Task completed: " + taskResult.getSummary() + 
                        ". Do you want to proceed?",
                        UserConfirmation.class
                    );
                })
                .then(chatSteps::processConfirmation)
                .then(StepDefinition.of("formatConfirmation", chatSteps::formatTaskResponse)
                    .withInputType(ConfirmationResult.class)),
            
            // Simple task
            simpleFlow -> simpleFlow
                .then(chatSteps::handleSimpleTask)
                .then(StepDefinition.of("formatSimple", chatSteps::formatTaskResponse)
                    .withInputType(SimpleResult.class))
        )
        .build();
    
    // Регистрация только одного графа!
    engine.register(workflow);
    
    // ... остальная часть теста ...
}
```

### 5. Дополнительные улучшения

1. **Валидация async методов**: WorkflowBuilder должен проверять, что async методы соответствуют сигнатуре
2. **Автоматическое определение типов**: Попытаться извлечь типы из method references где возможно
3. **Улучшенные сообщения об ошибках**: Четкие сообщения когда не удается определить связи в графе
4. **Документация**: Добавить примеры использования async в fluent API

## Итоги выполненной работы

### ✅ Завершено:
1. **Фаза 1**: Добавлена полная поддержка async в WorkflowBuilder БЕЗ workflowInstance
   - Метод `withAsyncHandler(Object)` для объектов с @AsyncStep методами
   - Метод `withAsyncHandler(String, TriFunction)` для прямой регистрации обработчиков
   - Создан `FluentApiAsyncStepMetadata` для хранения обработчиков без instance
   - Модифицирован `AsyncStepHandler` для поддержки FluentAPI

2. **Фаза 2**: Исправлен тест testAsyncWorkflowWithSuspension
   - Убрано двойное создание графа
   - Добавлена поддержка wildcard паттернов (* и prefix-*)
   - Async обработчики успешно вызываются

### ✅ Все задачи завершены:
1. **Проблема с Object типами** - ✅ Решена путем разделения `formatTaskResponse(Object)` на специфичные методы
2. **Проблема с resume** - ✅ Исправлена:
   - При resume теперь правильно определяется следующий шаг
   - Исправлена проблема с CompletableFuture (нужно использовать результат от resume(), а не от execute())
3. **Дополнительные тесты** - ✅ Добавлены FluentApiAsyncTest и FluentApiResumeTest

## Риски и соображения

1. **Обратная совместимость**: Изменения не должны сломать существующие тесты
2. **Консистентность**: Fluent API должен работать так же как аннотационный подход
3. **Производительность**: Анализ async методов не должен замедлять построение графа
4. **Безопасность типов**: Максимально сохранить compile-time проверки типов

## Тестирование

После внесения изменений необходимо:
1. Убедиться что все существующие тесты проходят
2. Исправить testAsyncWorkflowWithSuspension
3. Добавить дополнительные тесты для новой функциональности
4. Проверить что async обработчики корректно вызываются
5. Проверить suspend/resume функциональность
# План полного переноса функционала из driftkit-workflows-spring-boot-starter в новые модули

## Обзор

Проведен анализ driftkit-workflows-spring-boot-starter и новых модулей (driftkit-workflow-engine-core, driftkit-workflow-engine-spring-boot-starter). Выявлены критически важные компоненты, которые отсутствуют в новых модулях.

## Критически важные отсутствующие компоненты

### 1. Система трассировки запросов (КРИТИЧНО)

**Отсутствующие компоненты:**
- `SpringRequestTracingProvider` - провайдер трассировки для Spring
- `ModelRequestTrace` - домен для хранения трейсов
- `ModelRequestTraceRepository` - репозиторий для трейсов
- `ModelRequestService` - сервис для выполнения и трассировки запросов

**Функционал:**
- Автоматическая трассировка всех запросов к моделям (text-to-text, text-to-image, image-to-text)
- Сохранение контекста запроса, переменных, промптов
- Трассировка с информацией о workflow и агентах
- Асинхронное сохранение трейсов в MongoDB
- Интеграция с `RequestTracingRegistry`

### 2. REST API для одиночных запросов к моделям (КРИТИЧНО)

**Отсутствующие компоненты:**
- `LLMRestController` - полный REST API для работы с моделями
- `AIService` - сервис для обработки запросов к моделям
- `TasksService` - управление задачами
- `ModelRequestContext` - контекст запроса к модели

**Основные эндпоинты:**
```
POST /data/v1.0/admin/llm/message - асинхронная отправка сообщения
POST /data/v1.0/admin/llm/message/sync - синхронная отправка сообщения  
POST /data/v1.0/admin/llm/prompt/message - отправка через промпт (асинх)
POST /data/v1.0/admin/llm/prompt/message/sync - отправка через промпт (синх)
GET /data/v1.0/admin/llm/message/{messageId} - получение результата
GET /data/v1.0/admin/llm/chats - список чатов
PUT /data/v1.0/admin/llm/chat - создание чата
POST /data/v1.0/admin/llm/chat - обновление чата
GET /data/v1.0/admin/llm/languages - список языков
POST /data/v1.0/admin/llm/message/{messageId}/rate - оценка сообщения
```

### 3. Система управления задачами и сообщениями

**Отсутствующие компоненты:**
- `MessageTaskEntity` - entity для сообщений
- `MessageTaskRepository` - репозиторий сообщений 
- `ImageModelService` - сервис для генерации изображений
- `ChatService` - сервис управления чатами
- `ChatEntity` - entity чатов
- `ChatRepository` - репозиторий чатов

### 4. Конфигурация и автонастройка Spring Boot

**Отсутствующие компоненты:**
- `WorkflowAutoConfiguration` - автоконфигурация workflow
- `AsyncConfig` - конфигурация асинхронности
- Автоматическое сканирование пакетов
- Настройка MongoDB репозиториев

### 5. Дополнительная функциональность

**Отсутствующие компоненты:**
- `AnalyticsController` - контроллер аналитики
- `AnalyticsService` - сервис аналитики  
- `ChecklistService` - система чек-листов
- `LLMMemoryProvider` - провайдер памяти для LLM
- Система агентов (`AgentWorkflow`, `ModelWorkflow`)

## План миграции

### Этап 1: Система трассировки (ВЫСОКИЙ ПРИОРИТЕТ)

#### 1.1 Создание модуля driftkit-workflow-tracing

```java
// Структура модуля
driftkit-workflow-tracing/
├── src/main/java/ai/driftkit/workflow/tracing/
│   ├── core/
│   │   ├── RequestTracingProvider.java (интерфейс)
│   │   ├── RequestTracingRegistry.java (реестр)
│   │   └── TraceContext.java (контекст)
│   ├── domain/
│   │   ├── ModelRequestTrace.java
│   │   └── WorkflowInfo.java
│   └── repository/
│       └── ModelRequestTraceRepository.java
```

#### 1.2 Интеграция в driftkit-workflow-engine-spring-boot-starter

```java
@Component
@RequiredArgsConstructor
public class WorkflowTracingProvider implements RequestTracingProvider {
    
    private final ModelRequestTraceRepository traceRepository;
    private final Executor traceExecutor;
    
    @Override
    public void traceTextRequest(ModelTextRequest request, ModelTextResponse response, RequestContext context) {
        // Реализация трассировки
    }
    
    @Override
    public void traceImageRequest(ModelImageRequest request, ModelImageResponse response, RequestContext context) {
        // Реализация трассировки
    }
}
```

#### 1.3 Автоконфигурация трассировки

```java
@AutoConfiguration
@ConditionalOnClass(RequestTracingProvider.class)
@EnableConfigurationProperties(WorkflowTracingProperties.class)
public class WorkflowTracingAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public WorkflowTracingProvider workflowTracingProvider(
            ModelRequestTraceRepository repository,
            @Qualifier("traceExecutor") Executor executor) {
        return new WorkflowTracingProvider(repository, executor);
    }
    
    @Bean("traceExecutor")
    @ConditionalOnMissingBean(name = "traceExecutor")
    public Executor traceExecutor() {
        return Executors.newFixedThreadPool(4);
    }
}
```

### Этап 2: REST API для одиночных запросов (ВЫСОКИЙ ПРИОРИТЕТ)

#### 2.1 Создание модуля driftkit-workflow-llm-api

```java
// Основные компоненты
@RestController
@RequestMapping("/api/v1/llm")
public class LLMController {
    
    @PostMapping("/message")
    public ResponseEntity<MessageId> sendMessage(@RequestBody LLMRequest request) {
        // Асинхронная отправка
    }
    
    @PostMapping("/message/sync") 
    public ResponseEntity<MessageTask> sendMessageSync(@RequestBody LLMRequest request) {
        // Синхронная отправка
    }
    
    @PostMapping("/prompt/message")
    public ResponseEntity<MessageId> sendPromptMessage(@RequestBody PromptRequest request) {
        // Отправка через промпт
    }
    
    @GetMapping("/message/{messageId}")
    public ResponseEntity<MessageTask> getMessage(@PathVariable String messageId) {
        // Получение результата
    }
}
```

#### 2.2 ModelRequestService для workflow engine

```java
@Service
@RequiredArgsConstructor
public class WorkflowModelRequestService {
    
    private final ModelClient modelClient;
    private final ModelRequestTraceRepository traceRepository;
    private final RequestTracingProvider tracingProvider;
    
    public ModelTextResponse textToText(ModelRequestContext context) {
        // Выполнение запроса с трассировкой
        ModelTextRequest request = buildTextRequest(context);
        ModelTextResponse response = modelClient.textToText(request);
        
        // Трассировка через провайдер
        if (tracingProvider != null) {
            RequestContext traceContext = RequestContext.builder()
                .contextId(context.getContextId())
                .contextType(context.getContextType())
                .chatId(context.getChatId())
                .build();
            tracingProvider.traceTextRequest(request, response, traceContext);
        }
        
        return response;
    }
}
```

#### 2.3 Интеграция с Workflow Engine

```java
// В WorkflowContext добавить поддержку ModelRequestService
public class WorkflowContext {
    
    private ModelRequestService modelRequestService;
    
    public ModelTextResponse sendTextRequest(String prompt, Map<String, Object> variables) {
        ModelRequestContext context = ModelRequestContext.builder()
            .contextId(this.getRunId())
            .contextType("WORKFLOW")
            .promptText(prompt)
            .variables(variables)
            .chatId(this.getChatId())
            .build();
            
        return modelRequestService.textToText(context);
    }
}
```

### Этап 3: Система управления задачами и сообщениями

#### 3.1 Создание domain классов

```java
@Document(collection = "message_tasks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageTaskEntity {
    @Id
    private String messageId;
    private String chatId;
    private String message;
    private String result;
    private String systemMessage;
    private Language language;
    private String workflow;
    private String modelId;
    private Double temperature;
    private Boolean jsonResponse;
    private String purpose;
    private Grade grade;
    private String gradeComment;
    private Long createdTime;
    private Long responseTime;
    // ... остальные поля
}

@Document(collection = "chats")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatEntity {
    @Id
    private String chatId;
    private String userId;
    private String name;
    private String description;
    private String systemMessage;
    private Language language;
    private Long createdTime;
    private Long lastMessageTime;
    private Boolean archived;
    // ... остальные поля
}
```

#### 3.2 Создание репозиториев

```java
@Repository
public interface MessageTaskRepository extends MongoRepository<MessageTaskEntity, String> {
    Page<MessageTaskEntity> findByChatId(String chatId, Pageable pageable);
    List<MessageTaskEntity> findMessageTasksWithFixes(Pageable pageable);
    List<MessageTaskEntity> findAllById(List<String> ids);
}

@Repository
public interface ChatRepository extends MongoRepository<ChatEntity, String> {
    List<ChatEntity> findByUserIdAndArchivedFalseOrderByLastMessageTimeDesc(String userId);
    Optional<ChatEntity> findByUserIdAndChatId(String userId, String chatId);
}
```

#### 3.3 Создание сервисов

```java
@Service
@RequiredArgsConstructor
public class LLMTaskService {
    
    private final MessageTaskRepository messageTaskRepository;
    private final ModelRequestService modelRequestService; 
    private final ExecutorService executorService;
    
    public LLMTaskFuture addTask(MessageTask messageTask) {
        // Сохранение задачи
        MessageTaskEntity entity = MessageTaskEntity.fromMessageTask(messageTask);
        messageTaskRepository.save(entity);
        
        // Асинхронное выполнение
        Future<MessageTask> future = executorService.submit(() -> {
            return processMessage(messageTask);
        });
        
        return new LLMTaskFuture(messageTask.getMessageId(), future);
    }
    
    private MessageTask processMessage(MessageTask task) {
        // Обработка через ModelRequestService с трассировкой
        ModelRequestContext context = ModelRequestContext.builder()
            .contextId(task.getMessageId())
            .contextType("MESSAGE_TASK") 
            .promptText(task.getMessage())
            .chatId(task.getChatId())
            .messageTask(task)
            .build();
            
        ModelTextResponse response = modelRequestService.textToText(context);
        
        task.setResult(response.getResponse());
        task.setResponseTime(System.currentTimeMillis());
        
        // Сохранение результата
        MessageTaskEntity entity = MessageTaskEntity.fromMessageTask(task);
        messageTaskRepository.save(entity);
        
        return task;
    }
}
```

### Этап 4: Автоконфигурация и интеграция

#### 4.1 Обновление WorkflowEngineAutoConfiguration

```java
@AutoConfiguration
@ConditionalOnClass(WorkflowEngine.class)
@EnableConfigurationProperties({WorkflowEngineProperties.class, WorkflowLLMProperties.class})
@EnableMongoRepositories(basePackages = {
    "ai.driftkit.workflow.engine.spring.repository",
    "ai.driftkit.workflow.tracing.repository"
})
@ComponentScan(basePackages = {
    "ai.driftkit.workflow.engine.spring.service",
    "ai.driftkit.workflow.engine.spring.controller", 
    "ai.driftkit.workflow.tracing.service",
    "ai.driftkit.workflow.llm.service"
})
@Import({AsyncConfig.class, WorkflowTracingAutoConfiguration.class})
public class WorkflowEngineAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public ModelRequestService modelRequestService(
            ModelClient modelClient,
            RequestTracingProvider tracingProvider,
            Executor traceExecutor) {
        return new WorkflowModelRequestService(modelClient, tracingProvider, traceExecutor);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public LLMTaskService llmTaskService(
            MessageTaskRepository messageTaskRepository,
            ModelRequestService modelRequestService,
            @Qualifier("llmExecutor") ExecutorService executorService) {
        return new LLMTaskService(messageTaskRepository, modelRequestService, executorService);
    }
    
    @Bean("llmExecutor")
    @ConditionalOnMissingBean(name = "llmExecutor") 
    public ExecutorService llmExecutorService(WorkflowLLMProperties properties) {
        return Executors.newFixedThreadPool(properties.getCoreThreads());
    }
}
```

#### 4.2 Конфигурационные свойства

```java
@ConfigurationProperties(prefix = "driftkit.workflow.llm")
@Data
public class WorkflowLLMProperties {
    private boolean enabled = true;
    private int coreThreads = 4;
    private int maxThreads = 8;
    private String defaultModel = "gpt-4o";
    private double defaultTemperature = 0.7;
    
    @Data
    public static class Tracing {
        private boolean enabled = true;
        private int traceThreads = 2;
        private String collection = "model_request_traces";
    }
    
    @Data  
    public static class Api {
        private boolean enabled = true;
        private String basePath = "/api/v1/llm";
        private boolean requireAuth = false;
    }
}
```

### Этап 5: Интеграция с существующими workflow

#### 5.1 Обновление базовых классов workflow

```java
// В WorkflowContext добавить доступ к LLM API
public class WorkflowContext {
    
    private LLMTaskService llmTaskService;
    private ModelRequestService modelRequestService;
    
    // Асинхронная отправка
    public String sendMessage(String message) {
        MessageTask task = MessageTask.builder()
            .messageId(UUID.randomUUID().toString())
            .message(message)
            .chatId(this.getChatId())
            .language(this.getLanguage())
            .build();
            
        LLMTaskFuture future = llmTaskService.addTask(task);
        return future.getMessageId();
    }
    
    // Синхронная отправка
    public String sendMessageSync(String message) {
        ModelRequestContext context = ModelRequestContext.builder()
            .contextId(this.getRunId())
            .contextType("WORKFLOW")
            .promptText(message)
            .chatId(this.getChatId())
            .build();
            
        ModelTextResponse response = modelRequestService.textToText(context);
        return response.getResponse();
    }
}
```

#### 5.2 Хелперы для workflow

```java
@Component
public class WorkflowLLMHelper {
    
    private final ModelRequestService modelRequestService;
    
    public static String ask(WorkflowContext context, String prompt) {
        return ask(context, prompt, null);
    }
    
    public static String ask(WorkflowContext context, String prompt, Map<String, Object> variables) {
        ModelRequestContext requestContext = ModelRequestContext.builder()
            .contextId(context.getRunId())
            .contextType("WORKFLOW")
            .promptText(prompt)
            .variables(variables)
            .chatId(context.getChatId())
            .build();
            
        ModelTextResponse response = context.getModelRequestService().textToText(requestContext);
        return response.getResponse();
    }
}
```

## Структура финальных модулей

```
driftkit-workflow-engine-core/
├── ... (текущее содержимое)
└── ai/driftkit/workflow/engine/core/
    └── LLMIntegration.java (интерфейс интеграции с LLM)

driftkit-workflow-engine-spring-boot-starter/
├── ... (текущее содержимое)
├── ai/driftkit/workflow/engine/spring/
│   ├── llm/
│   │   ├── controller/LLMController.java
│   │   ├── service/LLMTaskService.java
│   │   ├── service/ModelRequestService.java
│   │   └── domain/MessageTaskEntity.java
│   ├── tracing/
│   │   ├── WorkflowTracingProvider.java
│   │   ├── domain/ModelRequestTrace.java
│   │   └── repository/ModelRequestTraceRepository.java
│   └── autoconfigure/
│       ├── WorkflowLLMAutoConfiguration.java
│       └── WorkflowTracingAutoConfiguration.java

driftkit-workflow-llm-api/ (новый модуль)
├── ai/driftkit/workflow/llm/
│   ├── controller/LLMRestController.java
│   ├── service/AIService.java
│   ├── service/TasksService.java
│   ├── service/ChatService.java
│   ├── domain/
│   └── repository/
```

## Критические требования для реализации

1. **Полная совместимость API** - все существующие эндпоинты должны работать без изменений
2. **Автоматическая трассировка** - каждый запрос к модели должен быть автоматически затрейсирован
3. **Асинхронность по умолчанию** - поддержка как синхронных, так и асинхронных запросов
4. **Интеграция с промптами** - поддержка `PromptService` и `PromptRequest`
5. **Обратная совместимость** - поддержка существующих workflow без изменений
6. **Конфигурируемость** - возможность отключения компонентов через properties
7. **Мониторинг и метрики** - интеграция с системами мониторинга

## Анализ дополнительных компонентов

### LLMMemoryProvider - Статус: НЕ ТРЕБУЕТСЯ

**Функционал уже покрыт через:**
- `ChatStore` (ai.driftkit.common.service.ChatStore) - управление памятью чата
- `InMemoryChatStore` - имплементация с токенизацией
- `ChatSessionRepository` - управление сессиями
- `WorkflowService.getChatHistory()` - загрузка истории

**Вывод:** LLMMemoryProvider не нужен как отдельный компонент.

### ModelWorkflow vs LLMAgent

**LLMAgent (driftkit-workflow-engine-agents) уже имеет:**
- ✅ Все типы запросов (text-to-text, text-to-image, image-to-text)
- ✅ Интеграцию с PromptService
- ✅ Интерфейс RequestTracingProvider для трассировки
- ✅ Работу с переменными и контекстными сообщениями

**Нужно добавить:**
- ❌ Удобные методы для workflow (sendTextToText, sendPromptText)
- ❌ Интеграцию с WorkflowContext

### RequestTracingProvider - ИНТЕРФЕЙС УЖЕ СУЩЕСТВУЕТ

В модуле `driftkit-workflow-engine-agents` уже есть интерфейс:
```java
public interface RequestTracingProvider {
    void traceTextRequest(ModelTextRequest request, ModelTextResponse response, RequestContext context);
    void traceImageRequest(ModelImageRequest request, ModelImageResponse response, RequestContext context);
    void traceImageToTextRequest(ModelTextRequest request, ModelTextResponse response, RequestContext context);
}
```

**Нужна только Spring имплементация!**

## Обновленный план реализации

### Этап 1: SpringRequestTracingProvider (КРИТИЧНО)

```java
@Component
@RequiredArgsConstructor
public class SpringRequestTracingProvider implements RequestTracingProvider {
    
    private final ModelRequestTraceRepository traceRepository;
    private final Executor traceExecutor;
    
    @Override
    public void traceTextRequest(ModelTextRequest request, ModelTextResponse response, RequestContext context) {
        ModelRequestTrace trace = buildTextTrace(request, response, context);
        CompletableFuture.runAsync(() -> saveTrace(trace), traceExecutor);
    }
    // ... остальные методы
}
```

### Этап 2: WorkflowModelHelper - замена ModelWorkflow

```java
@Component
@RequiredArgsConstructor
public class WorkflowModelHelper {
    
    private final ModelClient modelClient;
    private final PromptService promptService;
    private final RequestTracingProvider tracingProvider;
    
    public LLMAgent createAgent(WorkflowContext context) {
        return LLMAgent.builder()
            .modelClient(modelClient)
            .chatId(context.getChatId())
            .promptService(promptService)
            .tracingProvider(tracingProvider)
            .build();
    }
    
    public ModelTextResponse sendTextToText(String promptId, Map<String, Object> variables, 
                                           WorkflowContext context) {
        LLMAgent agent = createAgent(context);
        Prompt prompt = promptService.getCurrentPromptOrThrow(promptId, getLanguage(context));
        return agent.execute(prompt.getMessage(), variables);
    }
}
```

### Этап 3: Интеграция в WorkflowContext

```java
public class WorkflowContext {
    
    private WorkflowModelHelper modelHelper;
    
    public String askWithPrompt(String promptId, Map<String, Object> variables) {
        ModelTextResponse response = modelHelper.sendTextToText(promptId, variables, this);
        return response.getResponse();
    }
    
    public String ask(String text, Map<String, Object> variables) {
        ModelTextResponse response = modelHelper.sendPromptText(text, variables, this);
        return response.getResponse();
    }
}
```

## Приоритетность реализации (ОБНОВЛЕНО)

**КРИТИЧНО (делать в первую очередь):**
1. **SpringRequestTracingProvider** - имплементация существующего интерфейса RequestTracingProvider
2. **ModelRequestTrace + репозиторий** - домен для хранения трейсов
3. **REST API для одиночных запросов** - основная функция для внешних клиентов (/message, /message/sync)

**ВЫСОКИЙ ПРИОРИТЕТ:**
4. **WorkflowModelHelper** - удобная замена ModelWorkflow через LLMAgent
5. **Интеграция в WorkflowContext** - методы ask(), askWithPrompt()
6. **Система управления задачами** - MessageTaskEntity, TasksService

**СРЕДНИЙ ПРИОРИТЕТ:**
7. Дополнительные контроллеры (Analytics, Checklist)
8. Миграция существующих workflow

**НЕ ТРЕБУЕТСЯ:**
- LLMMemoryProvider (функционал уже есть)
- ModelWorkflow как базовый класс (заменяется на WorkflowModelHelper)
- Дублирование RequestTracingProvider (интерфейс уже есть)

Данный план обеспечивает полный перенос функционала с использованием существующих компонентов (LLMAgent, RequestTracingProvider) и минимальными изменениями.
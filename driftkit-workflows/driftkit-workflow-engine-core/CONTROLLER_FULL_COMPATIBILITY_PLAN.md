# План полной совместимости контроллеров

## Критические изменения для ПОЛНОГО соответствия

### 1. ИЗМЕНИТЬ базовые пути в WorkflowController

Текущий контроллер должен быть разделен на ДВА контроллера:

#### 1.1 AssistantController (для chat-assistant-framework совместимости)
```java
@RestController
@RequestMapping("/public/api1.0/ai/assistant/v2/")  // ТОЧНО КАК В СТАРОМ!
```

#### 1.2 WorkflowAdminController (для workflows-core совместимости)
```java
@Controller
@RequestMapping("/data/v1.0/admin/workflows")  // ТОЧНО КАК В СТАРОМ!
```

### 2. НЕДОСТАЮЩИЕ эндпоинты

#### 2.1 В AssistantController части:

**КРИТИЧНО - полностью отсутствует:**
```java
// ТОЧНАЯ сигнатура из старого контроллера
@GetMapping("/chat/history/pageable")
public PageableResponseWithChatMessage historyPageable(
    final @NotNull HttpServletRequest request,  // ВАЖНО: HttpServletRequest!
    @RequestParam String chatId,
    @RequestParam(required = false) String userId,
    @RequestParam(required = false, defaultValue = "0") int page,
    @RequestParam(required = false, defaultValue = "10") int limit,  // ВАЖНО: defaultValue = "10", не "1000"!
    @RequestParam(required = false, defaultValue = "asc") String sort
)
```

**КРИТИЧНО - отсутствует эндпоинт:**
```java
@GetMapping("/workflow/first-schema/{workflowId}")  // НЕ "/schema/initial"!
public FirstStepSchemaResponse getFirstStepSchema(@PathVariable String workflowId)
```

#### 2.2 В WorkflowAdminController части:

**КРИТИЧНО - неправильный формат ответа:**
```java
@GetMapping
public @ResponseBody RestResponse<List<Map<String, String>>> getWorkflows()  // НЕ List<WorkflowMetadata>!
```

### 3. НЕСООТВЕТСТВИЕ форматов ответов

#### 3.1 List Chats
- **Старый**: Возвращает `PageableResponseWithChat`
- **Новый**: Возвращает `Page<ChatInfo>` 
- **НУЖНО**: Вернуть ТОЧНО `PageableResponseWithChat`!

#### 3.2 Отсутствующие DTO классы:
```java
// ДОЛЖНЫ БЫТЬ ДОБАВЛЕНЫ:
public class PageableResponseWithChat
public class PageableResponseWithChatMessage  
public class RestResponse<T>
```

### 4. НЕСООТВЕТСТВИЕ импортов и пакетов

#### 4.1 ChatMessage, ChatRequest, ChatResponse
- **Старый**: `ai.driftkit.chat.framework.model.ChatDomain.*`
- **Новый**: `ai.driftkit.common.domain.chat.*`
- **РЕШЕНИЕ**: Использовать общие классы, но ПРОВЕРИТЬ полную совместимость полей!

#### 4.2 AIFunctionSchema
- **Старый**: `ai.driftkit.chat.framework.ai.domain.AIFunctionSchema`
- **Новый**: `ai.driftkit.workflow.engine.schema.AIFunctionSchema`
- **РЕШЕНИЕ**: Mapper или общий интерфейс

### 5. Детальный план реализации

#### Фаза 1: Разделение контроллера
1. Создать `AssistantCompatibilityController` с путем `/public/api1.0/ai/assistant/v2/`
2. Создать `WorkflowAdminController` с путем `/data/v1.0/admin/workflows`
3. Перенести соответствующие методы из текущего `WorkflowController`

#### Фаза 2: Добавление недостающих эндпоинтов
1. Добавить `/chat/history/pageable` с ТОЧНОЙ сигнатурой
2. Добавить `/workflow/first-schema/{workflowId}` 
3. Изменить формат ответа для `/data/v1.0/admin/workflows`

#### Фаза 3: Создание недостающих DTO
1. `PageableResponseWithChat` - обертка для Page<ChatSession>
2. `PageableResponseWithChatMessage` - обертка для Page<ChatMessage>
3. `RestResponse<T>` - общая обертка с полем success

#### Фаза 4: Проверка полной совместимости
1. Сравнить КАЖДОЕ поле в request/response
2. Проверить default значения параметров
3. Проверить HTTP статусы и обработку ошибок

### 6. ВАЖНЫЕ детали реализации

#### 6.1 Метод decode()
```java
// Старый использует Charset.defaultCharset()
URLDecoder.decode(userId, Charset.defaultCharset());

// Новый использует StandardCharsets.UTF_8
URLDecoder.decode(value, StandardCharsets.UTF_8);

// ДОЛЖНО БЫТЬ ОДИНАКОВО!
```

#### 6.2 Обработка исключений
- Старый выбрасывает `RuntimeException` с форматом "Forbidden for [%s] [%s]"
- Новый выбрасывает `ResponseStatusException`
- НУЖНО: Полное соответствие!

#### 6.3 Параметр HttpServletRequest
- Старый: `historyPageable` принимает `HttpServletRequest request`
- Новый: НЕ использует
- НУЖНО: Добавить для полной совместимости!

### 7. Конкретные изменения кода

#### 7.1 Создать новый контроллер для Assistant API:
```java
@RestController
@RequestMapping("/public/api1.0/ai/assistant/v2/")
@RequiredArgsConstructor
@Validated
public class AssistantCompatibilityController {
    // ВСЕ chat-related методы сюда
    // С ТОЧНЫМИ сигнатурами из старого AssistantController
}
```

#### 7.2 Создать контроллер для Workflow Admin API:
```java
@Controller
@RequestMapping(path = "/data/v1.0/admin/workflows")
public class WorkflowAdminController {
    @GetMapping
    public @ResponseBody RestResponse<List<Map<String, String>>> getWorkflows() {
        // Реализация
    }
}
```

### 8. Проверка совместимости

Каждый эндпоинт должен быть проверен:
1. ✅ Путь ИДЕНТИЧЕН
2. ✅ HTTP метод ИДЕНТИЧЕН  
3. ✅ Параметры запроса ИДЕНТИЧНЫ (имена, типы, default значения)
4. ✅ Формат ответа ИДЕНТИЧЕН
5. ✅ HTTP статусы ИДЕНТИЧНЫ
6. ✅ Обработка ошибок ИДЕНТИЧНА

## ИТОГ

Новый фреймворк НЕ МОЖЕТ использовать другие пути или форматы. Он должен быть 100% drop-in replacement для старых контроллеров. Все новые функции могут быть добавлены как ДОПОЛНИТЕЛЬНЫЕ эндпоинты, но существующие должны работать ИДЕНТИЧНО!
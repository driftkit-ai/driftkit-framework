# Structured Outputs Implementation Guide

## Обзор

Данный документ описывает имплементацию Structured Outputs (структурированных выходов) для AI клиентов Claude и Gemini в фреймворке DriftKit. Structured Outputs позволяют гарантировать, что ответы языковых моделей соответствуют заданной JSON схеме.

## Содержание

1. [Архитектура](#архитектура)
2. [Claude Structured Outputs](#claude-structured-outputs)
3. [Gemini Structured Outputs](#gemini-structured-outputs)
4. [Общая модель данных](#общая-модель-данных)
5. [Примеры использования](#примеры-использования)
6. [Сравнение провайдеров](#сравнение-провайдеров)

---

## Архитектура

### Общая архитектура Structured Outputs

```mermaid
graph TB
    subgraph "Client Layer"
        User[User Application]
    end

    subgraph "DriftKit Common"
        RF[ResponseFormat]
        JS[JsonSchema]
        SP[SchemaProperty]
    end

    subgraph "Provider Clients"
        OAI[OpenAI Client]
        CLA[Claude Client]
        GEM[Gemini Client]
    end

    subgraph "Provider APIs"
        OAIAPI[OpenAI API]
        CLAAPI[Claude API]
        GEMAPI[Gemini API]
    end

    User -->|Creates request with| RF
    RF -->|Common schema| JS
    JS -->|Properties| SP

    RF -->|Converts to| OAI
    RF -->|Converts to| CLA
    RF -->|Converts to| GEM

    OAI -->|JSON Schema format| OAIAPI
    CLA -->|output_format| CLAAPI
    GEM -->|responseSchema| GEMAPI

    OAIAPI -->|Structured response| User
    CLAAPI -->|Structured response| User
    GEMAPI -->|Structured response| User
```

### Поток данных для Structured Outputs

```mermaid
sequenceDiagram
    participant U as User
    participant MC as ModelClient
    participant RF as ResponseFormat
    participant Conv as Schema Converter
    participant API as Provider API

    U->>MC: textToText(prompt with ResponseFormat)
    MC->>RF: Get JsonSchema
    RF->>Conv: Convert to provider-specific format

    alt OpenAI
        Conv->>API: response_format.json_schema
    else Claude
        Conv->>API: output_format.json_schema
    else Gemini
        Conv->>API: generationConfig.responseSchema
    end

    API->>MC: Structured JSON response
    MC->>U: ModelTextResponse with validated content
```

---

## Claude Structured Outputs

### Обзор Claude API

Claude поддерживает два режима structured outputs:
1. **JSON Output Mode** - через параметр `output_format`
2. **Strict Tool Use** - через флаг `strict: true` в определении инструментов

### Архитектура Claude Structured Outputs

```mermaid
classDiagram
    class ClaudeMessageRequest {
        +String model
        +List~ClaudeMessage~ messages
        +Integer maxTokens
        +OutputFormat outputFormat
        +List~ClaudeTool~ tools
    }

    class OutputFormat {
        +String type
        +JsonSchema jsonSchema
    }

    class JsonSchema {
        +String name
        +Boolean strict
        +SchemaDefinition schema
    }

    class SchemaDefinition {
        +String type
        +Map~String, Property~ properties
        +List~String~ required
        +Boolean additionalProperties
    }

    class Property {
        +String type
        +String description
        +List~String~ enum
        +Map~String, Property~ properties
        +List~String~ required
        +Property items
    }

    ClaudeMessageRequest --> OutputFormat
    OutputFormat --> JsonSchema
    JsonSchema --> SchemaDefinition
    SchemaDefinition --> Property
    Property --> Property : nested
```

### Процесс конверсии схемы для Claude

```mermaid
flowchart TD
    A[ResponseFormat.JsonSchema] --> B{Check type}
    B -->|JSON_SCHEMA| C[Create OutputFormat]
    B -->|JSON_OBJECT| D[Set type: json_object only]

    C --> E[Convert properties]
    E --> F{For each property}
    F --> G[Map type field]
    F --> H[Map description]
    F --> I[Map enum values]
    F --> J{Has nested properties?}

    J -->|Yes| K[Recursively convert nested]
    J -->|No| L[Create simple Property]

    K --> M[Set required fields]
    L --> M
    M --> N[Set additionalProperties: false]
    N --> O[Create JsonSchema with strict: true]
    O --> P[Return OutputFormat]

    D --> Q[Return simple OutputFormat]
```

### Особенности Claude Structured Outputs

#### Требования API

- **Beta Header**: Требуется заголовок `anthropic-beta: structured-outputs-2025-11-13`
- **Supported Models**:
  - Claude Sonnet 4.5
  - Claude Opus 4.1
- **Strict Mode**: По умолчанию включен (`strict: true`)

#### Поддерживаемые JSON Schema конструкции

✅ **Поддерживаются:**
- Стандартные типы: `object`, `array`, `string`, `number`, `boolean`, `null`
- `required` - обязательные поля
- `enum` - перечисления
- Вложенные объекты и массивы
- `description` для полей

❌ **НЕ поддерживаются:**
- `minimum`, `maximum` - добавляются SDK в description
- `minLength`, `maxLength` - добавляются SDK в description
- Циклические и чрезмерно рекурсивные определения
- Сложные regex patterns

#### Обработка ошибок

```mermaid
flowchart LR
    A[API Request] --> B{Response Status}
    B -->|stop_reason: end_turn| C[Success]
    B -->|stop_reason: refusal| D[Refusal - may not match schema]
    B -->|stop_reason: max_tokens| E[Truncation - incomplete output]
    B -->|HTTP 400| F[Schema too complex]

    D --> G[Handle gracefully]
    E --> G
    F --> H[Simplify schema]
```

### Производительность и кэширование

```mermaid
gantt
    title Claude Structured Outputs - Performance Timeline
    dateFormat X
    axisFormat %s

    section First Request
    Grammar Compilation :a1, 0, 2000
    Model Processing :a2, 2000, 5000

    section Cached Requests (24h)
    Direct Processing :b1, 0, 3000
```

---

## Gemini Structured Outputs

### Обзор Gemini API

Gemini использует два параметра в `generationConfig`:
- `responseMimeType`: `"application/json"`
- `responseSchema`: JSON Schema объект

### Архитектура Gemini Structured Outputs

```mermaid
classDiagram
    class GeminiChatRequest {
        +List~GeminiContent~ contents
        +GeminiGenerationConfig generationConfig
        +List~GeminiTool~ tools
    }

    class GeminiGenerationConfig {
        +Double temperature
        +Integer maxOutputTokens
        +String responseMimeType
        +GeminiSchema responseSchema
    }

    class GeminiSchema {
        +String type
        +String description
        +Map~String, GeminiSchema~ properties
        +GeminiSchema items
        +List~String~ enum
        +List~String~ required
        +List~String~ propertyOrdering
        +Boolean nullable
        +String format
    }

    GeminiChatRequest --> GeminiGenerationConfig
    GeminiGenerationConfig --> GeminiSchema
    GeminiSchema --> GeminiSchema : recursive
```

### Процесс конверсии схемы для Gemini

```mermaid
flowchart TD
    A[ResponseFormat.JsonSchema] --> B{Check type}
    B -->|JSON_SCHEMA| C[Create GeminiSchema]
    B -->|JSON_OBJECT| D[Set responseMimeType only]

    C --> E[Convert to uppercase type]
    E --> F{Type mapping}
    F -->|object| G[Type: OBJECT]
    F -->|array| H[Type: ARRAY]
    F -->|string| I[Type: STRING]
    F -->|number| J[Type: NUMBER]
    F -->|integer| K[Type: INTEGER]
    F -->|boolean| L[Type: BOOLEAN]

    G --> M{Has properties?}
    M -->|Yes| N[Convert each property recursively]
    N --> O[Add to properties map]
    O --> P[Set required list]
    P --> Q[Set propertyOrdering for Gemini 2.0+]

    H --> R[Convert items schema]
    R --> S[Set items field]

    I --> T{Has enum?}
    T -->|Yes| U[Set enum values]
    T -->|No| V{Has format?}
    V -->|Yes| W[Set format: date-time, date, time]

    Q --> X[Set description]
    S --> X
    U --> X
    W --> X
    X --> Y[Return GeminiSchema]

    D --> Z[Return simple config]
```

### Особенности Gemini Structured Outputs

#### Поддерживаемые модели

- ✅ Gemini 3 Pro Preview
- ✅ Gemini 2.5 Pro
- ✅ Gemini 2.5 Flash
- ✅ Gemini 2.5 Flash-Lite
- ✅ Gemini 2.0 Flash (требует `propertyOrdering`)

#### Поддерживаемые типы и ограничения

```mermaid
graph LR
    subgraph "Базовые типы"
        A[STRING]
        B[NUMBER]
        C[INTEGER]
        D[BOOLEAN]
        E[OBJECT]
        F[ARRAY]
        G[NULL]
    end

    subgraph "Ограничения для STRING"
        A --> H[enum]
        A --> I[format: date-time, date, time]
    end

    subgraph "Ограничения для NUMBER/INTEGER"
        B --> J[enum]
        B --> K[minimum]
        B --> L[maximum]
        C --> J
        C --> K
        C --> L
    end

    subgraph "Ограничения для ARRAY"
        F --> M[items]
        F --> N[minItems]
        F --> O[maxItems]
        F --> P[prefixItems]
    end

    subgraph "Ограничения для OBJECT"
        E --> Q[properties]
        E --> R[required]
        E --> S[additionalProperties]
        E --> T[propertyOrdering]
    end
```

#### Дескриптивные свойства

```mermaid
mindmap
  root((GeminiSchema))
    title
      Название схемы
    description
      Инструкции для модели
      Влияет на поведение
    format
      date-time
      date
      time
    propertyOrdering
      Порядок полей для Gemini 2.0
```

### Интеграция с инструментами Gemini 3

```mermaid
flowchart TB
    A[Gemini 3 Pro Preview] --> B[Structured Outputs]
    A --> C[Google Search]
    A --> D[URL Context]
    A --> E[Code Execution]

    B --> F[Combined Usage]
    C --> F
    D --> F
    E --> F

    F --> G[Enhanced Responses]
```

---

## Общая модель данных

### ResponseFormat - Унифицированная модель

```mermaid
classDiagram
    class ResponseFormat {
        +ResponseType type
        +JsonSchema jsonSchema
        +jsonObject(Class) ResponseFormat$
        +jsonSchema(Class) ResponseFormat$
        +jsonObject() ResponseFormat$
        +text() ResponseFormat$
    }

    class ResponseType {
        <<enumeration>>
        TEXT
        JSON_OBJECT
        JSON_SCHEMA
        IMAGE
    }

    class JsonSchema {
        +String title
        +String type
        +Map~String, SchemaProperty~ properties
        +List~String~ required
        +Boolean additionalProperties
        +Boolean strict
    }

    class SchemaProperty {
        +String type
        +String description
        +List~String~ enumValues
        +Map~String, SchemaProperty~ properties
        +List~String~ required
        +SchemaProperty items
        +Object additionalProperties
    }

    ResponseFormat --> ResponseType
    ResponseFormat --> JsonSchema
    JsonSchema --> SchemaProperty
    SchemaProperty --> SchemaProperty : recursive
```

### Маппинг между провайдерами

```mermaid
graph TB
    subgraph "Common Model"
        RF[ResponseFormat]
        JS[JsonSchema]
    end

    subgraph "OpenAI Model"
        OFmt[ResponseFormat]
        OJS[JsonSchema]
        OSD[SchemaDefinition]
    end

    subgraph "Claude Model"
        CFmt[OutputFormat]
        CJS[JsonSchema]
        CSD[SchemaDefinition]
    end

    subgraph "Gemini Model"
        GCfg[GenerationConfig]
        GSch[GeminiSchema]
    end

    RF -->|Already implemented| OFmt
    RF -->|New implementation| CFmt
    RF -->|Enhanced implementation| GCfg

    JS -->|convertModelJsonSchema| OJS
    JS -->|convertToClaudeJsonSchema| CJS
    JS -->|convertToGeminiSchema| GSch

    OJS --> OSD
    CJS --> CSD
```

---

## Примеры использования

### Пример 1: Простая структура данных

#### Определение Java класса

```java
public class UserProfile {
    private String name;
    private int age;
    private String email;

    // getters, setters, constructors
}
```

#### Использование с разными провайдерами

```java
// Создание request с structured output
ModelTextRequest request = ModelTextRequest.builder()
    .messages(messages)
    .responseFormat(ResponseFormat.jsonSchema(UserProfile.class))
    .build();

// OpenAI
OpenAIModelClient openAIClient = new OpenAIModelClient();
ModelTextResponse openAIResponse = openAIClient.textToText(request);

// Claude
ClaudeModelClient claudeClient = new ClaudeModelClient();
ModelTextResponse claudeResponse = claudeClient.textToText(request);

// Gemini
GeminiModelClient geminiClient = new GeminiModelClient();
ModelTextResponse geminiResponse = geminiClient.textToText(request);
```

#### Генерируемые схемы

**Common Schema:**
```json
{
  "type": "object",
  "title": "UserProfile",
  "properties": {
    "name": {"type": "string", "description": "User name"},
    "age": {"type": "integer", "description": "User age"},
    "email": {"type": "string", "description": "User email"}
  },
  "required": ["name", "age", "email"],
  "additionalProperties": false
}
```

**Claude Format:**
```json
{
  "type": "json_schema",
  "json_schema": {
    "name": "UserProfile",
    "strict": true,
    "schema": {
      "type": "object",
      "properties": {
        "name": {"type": "string", "description": "User name"},
        "age": {"type": "number", "description": "User age"},
        "email": {"type": "string", "description": "User email"}
      },
      "required": ["name", "age", "email"],
      "additionalProperties": false
    }
  }
}
```

**Gemini Format:**
```json
{
  "responseMimeType": "application/json",
  "responseSchema": {
    "type": "OBJECT",
    "properties": {
      "name": {"type": "STRING", "description": "User name"},
      "age": {"type": "INTEGER", "description": "User age"},
      "email": {"type": "STRING", "description": "User email"}
    },
    "required": ["name", "age", "email"],
    "propertyOrdering": ["name", "age", "email"]
  }
}
```

### Пример 2: Сложная вложенная структура

```mermaid
classDiagram
    class Company {
        +String name
        +Address address
        +List~Employee~ employees
    }

    class Address {
        +String street
        +String city
        +String country
    }

    class Employee {
        +String name
        +String role
        +double salary
    }

    Company --> Address
    Company --> Employee
```

```java
public class Company {
    private String name;
    private Address address;
    private List<Employee> employees;
}

public class Address {
    private String street;
    private String city;
    private String country;
}

public class Employee {
    private String name;
    private String role;
    private double salary;
}

// Usage
ResponseFormat format = ResponseFormat.jsonSchema(Company.class);
```

### Пример 3: Enum и ограничения

```java
public class OrderStatus {
    public enum Status {
        PENDING, PROCESSING, SHIPPED, DELIVERED, CANCELLED
    }

    private String orderId;
    private Status status;
    private LocalDateTime timestamp;
}

// Генерируется схема с enum
{
  "properties": {
    "orderId": {"type": "string"},
    "status": {
      "type": "string",
      "enum": ["PENDING", "PROCESSING", "SHIPPED", "DELIVERED", "CANCELLED"]
    },
    "timestamp": {
      "type": "string",
      "format": "date-time"
    }
  }
}
```

---

## Сравнение провайдеров

### Таблица возможностей

| Возможность | OpenAI | Claude | Gemini |
|------------|--------|--------|--------|
| JSON Object Mode | ✅ | ✅ | ✅ |
| JSON Schema Mode | ✅ | ✅ | ✅ |
| Strict Mode | ✅ | ✅ (default) | ⚠️ (implicit) |
| Nested Objects | ✅ | ✅ | ✅ |
| Arrays | ✅ | ✅ | ✅ |
| Enums | ✅ | ✅ | ✅ |
| Format (date-time, etc.) | ⚠️ | ⚠️ | ✅ |
| Min/Max constraints | ⚠️ | ❌ | ✅ |
| Pattern/Regex | ⚠️ | ❌ | ⚠️ |
| Nullable fields | ✅ | ✅ | ✅ |
| Property ordering | ❌ | ❌ | ✅ (Gemini 2.0+) |
| Streaming support | ✅ | ✅ | ✅ |
| Tool use + Structured | ✅ | ✅ | ✅ |
| Caching | ✅ | ✅ (24h) | ✅ |

### Производительность

```mermaid
graph LR
    subgraph "First Request"
        A1[OpenAI: ~200ms overhead]
        B1[Claude: ~2000ms overhead]
        C1[Gemini: ~150ms overhead]
    end

    subgraph "Cached Request"
        A2[OpenAI: ~50ms overhead]
        B2[Claude: ~100ms overhead]
        C2[Gemini: ~50ms overhead]
    end

    A1 -.->|After cache warm-up| A2
    B1 -.->|After cache warm-up| B2
    C1 -.->|After cache warm-up| C2
```

### Рекомендации по выбору

```mermaid
flowchart TD
    A{Выбор провайдера} --> B{Требования}

    B -->|Максимальная строгость схемы| C[Claude]
    B -->|Сложные constraints min/max| D[Gemini]
    B -->|Стандартные JSON схемы| E[OpenAI или Gemini]
    B -->|Низкая латентность| F[OpenAI или Gemini]
    B -->|Agentic workflows с tools| G[Claude]
    B -->|Интеграция с Google Search| H[Gemini 3]

    C --> I[Лучше для: Data extraction, Classification]
    D --> J[Лучше для: Validated business data]
    E --> K[Лучше для: General purpose]
    F --> L[Лучше для: Real-time apps]
    G --> M[Лучше для: Multi-tool validation]
    H --> N[Лучше для: RAG with web search]
```

### Стоимость токенов

```mermaid
graph TB
    subgraph "Token Overhead"
        A[Base prompt tokens]
        B[+ Schema injection ~50-200 tokens]
        C[+ System instructions ~20-50 tokens]
    end

    A --> D[Total Input Tokens]
    B --> D
    C --> D

    D --> E{Provider}
    E -->|OpenAI| F[GPT-4: $0.03/1k tokens]
    E -->|Claude| G[Sonnet 4.5: $0.003/1k tokens]
    E -->|Gemini| H[2.5 Pro: $0.00125/1k tokens]
```

---

## Лучшие практики

### 1. Дизайн схем

```mermaid
flowchart LR
    A[Schema Design] --> B[Keep schemas simple]
    A --> C[Use clear descriptions]
    A --> D[Leverage enums]
    A --> E[Avoid deep nesting]

    B --> F[Better performance]
    C --> G[Better accuracy]
    D --> H[Constrained values]
    E --> I[Avoid complexity errors]
```

### 2. Обработка ошибок

```java
try {
    ModelTextResponse response = client.textToText(request);

    // Проверка finish_reason
    if ("refusal".equals(response.getChoices().get(0).getFinishReason())) {
        // Модель отказалась отвечать - может не соответствовать схеме
        log.warn("Model refused to respond");
    } else if ("length".equals(response.getChoices().get(0).getFinishReason())) {
        // Ответ обрезан - неполный JSON
        log.warn("Response truncated - increase max_tokens");
    }

    // Парсинг и валидация
    String content = response.getChoices().get(0).getMessage().getContent();
    UserProfile profile = objectMapper.readValue(content, UserProfile.class);

} catch (JsonProcessingException e) {
    log.error("Failed to parse structured output", e);
    // Fallback logic
}
```

### 3. Оптимизация производительности

- Переиспользуйте одинаковые схемы для кэширования
- Упрощайте схемы где возможно
- Используйте `additionalProperties: false` для строгости
- Для Claude: первый запрос медленнее из-за компиляции грамматики

### 4. Валидация на стороне клиента

```java
// Дополнительная валидация после получения ответа
public <T> T validateAndParse(String jsonContent, Class<T> clazz) {
    // 1. JSON validation
    if (!JsonUtils.isValidJSON(jsonContent)) {
        jsonContent = JsonUtils.fixIncompleteJSON(jsonContent);
    }

    // 2. Schema validation
    T object = objectMapper.readValue(jsonContent, clazz);

    // 3. Business logic validation
    validateBusinessRules(object);

    return object;
}
```

---

## Миграция и обратная совместимость

### Поддержка legacy кода

```mermaid
flowchart TB
    A[Legacy Code] --> B{Uses jsonObjectSupport?}
    B -->|Yes| C[Auto-convert to JSON_OBJECT mode]
    B -->|No| D[Use explicit ResponseFormat]

    C --> E[Set responseMimeType: application/json]
    D --> F{Has ResponseFormat?}
    F -->|JSON_SCHEMA| G[Full structured output]
    F -->|JSON_OBJECT| E
    F -->|TEXT| H[No conversion]
```

### Пример миграции

```java
// Old approach (still supported)
ModelClient client = new ClaudeModelClient();
client.setJsonObjectSupport(true);

// New approach (recommended)
ModelTextRequest request = ModelTextRequest.builder()
    .responseFormat(ResponseFormat.jsonSchema(MyClass.class))
    .build();
```

---

## Заключение

Structured Outputs - мощный инструмент для обеспечения надежности и предсказуемости ответов AI моделей. Реализация в DriftKit обеспечивает:

✅ Унифицированный API для всех провайдеров
✅ Автоматическую конверсию схем
✅ Валидацию и обработку ошибок
✅ Обратную совместимость
✅ Production-ready решение

### Дальнейшее развитие

- [ ] Поддержка дополнительных JSON Schema constraints
- [ ] Кэширование скомпилированных схем
- [ ] Метрики и мониторинг использования
- [ ] A/B тестирование разных провайдеров
- [ ] Автоматическая генерация документации из схем

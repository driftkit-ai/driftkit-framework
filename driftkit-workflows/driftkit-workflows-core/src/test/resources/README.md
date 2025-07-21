# Structured Output Integration Test

Этот тест демонстрирует работу structured output функциональности с OpenAI API.

## Настройка и запуск

1. **Установите API ключ OpenAI:**
   ```bash
   export OPENAI_API_KEY="your-openai-api-key-here"
   ```

2. **Включите тест:**
   Удалите аннотацию `@Disabled` в классе `StructuredOutputIntegrationTest`

3. **Запустите тест:**
   ```bash
   mvn test -Dtest=StructuredOutputIntegrationTest -pl driftkit-workflows/driftkit-workflows-core
   ```

## Что тестируется

### 1. Текстовый ввод + Structured Output
- Извлечение информации о человеке из текста
- Проверка валидности JSON схемы
- Парсинг ответа в POJO класс

### 2. Ввод изображения + Structured Output  
- Анализ изображения с извлечением ключевой информации
- Автоматическое создание тестового изображения если реальное не найдено
- Структурированный ответ с описанием, объектами, цветами

### 3. Текст + Изображение + Structured Output
- Комбинированный анализ документа
- Извлечение людей, дат, компаний из изображения документа
- Подробный структурированный анализ

### 4. Сравнение форматов ответов
- `text` - обычный текстовый ответ
- `json_object` - неструктурированный JSON 
- `json_schema` - строго структурированный JSON

## Тестовые данные

Тест включает следующие POJO классы для демонстрации structured output:

- **PersonExtraction** - извлечение информации о людях
- **ImageAnalysis** - анализ изображений  
- **DocumentAnalysis** - анализ документов
- **WeatherInfo** - информация о погоде

## Примечания

- Тесты по умолчанию отключены (аннотация `@Disabled`)
- Требуется рабочий OpenAI API ключ
- Используется модель `gpt-4o` с поддержкой vision и structured outputs
- Тест автоматически создаёт синтетическое изображение если реальное не найдено
- Все JSON операции используют `JsonUtils` из driftkit-common

## Структура теста

```java
@Test
void testStructuredOutputWithTextInput() {
    // Создание ResponseFormat с JSON схемой
    ResponseFormat responseFormat = ResponseFormat.jsonSchema(PersonExtraction.class);
    
    // Создание запроса с structured output
    ModelTextRequest request = ModelTextRequest.builder()
        .messages(...)
        .responseFormat(responseFormat)  // <-- Ключевая часть
        .build();
        
    // Выполнение запроса
    ModelTextResponse response = modelClient.textToText(request);
    
    // Парсинг структурированного ответа
    PersonExtraction person = JsonUtils.fromJson(content, PersonExtraction.class);
}
```

Этот тест демонстрирует полную интеграцию structured output функциональности в DriftKit фреймворке.
2.2. Декларативное Построение Графа (Fluent API)
Для сложных, нелинейных или динамически генерируемых workflow предоставляется Fluent API. Этот подход, вдохновленный синтаксической элегантностью mastra.ai , дает разработчику полный и явный контроль над структурой графа.   

Усовершенствованный StepDefinition:

Ключевое изменение — отказ от обязательных строковых идентификаторов.

StepDefinition.of(methodReference): Фабричный метод, принимающий ссылку на метод (::). ID шага автоматически выводится из имени метода. Это основной способ определения шагов.

StepDefinition.of(id, lambda): Для лямбда-выражений, у которых нет имени, сохраняется возможность указать ID вручную.

API класса WorkflowBuilder:

Workflow.define(id, inputType, outputType): Статический фабричный метод для начала определения нового workflow.

.then(StepDefinition): Определяет последовательное выполнение шагов. Прямой аналог .then() в mastra.ai.   

.parallel(List<StepDefinition>): Определяет параллельное выполнение группы шагов. Выполнение продолжится только после завершения всех шагов в группе. Аналог .parallel() в mastra.ai.   

.branch(Predicate<WorkflowContext>, StepDefinition, StepDefinition): Определяет условное ветвление. Первый StepDefinition выполняется, если предикат истинен, второй — если ложен. Возможны перегруженные версии для более сложных ветвлений. Аналог .branch() в mastra.ai.   

.build(): Завершает определение, валидирует граф и возвращает неизменяемый объект WorkflowGraph. Аналогичен методу .commit() в mastra.ai.   

Пример использования Fluent API с DI и автоматическим определением ID:

Java

@Component
public class PaymentSteps {
// Шаг без контекста
public StepResult<ValidatedOrder> validateOrder(Order order) { /*... */ return new Continue<>(validatedOrder); }

    // Шаг с контекстом
    public StepResult<Void> chargeCreditCard(ValidatedOrder order, WorkflowContext context) { 
        System.out.println("Charging card for runId: " + context.runId());
        /*... */ 
        return new Continue<>(null); 
    }
    
    public StepResult<Void> updateInventory(ValidatedOrder order) { /*... */ return new Continue<>(null); }
    
    public StepResult<Receipt> generateReceipt(WorkflowContext context) { 
        // Этот шаг не получает данные от предыдущего, только контекст
        /*... */ 
        return new Finish<>(receipt); 
    }
}

// Определение workflow в конфигурационном классе Spring
@Configuration
public class WorkflowConfiguration {
@Bean
public WorkflowGraph<Order, Receipt> paymentWorkflow(PaymentSteps paymentSteps) { // PaymentSteps внедряется DI-контейнером
return Workflow
.define("fluent-payment-workflow", Order.class, Receipt.class)
.then(StepDefinition.of(paymentSteps::validateOrder)) // ID будет "validateOrder"
.parallel(List.of(
StepDefinition.of(paymentSteps::chargeCreditCard), // ID будет "chargeCreditCard"
StepDefinition.of(paymentSteps::updateInventory)  // ID будет "updateInventory"
))
.then(StepDefinition.of(paymentSteps::generateReceipt)) // ID будет "generateReceipt"
.build();
}
}
2.3. Синергия и Взаимодействие Моделей
Истинная мощь гибридной модели раскрывается при совместном использовании обоих подходов. Разработчики могут создавать библиотеки атомарной, переиспользуемой бизнес-логики в виде @Workflow-классов, а затем собирать из этих "кирпичиков" сложные, специфичные для приложения "мега-workflow" с помощью Fluent API. Это способствует соблюдению принципа DRY (Don't Repeat Yourself) и разделению ответственности. Такой подход аналогичен тому, как mastra.ai позволяет использовать agents и tools в качестве шагов в своих workflow.   

Механизмы взаимодействия:

Импорт шагов: WorkflowBuilder будет предоставлять методы для импорта шагов, определенных через аннотации. Например: builder.then(StepDefinition.of(userRouterWorkflow::startOnboarding)).

Редактирование графа: Предоставляется возможность загрузить автоматически построенный граф в WorkflowBuilder для его дальнейшей модификации. Например: Workflow.edit("user-router-workflow").then(StepDefinition.of(loggingSteps::logNewUser)).build().

Таблица 2: Сравнение подходов к определению Workflow

Характеристика	Annotation-Driven (Автоматический)	Fluent API (Декларативный)
Краткость	Высокая. Минимум бойлерплейта для простых и древовидных структур.	Высокая. Не требует строковых ID, использует ссылки на методы.
Явность	Низкая. Связи между шагами неявны и определяются сигнатурами методов.	Высокая. Вся структура графа явно описана в коде построителя.
Гибкость	Средняя. Хорошо подходит для статических графов. Сложно реализовать циклы или динамическое изменение структуры.	Высокая. Позволяет программно строить графы любой сложности, включая циклы и динамическую композицию.
Порог входа	Низкий. Требуется знание только нескольких аннотаций.	Низкий. Требуется понимание API построителя, но без "магических строк".
Типобезопасность ветвления	Максимальная. Гарантируется компилятором через sealed интерфейсы.	Зависит от реализации. Требует аккуратного использования предикатов и типов.
Типичный сценарий	Простые чат-боты, обработчики событий, линейные процессы, классификаторы (RouterWorkflow).	Сложные оркестровки с параллелизмом, циклами, условной логикой, интеграция нескольких workflow (ComplexOnboardingWorkflow).

4. Собираем сложный workflow с помощью Fluent API без строковых ID:

Java

@Configuration
public class OnboardingWorkflowConfig {

    @Bean
    public WorkflowGraph<String, String> onboardingWorkflow(OnboardingSteps onboardingSteps) { // DI
        return Workflow
         .define("complex-onboarding", String.class, String.class)
            // Шаг 1: Приостановка для запроса имени (лямбда, ID нужен)
         .then(StepDefinition.of("ask-name", (initialTrigger) -> 
                new Suspend("Welcome to DriftKit! What is your name?", Map.of())))
            
            // Шаг 2: Асинхронная проверка в CRM (ID "checkCrm" выводится из имени метода)
         .then(StepDefinition.of(onboardingSteps::checkCrm))
            
            // Шаг 3: Ветвление в зависимости от результата проверки
         .branch(
                // Предикат: проверяем результат шага "checkCrm"
                (context) -> context.stepOutputs().get("checkCrm", CrmCheckResult.class).exists(),
                
                // Ветка "ИСТИНА" (пользователь существует)
                Workflow.define("existing-user-flow")
                 .then(StepDefinition.of("greet-existing", (CrmCheckResult res) -> 
                        new Finish<>("Welcome back, " + res.name() + "!")))
                 .buildAsSubWorkflow(),
                    
                // Ветка "ЛОЖЬ" (новый пользователь)
                Workflow.define("new-user-flow")
                 .parallel(List.of( // Шаг 4: Параллельное выполнение без строковых ID
                        StepDefinition.of(onboardingSteps::createAccount),
                        StepDefinition.of(onboardingSteps::sendWelcomeEmail)
                    ))
                 .then(StepDefinition.of("complete-onboarding", (context) -> {
                        // Получаем результат по имени метода, ставшему ID
                        UserAccount account = context.stepOutputs().get("createAccount", UserAccount.class);
                        return new Finish<>("Account for " + account.name() + " created and welcome email sent!");
                    }))
                 .buildAsSubWorkflow()
            )
         .build();
    }
}
Этот пример наглядно демонстрирует, как гибридный подход позволяет сочетать простоту аннотаций для определения атомарной логики и мощь Fluent API для оркестрации сложных, нелинейных процессов, полностью реализуя видение, изложенное в исходном запросе.
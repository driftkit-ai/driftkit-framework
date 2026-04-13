Техническое Задание: Рефакторинг и Унификация Движка Workflow в DriftKit Framework
Версия документа: 1.2
Дата: 24.05.2024
Статус: Проект (Обновлено)

Аннотация: Настоящий документ представляет собой исчерпывающее техническое задание (ТЗ) на проектирование и реализацию нового унифицированного движка workflow для фреймворка DriftKit. Целью данной инициативы является устранение архитектурной фрагментации между модулями driftkit-workflows и driftkit-chat-assistant-framework, повышение удобства разработки (Developer Experience) и внедрение современных, типобезопасных подходов к определению и выполнению сложных бизнес-процессов. Новый движок будет основан на гибридной модели, сочетающей декларативное определение графа через Fluent API, вдохновленное mastra.ai, и автоматическое построение графа на основе аннотаций и sealed интерфейсов Java.

Раздел 1: Концептуальное Ядро и Ключевые Абстракции Нового Движка Workflow
В основе нового движка лежит набор четко определенных, неизменяемых (immutable) и типобезопасных абстракций. Эти компоненты формируют фундамент, на котором будут построены как аннотационно-управляемый, так и Fluent API подходы к определению workflow. Корректное проектирование этого ядра является критическим фактором успеха всей инициативы.

1.1. Фундаментальные Компоненты Системы
Система оперирует следующими ключевыми понятиями:

WorkflowGraph<T, R>: Неизменяемый типизированный объект, представляющий собой полное определение (blueprint) workflow. Параметр T обозначает тип входных данных для всего workflow, а R — тип финального результата. WorkflowGraph является направленным ациклическим графом (DAG), состоящим из узлов (StepNode) и рёбер (Edge), которые описывают логику переходов между шагами. Эта структура является конечным продуктом как автоматического сканирования аннотаций, так и программного построения через Fluent API.

StepNode: Узел в WorkflowGraph. Каждый узел инкапсулирует метаданные одного шага, включая его уникальный идентификатор (id), описание, а также ссылку на исполняемую логику. Эта логика может быть представлена как java.lang.reflect.Method для аннотированного подхода или как функциональный интерфейс (например, java.util.function.Function) для подхода с Fluent API.

WorkflowContext: Неизменяемый объект-контейнер, который агрегирует всю информацию о текущем запуске workflow и передается от шага к шагу. Его неизменяемость гарантирует предсказуемость и потокобезопасность. Контекст доступен в каждом шаге workflow и содержит:

runId: Уникальный идентификатор конкретного запуска workflow.

triggerData: Исходные данные, с которыми был инициирован запуск.

stepOutputs: Неизменяемая карта Map<String, Object>, хранящая результаты всех уже выполненных шагов. Доступ к результатам осуществляется по id шага. Этот механизм, вдохновленный подходом mastra.ai (context.getStepResult()) , является ключевым, так как позволяет любому шагу получать доступ к состоянию не только своего непосредственного предшественника, но и любого другого ранее выполненного шага в графе.   

Параметры Шага: Входные данные для любого шага состоят из двух частей:

Данные от предыдущего шага: Объект, возвращенный предыдущим шагом (например, внутри Continue<T>).

WorkflowContext: Полный контекст выполнения, который может быть опционально добавлен в параметры метода для доступа к глобальному состоянию workflow. Движок автоматически определяет, нужен ли контекст, анализируя сигнатуру метода шага.

StepResult (Sealed Interface): Краеугольный камень новой архитектуры, предложенный в исходном запросе. StepResult — это sealed interface, который формализует и типизирует все возможные исходы выполнения одного шага. Использование sealed интерфейса переносит логику ветвления из нестабильных рантайм-проверок (например, анализ строковых флагов или if-else каскады) на уровень компиляции, делая поток управления явным, понятным и типобезопасным.

1.2. Управление Потоком через StepResult
Применение sealed interface для StepResult является мощным архитектурным паттерном. Оно преобразует неявную логику ветвления в явный, проверяемый компилятором контракт между разработчиком шага и движком workflow. Движок может использовать switch expression над типом StepResult для исчерпывающей обработки всех возможных исходов, что полностью устраняет путаницу и повышает читаемость кода.

Когда движок анализирует метод, он видит не просто некий объект в качестве возвращаемого значения, а конкретный sealed тип. Анализируя его permits клаузу, движок может автоматически обнаружить все возможные ветви исполнения, исходящие из данного шага (например, Continue, Suspend, BranchToPayment, BranchToVerification), и построить соответствующие рёбра в WorkflowGraph.

Базовый sealed interface и его реализации определяются следующим образом:

Java

public sealed interface StepResult<+R> permits Continue, Suspend, Branch, Finish, Fail {
// Marker interface
}

public record Continue<T>(T data) implements StepResult<Void> {}
public record Suspend(Object promptToUser, Map<String, Object> metadata) implements StepResult<Void> {}
public record Branch(Object event) implements StepResult<Void> {}
public record Finish<R>(R result) implements StepResult<R> {}
public record Fail(Throwable error) implements StepResult<Void> {}
Continue<T>(T data): Стандартный исход, означающий успешное завершение шага и продолжение выполнения. Поле data содержит результат, который будет передан на вход следующему шагу в графе.

Suspend(Object promptToUser, Map<String, Object> metadata): Приостановка выполнения workflow для реализации механизма Human-in-the-Loop (HITL). Это напрямую реализует одну из ключевых возможностей chat-framework  и аналогично функциональности    

suspend() в mastra.ai. Поле    

promptToUser содержит данные (например, текст вопроса, варианты ответов), которые должны быть отправлены конечному пользователю. metadata может содержать дополнительную информацию, например, о формате ожидаемого ответа.

Branch(Object event): Явное ветвление потока управления. Поле event представляет собой объект-событие (часто record), который используется движком для определения следующего шага. Движок будет искать шаг, который в качестве входного параметра принимает тип этого события.

Finish<R>(R result): Успешное завершение всего workflow. Поле result содержит финальный результат, который соответствует типу R в определении WorkflowGraph<T, R>.

Fail(Throwable error): Завершение workflow с ошибкой. Поле error содержит исключение, которое привело к сбою.

1.3. Контекст Выполнения (WorkflowInstance)
Если WorkflowGraph — это неизменяемый "чертеж", то WorkflowInstance — это изменяемый (mutable) объект, представляющий собой один конкретный запущенный процесс. Разделение этих двух понятий является критически важным для построения надежной системы, способной одновременно обрабатывать множество экземпляров одного и того же workflow. Эта модель упрощает управление состоянием и персистентность, так как для сохранения приостановленного workflow достаточно сериализовать только его WorkflowInstance. Данный подход обеспечивает продакшен-уровень стабильности, что является одним из ключевых преимуществ DriftKit.   

Объект WorkflowInstance управляется исключительно движком WorkflowEngine и содержит:

Ссылку на неизменяемый WorkflowGraph.

Текущий WorkflowContext (включая runId, triggerData и stepOutputs).

Текущий статус: RUNNING, SUSPENDED, FINISHED, FAILED.

Историю выполнения (List<StepExecutionRecord>), полезную для трассировки и отладки.

Таблица 1: Жизненный цикл StepResult и реакция WorkflowEngine

Тип StepResult	Передаваемые данные	Действие WorkflowEngine	Изменение статуса WorkflowInstance
Continue<T>	data: Результат шага типа T.	Находит следующий узел в графе, который принимает тип T. Обновляет WorkflowContext с результатом data. Вызывает следующий шаг.	RUNNING → RUNNING
Suspend	promptToUser: Объект для отправки пользователю. metadata: Дополнительные метаданные.	Сериализует WorkflowInstance и сохраняет его в WorkflowStateRepository. Возвращает promptToUser вызывающему коду (например, AssistantController).	RUNNING → SUSPENDED
Branch	event: Объект-событие.	Находит следующий узел в графе, который принимает тип event. Обновляет WorkflowContext. Вызывает найденный шаг.	RUNNING → RUNNING
Finish<R>	result: Финальный результат workflow типа R.	Завершает выполнение. Записывает финальный результат в WorkflowInstance. Удаляет инстанс из активных, но может сохранить в истории.	RUNNING → FINISHED
Fail	error: Исключение.	Завершает выполнение. Записывает ошибку в WorkflowInstance. Выполняет логику обработки ошибок (например, ретрай).	RUNNING → FAILED

Экспортировать в Таблицы
Раздел 2: Гибридная Модель Определения Графа Workflow
Новый движок предлагает два взаимодополняющих способа определения workflow, удовлетворяя потребности разных сценариев и предпочтений разработчиков. Эта гибридная модель является ключевым элементом рефакторинга.

2.1. Автоматическое Построение Графа (Annotation-Driven & Type-Safe)
Этот подход "соглашение вместо конфигурации" (convention-over-configuration) позволяет разработчику определять workflow в рамках одного Java-класса, минимизируя бойлерплейт. Фреймворк автоматически сканирует класс при запуске приложения, строит WorkflowGraph и регистрирует его в движке.

Ключевые аннотации:

@Workflow(id = "my-chat-workflow", version = "1.0"): Маркирует класс как определение workflow. id должен быть уникальным в системе.

@InitialStep: Маркирует метод, являющийся точкой входа в workflow. В каждом workflow может быть только один такой метод.

@Step(id = ""): Маркирует обычный метод как шаг workflow. id является опциональным. Если он не указан, в качестве ID шага будет использоваться имя метода. Это упрощает написание кода и уменьшает количество "магических строк".

Механизм автоматического построения графа:

При запуске WorkflowEngine сканирует classpath на наличие классов, аннотированных @Workflow.

Для каждого такого класса движок находит метод с аннотацией @InitialStep. Этот метод становится корневым узлом графа.

Далее движок рекурсивно анализирует возвращаемые типы (StepResult) каждого @Step-метода, чтобы построить рёбра графа:

Если метод возвращает Continue<T>, движок ищет другой @Step, который принимает на вход объект типа T. Найдя такой метод, он создает ребро между двумя шагами.

Если метод возвращает Branch(MyEvent event), движок ищет @Step, принимающий на вход MyEvent.

Ключевое усовершенствование: Если метод возвращает sealed interface MyEvents, движок через рефлексию получает список всех разрешенных подтипов (getPermittedSubclasses()). Для каждого подтипа он ищет соответствующий @Step, который принимает этот подтип в качестве аргумента. Таким образом, движок автоматически строит ветвление, полностью основанное на статической типизации Java.

Интеграция с Dependency Injection (DI):

Это ключевое улучшение для обеспечения слабой связности и тестируемости.

Классы Workflow как компоненты: Классы, аннотированные @Workflow, должны рассматриваться как компоненты (например, Spring Beans). Разработчики могут использовать стандартные DI-аннотации (@Autowired, @Inject) для внедрения зависимостей (сервисов, репозиториев) через конструктор.

Жизненный цикл: WorkflowEngine будет интегрирован с DI-контейнером приложения. При необходимости выполнить шаг, движок не будет создавать новый экземпляр @Workflow-класса, а запросит уже сконфигурированный bean из DI-контейнера. Это гарантирует, что все зависимости будут корректно внедрены.

Методы шагов: Методы, аннотированные @Step, должны быть нестатическими (instance methods), чтобы иметь доступ к внедренным зависимостям.

Пример реализации RouterWorkflow с учетом DI и автоматических ID:

Java

// События определяют возможные ветви исполнения
public sealed interface UserClassificationResult permits KnownUser, NewUser, SuspendedQuery {}
public record KnownUser(User user) implements UserClassificationResult {}
public record NewUser(String email) implements UserClassificationResult {}
public record SuspendedQuery(String queryId) implements UserClassificationResult {}

@Workflow(id = "user-router-workflow")
@Component // Пример для Spring Framework
public class UserRouterWorkflow {

    private final UserService userService;

    // Конструктор для DI
    @Autowired
    public UserRouterWorkflow(UserService userService) {
        this.userService = userService;
    }

    @InitialStep
    public StepResult<UserClassificationResult> classifyUser(String email) {
        var user = userService.findByEmail(email);
        if (user.isPresent()) {
            return new Continue<>(new KnownUser(user.get()));
        } else {
            return new Continue<>(new NewUser(email));
        }
    }

    // ID шага будет "handleKnownUser" по имени метода
    @Step
    public StepResult<Void> handleKnownUser(KnownUser event, WorkflowContext context) {
        // Логика для известного пользователя, с доступом к полному контексту
        System.out.println("Handling known user for runId: " + context.runId());
        return new Finish<>("Welcome back, " + event.user().getName());
    }

    // ID шага будет "startOnboarding"
    @Step
    public StepResult<Void> startOnboarding(NewUser event) {
        // Логика для нового пользователя (контекст не требуется)
        return new Suspend("Hello! What is your name?", Map.of("email", event.email()));
    }
}



Экспортировать в Таблицы
Раздел 3: Продвинутое Управление Потоком и Выполнением
Новый движок должен быть не просто фреймворком для определения графов, а мощной средой выполнения (runtime), поддерживающей сложные сценарии, такие как Human-in-the-Loop и асинхронные операции.

3.1. Реализация Human-in-the-Loop (HITL)
Эта функциональность является центральной для chat-framework и позиционируется как одно из ключевых преимуществ DriftKit в целом ("human-in-the-loop for critical decisions").   

AssistantController (или аналогичный компонент на фронтенде) будет основным потребителем этого механизма.

Жизненный цикл приостановки и возобновления:

AssistantController получает запрос от пользователя (например, сообщение в чате).

Он вызывает WorkflowEngine.execute(runId, userInput). Если runId не предоставлен, создается новый WorkflowInstance. Если предоставлен, загружается существующий.

WorkflowEngine последовательно выполняет шаги workflow.

Один из шагов возвращает StepResult.Suspend(prompt, metadata).

WorkflowEngine переводит WorkflowInstance в статус SUSPENDED, сохраняет его полное состояние (включая WorkflowContext с результатами всех предыдущих шагов) через WorkflowStateRepository и возвращает объект prompt в AssistantController.

AssistantController отправляет prompt пользователю и завершает обработку текущего запроса.

Когда от пользователя приходит ответ, AssistantController снова вызывает WorkflowEngine.execute(runId, newResponse), передавая тот же runId и новый ввод от пользователя.

Движок загружает WorkflowInstance из репозитория, переводит его в статус RUNNING и продолжает выполнение с шага, следующего за тем, который был приостановлен.

3.2. Асинхронное Выполнение Шагов
Поддержка асинхронных операций (@AsyncStep) критически важна для эффективной работы с I/O-bound задачами (вызовы LLM, запросы к внешним API, операции с базами данных), так как она позволяет не блокировать потоки исполнителя (ThreadPool) движка.

Реализация:

Метод, который должен выполняться асинхронно, помечается аннотацией @AsyncStep (или определяется как асинхронный в Fluent API). ID шага также будет по умолчанию браться из имени метода.

Такой метод должен возвращать CompletableFuture<StepResult>.

Когда WorkflowEngine доходит до такого шага, он не блокирует свой рабочий поток в ожидании результата. Вместо этого он вызывает асинхронный метод и регистрирует коллбэки на возвращенном CompletableFuture.

Движок может продолжать обрабатывать другие workflow или другие шаги в том же workflow (если они параллельны).

Когда CompletableFuture завершается (успешно или с ошибкой), его результат (StepResult) помещается во внутреннюю очередь WorkflowEngine для дальнейшей обработки. Рабочий поток подхватывает этот результат и продолжает выполнение графа с того места, где оно было приостановлено в ожидании асинхронной операции.

3.3. Визуализация и Интроспекция Графа
Возможность визуализировать граф workflow является мощным инструментом для отладки, документирования и анализа бизнес-процессов. Это требование напрямую поддерживает маркетинговое позиционирование DriftKit: "Workflow as maintainable graph".   

API для экспорта графа:

На объекте WorkflowGraph будет определен метод export(GraphFormat format).

GraphFormat будет перечислением (enum) с как минимум двумя вариантами:

DOT: Формат для утилиты Graphviz, стандарт де-факто для рендеринга графов. Позволяет легко генерировать изображения (PNG, SVG) структуры workflow.

JSON: Структурированный формат, который может быть использован для построения кастомных интерактивных визуализаций на фронтенде с помощью библиотек типа react-flow или d3.js. Это открывает путь к созданию в будущем полноценного визуального редактора workflow, аналогичного тому, что заявлен для модуля DriftKit Context Engineering.   

Реализация экспортера будет итерировать по StepNode и Edge в WorkflowGraph и генерировать соответствующее текстовое представление, включая id шагов, их описания и типы переходов.

Раздел 4: План Реализации и Миграции
Этот раздел содержит конкретные, пошаговые инструкции для команды разработки по внедрению нового движка и миграции существующего кода.

4.1. Новая Структура Модулей
Предлагается следующая реструктуризация модулей, связанных с workflow:

driftkit-workflow-engine (Новый модуль):

Назначение: Ядро нового унифицированного движка.

Содержимое: Все ключевые абстракции (StepResult, WorkflowGraph, WorkflowContext, WorkflowInstance), основной класс WorkflowEngine, построитель WorkflowBuilder, обработчики аннотаций (@Workflow, @Step и т.д.), интерфейс персистентности WorkflowStateRepository. Этот модуль не будет иметь зависимостей от конкретных бизнес-логик или чат-фреймворков.

driftkit-workflows (Рефакторинг):

Назначение: Библиотека стандартных, переиспользуемых workflow и шагов общего назначения.

Изменения: Существующая логика движка будет помечена как @Deprecated с планом удаления в следующей мажорной версии. Модуль будет переведен на использование driftkit-workflow-engine. Он станет поставщиком готовых шаблонов, таких как RAGModifyWorkflow, RAGSearchWorkflow, ReasoningWorkflow , реализованных на базе нового движка.   

driftkit-chat-assistant-framework (Рефакторинг):

Назначение: Слой интеграции workflow с чат-интерфейсами.

Изменения: Модуль будет значительно облегчен. Его собственный механизм управления диалогами будет полностью заменен на использование driftkit-workflow-engine. Он сохранит за собой классы, отвечающие за взаимодействие с пользователем (AssistantController), управление сессиями и каналами связи, но вся логика исполнения состояний диалога будет делегирована новому движку.

4.2. Спецификации API (Java Interfaces & Records)
Ниже приведены определения ключевых публичных API нового движка. Это "контракт", который должен быть реализован.

Java

// --- Core Annotations ---
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Workflow {
String id();
String version() default "1.0";
}

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface InitialStep {}

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Step {
// ID опционален. Если не указан, используется имя метода.
String value() default "";
String description() default "";
}

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AsyncStep {
// ID опционален. Если не указан, используется имя метода.
String value() default "";
String description() default "";
}

// --- Core Abstractions ---
public sealed interface StepResult<+R> permits Continue, Suspend, Branch, Finish, Fail { }
public record Continue<T>(T data) implements StepResult<Void> { }
public record Suspend(Object promptToUser, Map<String, Object> metadata) implements StepResult<Void> { }
public record Branch(Object event) implements StepResult<Void> { }
public record Finish<R>(R result) implements StepResult<R> { }
public record Fail(Throwable error) implements StepResult<Void> { }

public record WorkflowContext(String runId, Object triggerData, Map<String, Object> stepOutputs) { }

// --- Engine and Persistence Interfaces ---
public interface WorkflowEngine {
<T, R> WorkflowExecution<R> execute(String workflowId, T input);
<T, R> WorkflowExecution<R> execute(String runId, T input);
void register(WorkflowGraph<?,?> graph);
}

public interface WorkflowStateRepository {
void save(WorkflowInstance instance);
Optional<WorkflowInstance> load(String runId);
void delete(String runId);
}
4.3. Требования к WorkflowEngine
Интеграция с DI: Движок должен иметь точку расширения для интеграции с DI-контейнерами (например, Spring ApplicationContext). При вызове шага он должен получать экземпляр @Workflow-класса из контейнера, а не создавать его самостоятельно.

Регистрация: Движок при старте должен предоставлять механизм для обнаружения и регистрации workflow. Это включает сканирование classpath на наличие @Workflow-классов и возможность программной регистрации графов, созданных через WorkflowBuilder.

Выполнение: Основной метод execute должен быть потокобезопасным и управлять полным жизненным циклом WorkflowInstance — от создания и выполнения до приостановки, возобновления и завершения.

Персистентность: Движок должен работать через абстрактный WorkflowStateRepository. Необходимо предоставить как минимум две реализации: InMemoryStateRepository для тестов и простых случаев, и JdbcStateRepository для продакшен-использования (по аналогии с бэкендами в DriftKit Vector ).   

Валидация: В момент регистрации графа движок обязан проводить его валидацию: проверять на наличие висячих узлов (шагов, на которые нет переходов), на отсутствие обработчиков для всех ветвей sealed интерфейса, на уникальность id шагов и т.д.

Таблица 3: План миграции ключевых компонентов

Компонент / Функциональность	Исходный Модуль	Целевой Модуль	Действие
Логика исполнения Workflow	driftkit-workflows, driftkit-chat-assistant-framework	driftkit-workflow-engine	Заменить. Полностью заменить на новый WorkflowEngine.
Аннотации @Step и др.	driftkit-workflows	driftkit-workflow-engine	Перенести и расширить. Перенести, добавить @Workflow, @InitialStep, адаптировать под StepResult и опциональный ID.
RouterWorkflow	driftkit-workflows-examples	driftkit-workflows-examples	Рефакторинг. Переписать с использованием @Workflow, sealed interface, DI и автоматических ID.
ChatWorkflow	driftkit-workflows-examples	driftkit-workflows-examples	Рефакторинг. Переписать с использованием нового движка, активно используя StepResult.Suspend для HITL.
Логика @AsyncStep	driftkit-chat-assistant-framework (предположительно)	driftkit-workflow-engine	Унифицировать. Реализовать поддержку CompletableFuture<StepResult> как часть ядра движка.
Управление состоянием HITL	driftkit-chat-assistant-framework	driftkit-workflow-engine	Абстрагировать. Реализовать через WorkflowStateRepository и StepResult.Suspend.
AssistantController	driftkit-chat-assistant-framework	driftkit-chat-assistant-framework	Адаптировать. Изменить для взаимодействия с новым WorkflowEngine вместо старой логики диалогов.

Экспортировать в Таблицы
Раздел 5: Эталонные Реализации и Примеры ("До" и "После")
Этот раздел демонстрирует практическое применение новой архитектуры на конкретных примерах, показывая преимущества в читаемости, надежности и гибкости.

5.1. Рефакторинг RouterWorkflow
RouterWorkflow  — это классический пример задачи классификации и маршрутизации.   

"До" (Предполагаемая реализация):
Текущая реализация, вероятно, использует строковые флаги или множественные if-else конструкции для определения следующего шага, что делает код громоздким и склонным к ошибкам.

Java

// Гипотетический код "ДО"
public class OldRouterWorkflow {
public String route(String input) {
if (input.contains("order")) {
return "handle_order";
} else if (input.contains("support")) {
return "handle_support";
}
return "fallback";
}
//... далее ручная обработка строкового результата
}
"После" (Реализация на новом движке):
Новая версия использует sealed interface для декларативного описания всех возможных маршрутов. Граф строится автоматически, ID шагов выводятся из имен методов, а код становится самодокументируемым и легко интегрируется с DI.

Java

// 1. Определяем возможные маршруты как sealed interface
public sealed interface RoutingDecision permits OrderQuery, SupportQuery, GeneralQuery {}
public record OrderQuery(String orderId) implements RoutingDecision {}
public record SupportQuery(String details) implements RoutingDecision {}
public record GeneralQuery(String text) implements RoutingDecision {}

// 2. Определяем workflow как Spring-компонент
@Workflow(id = "main-router")
@Component
public class NewRouterWorkflow {

    @InitialStep
    public StepResult<RoutingDecision> route(String input) {
        if (input.startsWith("order:")) {
            return new Continue<>(new OrderQuery(input.substring(6)));
        } else if (input.startsWith("support:")) {
            return new Continue<>(new SupportQuery(input.substring(8)));
        } else {
            return new Continue<>(new GeneralQuery(input));
        }
    }

    @Step // ID = "processOrder"
    public StepResult<Void> processOrder(OrderQuery query, WorkflowContext context) {
        // Логика обработки заказа с доступом к контексту
        System.out.println("Processing order for runId: " + context.runId());
        return new Finish<>("Order processed.");
    }

    @Step // ID = "createSupportTicket"
    public StepResult<Void> createSupportTicket(SupportQuery query) {
        // Логика создания тикета (контекст не нужен)
        System.out.println("Creating support ticket for: " + query.details());
        return new Finish<>("Support ticket created.");
    }
    
    @Step // ID = "fallback"
    public StepResult<Void> fallback(GeneralQuery query) {
        // Логика для общих вопросов
        return new Suspend("I'm not sure how to help with that. Could you rephrase?", Map.of());
    }
}
5.2. Реализация ComplexOnboardingWorkflow
Этот пример демонстрирует синергию Fluent API и аннотированных шагов для создания сложного процесса с HITL, асинхронностью и параллелизмом, без использования строковых идентификаторов.

Сценарий: Онбординг нового пользователя.

Поприветствовать и спросить имя (HITL).

Асинхронно проверить в CRM, существует ли пользователь.

Ветвление: разная логика для новых и существующих пользователей.

Для новых: параллельно создать аккаунт и отправить приветственный email.

Завершить.

1. Определяем атомарные шаги как Spring-компонент:

Java

// Этот класс содержит переиспользуемую бизнес-логику и управляется Spring
@Component
public class OnboardingSteps {
private final CrmService crmService;
private final MailService mailService;

    @Autowired
    public OnboardingSteps(CrmService crmService, MailService mailService) {
        this.crmService = crmService;
        this.mailService = mailService;
    }

    @AsyncStep // ID будет "checkCrm"
    public CompletableFuture<StepResult<CrmCheckResult>> checkCrm(String name) {
        return crmService.userExistsAsync(name)
         .thenApply(exists -> new Continue<>(new CrmCheckResult(name, exists)));
    }

    @Step // ID будет "createAccount"
    public StepResult<UserAccount> createAccount(String name) {
        // синхронная логика создания аккаунта
        return new Continue<>(new UserAccount(name));
    }

    @Step // ID будет "sendWelcomeEmail"
    public StepResult<Void> sendWelcomeEmail(String name) {
        mailService.sendWelcome(name);
        return new Continue<>(null);
    }
}

// Вспомогательные рекорды
public record CrmCheckResult(String name, boolean exists) {}
public record UserAccount(String name) {}
2. Собираем сложный workflow с помощью Fluent API без строковых ID:

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
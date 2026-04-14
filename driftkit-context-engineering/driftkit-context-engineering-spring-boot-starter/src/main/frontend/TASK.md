# Code Review: Context Engineering Platform

**Branch**: `feature/deepseek-unified-caching`  
**Date**: 2026-04-14  
**Scope**: All Java + Vue/TS changes in `driftkit-context-engineering`

---

## 🔴 Critical Priority

- [x] **[XSS]** `ChatPage.vue` — `formatMarkdown()` using `marked.parse()` without sanitization. Fixed: wrapped with `DOMPurify.sanitize()`. Also escaped HTML in JSON `formatMessage()`
- [x] **[XSS]** `TracesPage.vue:88`, `TestSetsTab.vue`, `RunResultsView.vue` — `v-html="highlightVariables(...)"`. **Not a real XSS**: `highlightVariables()` in `formatting.ts` already escapes `&`, `<`, `>` before adding spans. Safe.
- [x] **[THREAD-SAFETY]** `EvaluationService.java` — `static SimpleDateFormat` replaced with `DateTimeFormatter` (thread-safe)
- [x] **[BUG]** `EvaluationService.java` — Raw `((Map) details).putAll(...)` replaced with `instanceof Map<?,?>` check + proper generic cast

## 🟠 High Priority

- [x] **[SECURITY]** `PromptRestController.java` — Added `import Base64`, `import StandardCharsets`, replaced inline `java.util.Base64` with proper import + `StandardCharsets.UTF_8`
- [x] **[MEMORY-LEAK]** `ChatPage.vue` — Added `activePollingIntervals` tracking, `onBeforeUnmount` cleanup, and `.catch()` on poll errors
- [x] **[MEMORY-LEAK]** `PlaygroundPage.vue` — Added `activeIntervals` tracking, `onBeforeUnmount` cleanup, and error handling in poll intervals
- [x] **[MEMORY-LEAK]** `testRunMethods.ts` — Already had `cleanupPolling()` exported, `PromptsPage.vue` already calls it in `onBeforeUnmount`
- [x] **[MEMORY-LEAK]** `AllRunsView.vue` — Already had `onBeforeUnmount` with `clearInterval()`. No fix needed
- [x] **[INLINE-PKG]** `PromptRestController.java` — Added proper imports for `Base64` and `StandardCharsets`
- [x] **[INLINE-PKG]** `TestSetService.java` — Replaced `.collect(java.util.stream.Collectors.toList())` with `.toList()` (Java 16+)
- [x] **[NPE]** `PromptRestController.java` — Added null/blank check before `ids.split(",")` with `ResponseEntity.badRequest()`
- [x] **[SILENT-FAIL]** `PromptRestController.java` — Empty catch → `log.warn("Failed to extract user from auth header", e)`
- [x] **[SILENT-FAIL]** `PipelineTestService.java` — Added `log.warn("Pipeline test run not found: {}", runId)`
- [x] **[ERROR-HANDLING]** `ChatPage.vue:187` — Added `.catch()` on `createChat` axios call
- [x] **[ERROR-HANDLING]** `ChatPage.vue:315,317` — Empty catch blocks → `console.error('Clipboard write failed:', err)`
- [x] **[ERROR-HANDLING]** `PlaygroundPage.vue:241` — Empty catch → clarifying comment
- [x] **[UX]** All `alert()` calls replaced:
  - `AdminLayout.vue` — 6 `alert()` → `toast.add()` with PrimeVue Toast
  - `PromptsPage.vue` — 2 `alert()` → `toast.add()` with PrimeVue Toast
  - `useAuth.ts` — `alert()` → `loginError` ref shown as `<Message>` in LoginPage
  - `testRunMethods.ts` — `alert()` → `console.warn()`
  - `ChatPage.vue` — `alert()` → `console.error()`
  - `main.ts` — Registered `ToastService` plugin
- [x] **[RACE]** `ChatPage.vue` — Added `AbortController` to `fetchMessages()`. Previous request is aborted when new chat is selected

## 🟡 Medium Priority

### Backend
- [ ] **[TYPE-SAFETY]** `TestSetService.java:52,87,217` — `.map(TestSetItem.class::cast)` without instanceof check
- [ ] **[THREAD-SAFETY]** `PromptEnvironmentResolver.java:26-27` — Race between null check and dereference
- [ ] **[RESOURCE]** `EvaluationService.java:665-668` — `StringWriter`/`PrintWriter` never closed
- [ ] **[AUDIT]** `PromptRestController.java` — Audit calls pass `null` for `performedBy`
- [ ] **[MAGIC]** `EvaluationService.java` — Hardcoded `"qa_test_pipeline"` and date format strings

### Frontend
- [ ] **[TYPE-SAFETY]** Multiple files use `any` types — TracesPage, PromptsPage, PlaygroundPage, PipelinesPage, ChatPage
- [ ] **[REACTIVITY]** `TracesPage.vue:283-291` — `setTimeout(0)` hack in `regroupTraces()`

## 🟢 Low Priority

- [ ] **[CLEANUP]** `PromptsPage.vue:115-116` — No-op `emit` function
- [ ] **[DEBUG]** `RunResultsView.vue:374,377` — `console.log()` left in production
- [ ] **[CONFIG]** Hardcoded API paths and poll intervals
- [ ] **[DOCS]** New REST endpoints missing OpenAPI annotations
- [ ] **[REFACTOR]** `Prompt.java` 19-param constructor → `@Builder`

---

## Previously Fixed (This Branch)

- [x] `ClaudeModelClient.buildClaudeRequest()` streaming missing `applyCachePolicy()`
- [x] `LLMAgent` only passed `cachePolicy` in `executeText()`, 8 other paths missed
- [x] `DeepSeekModelClient.isThinkingEnabled()` returned true for medium
- [x] `ClaudeModelClient.applyCachePolicy(AUTO)` breakpoints on system + text
- [x] `MongodbPromptService.savePrompt()` — race condition on version increment (synchronized)
- [x] `PipelineTestService.executeRun()` — PASSED bug (always returned PASSED)
- [x] `LLMAgent.workflowId/workflowStep` — volatile + synchronized setter
- [x] `EvaluationService.testExecutor` — @PreDestroy shutdown
- [x] `PipelineTestService.executor` — @PreDestroy shutdown
- [x] `RegressionDetectionService.waitForCompletion()` — CompletableFuture.get(timeout)
- [x] `PromptEnvironmentResolver.resolver` — AtomicReference
- [x] `PromptServiceBase` — extracted resolvePrompt() helper
- [x] `ClaudeMessageRequest.getSystemAsString()` — @JsonIgnore
- [x] `ClaudeModelClient.processPrompt()` — preserve cache_control on system messages
- [x] `appConfig.ts` — deleted (hardcoded production IP)
- [x] `AdminLayout.vue` — menuItems.value.find() fix
- [x] Vite proxy path rewrite stripping `/data` prefix
- [x] DeepSeek registered as "openai" → fixed to "deepseek" via SPI

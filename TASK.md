# Code Review: Context Engineering Platform

## Review Summary

Branch `feature/context-engineering-platform` — 9 commits, 14 phases:
- Frontend: Vue CLI → Vite + PrimeVue + sidebar layout
- Prompt lifecycle: DRAFT → AUTO_TESTING → MANUAL_TESTING → CURRENT → REPLACED
- Pipeline registry + test runner with prompt overrides
- Agent hierarchical tracing, environments, playground, cost tracking, audit, regression detection
- 80 tests covering all new business logic

---

## Previous Review (DeepSeek & Caching) — Completed

- [x] `ClaudeModelClient.buildClaudeRequest()` streaming missing `applyCachePolicy()`
- [x] `LLMAgent` only passed `cachePolicy` in `executeText()`, 8 other paths missed it
- [x] `DeepSeekModelClient.isThinkingEnabled()` returned true for medium
- [x] `ClaudeModelClient.applyCachePolicy(AUTO)` breakpoints on both system + text

---

## 🔴 Critical Priority

- [ ] **[CONCURRENCY]** `MongodbPromptService.savePrompt()` — race between read (line 76) and write (line 91). Two threads saving same method+language can both read CURRENT, create duplicate versions. Fix: MongoDB `findAndModify` with optimistic version locking.

- [ ] **[BUG]** `PipelineTestService.executeRun()` lines 133-138 — both `if` and `else` branches set `status = "PASSED"`. Expected output is never compared to actual result. Test runner always reports pass.

- [ ] **[BUG]** `RegressionDetectionService` line 68 — `lastRun.getPassedCases() / lastRun.getTotalCases()` division by zero when `getTotalCases() == 0`.

## 🟠 High Priority

- [ ] **[CONCURRENCY]** `LLMAgent.workflowId/workflowStep` — made non-final for SequentialAgent context injection. If agent is reused across threads, concurrent `setWorkflowContext()` causes data race. Consider ThreadLocal approach.

- [ ] **[RESOURCE]** `EvaluationService.testExecutor` — `ExecutorService` never shutdown. Add `@PreDestroy` method.

- [ ] **[RESOURCE]** `PipelineTestService.executor` — same, no shutdown hook.

- [ ] **[BLOCKING]** `RegressionDetectionService.waitForCompletion()` — `Thread.sleep(5000)` in loop blocks Spring scheduler thread up to 5 minutes. Use `CompletableFuture` with timeout.

- [ ] **[UX]** `PromptsPage.vue` `publishPrompt()` — no confirmation dialog before publishing to production.

- [ ] **[PERFORMANCE]** `PromptRestController.renameFolder()` — loads ALL prompts, filters and saves one by one. Use MongoDB bulk update.

- [ ] **[PERFORMANCE]** `PromptServiceBase` environment fallback — `getPromptsByMethods()` fetches all versions then filters. Add `findByMethodAndLanguageAndVersion()` query.

## 🟡 Medium Priority

- [ ] **[CONCURRENCY]** `PromptEnvironmentResolver.resolver` — use `AtomicReference<VersionResolver>` instead of `volatile`.

- [ ] **[DUPLICATION]** `PromptServiceBase` — override+environment+CURRENT fallback duplicated in both `getCurrentPromptOrThrow()` and `getCurrentPrompt()`. Extract to private helper.

- [ ] **[AUDIT]** `PromptRestController` audit calls pass `null` for `performedBy`. Extract user identity from request context.

- [ ] **[UX]** `PlaygroundPage.vue` — JSON parse error in variables silently swallowed (line 102). Show validation message.

- [ ] **[UX]** `AdminLayout.vue` — `.catch(() => {})` swallows errors in modal actions.

- [ ] **[TESTING]** `DeepSeekModelClient` — unit tests still missing (from previous review).

- [ ] **[TESTING]** `ClaudeModelClient.applyCachePolicy()` — unit tests still missing.

## 🟢 Low Priority

- [ ] **[CLEANUP]** `Prompt.java` `@AllArgsConstructor` creates 19-param constructor. Consider `@Builder` for callsites.

- [ ] **[STYLE]** `PromptServiceBase` uses `==` for Language enum comparison. Works but `.equals()` is conventional.

- [ ] **[DOCS]** New REST endpoints have no OpenAPI/Swagger annotations.

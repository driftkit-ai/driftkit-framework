# Code Review: DeepSeek & Unified Caching Feature

## Review Summary

Feature branch `feature/deepseek-unified-caching` adds:
- 3 new core domain classes (CacheControl, CachePolicy, CacheUsage)
- New `driftkit-clients-deepseek` module with thinking/reasoning and cache metrics
- Claude cache_control support with AUTO placement
- OpenAI cache metrics mapping (prompt_tokens_details.cached_tokens)
- LLMAgent SDK integration with cachePolicy, reasoningContent, cacheUsage in AgentResponse

## Fixed during review (round 1)

- [x] **[BUG]** `ClaudeModelClient.buildClaudeRequest()` (streaming path) was missing `applyCachePolicy()` — fixed
- [x] **[BUG]** `LLMAgent` only passed `cachePolicy` in `executeText()`, 8 other request builders missed it — fixed, all 9 now pass it
- [x] **[CLEANUP]** Unused `StringUtils` import in `DeepSeekModelClient` — removed

## Fixed during review (round 2)

- [x] **[BUG-RISK]** `DeepSeekModelClient.isThinkingEnabled()` returned `true` for `ReasoningEffort.medium` (default) — now only `high`/`dynamic` or explicit `deepseek-reasoner` model enables thinking
- [x] **[ROBUSTNESS]** `DeepSeekModelClient.processStreamingPrompt()` — added HTTP status code checking with error body logging, matching OpenAI/Claude patterns
- [x] **[FEATURE]** `ClaudeModelClient.applyCachePolicy(AUTO)` — now places breakpoints on BOTH system message AND last large text block independently (was only placing one)
- [x] **[CONSISTENCY]** `ClaudeModelClient.applyCacheControlFromElement()` — now checks `CacheType.EPHEMERAL` explicitly; `CacheType.AUTO` is ignored for Claude since it requires explicit breakpoints
- [x] **[DESIGN]** `ClaudeMessageRequest.system` — added `@JsonDeserialize(using = SystemDeserializer.class)` to handle both String and List<ClaudeContent> on deserialization
- [x] **[CLEANUP]** `CompletableFuture` import in DeepSeekModelClient — verified it IS used (anonymous class in streamTextToText). No issue.
- [x] **[DOCS]** Updated `CLAUDE.md` — added DeepSeek module, cache control, and reasoning integration points
- [x] **[CLEANUP]** Verified `ChatCompletionResponse.OpenAIUsage` type change — only used internally in `OpenAIModelClient`. No external callers affected.

## 🟠 High Priority (remaining)

- [ ] **[TESTING]** Add unit tests for `DeepSeekModelClient` — at minimum: `buildRequest()` with thinking enabled/disabled, `mapUsage()` with cache metrics, `mapChoice()` with reasoning_content
- [ ] **[TESTING]** Add unit tests for `ClaudeModelClient.applyCachePolicy()` — AUTO mode with system prompt >1024 tokens, MANUAL mode with explicit markers, NONE mode no-op
- [ ] **[TESTING]** Add unit tests for `CacheUsage.getHitRatio()` — edge cases: nulls, zero totals, normal ratios
- [ ] **[TESTING]** Add unit tests for OpenAI `mapToModelTextResponse()` with new `OpenAIUsage.PromptTokensDetails` and `CompletionTokensDetails`

# ChatStore Implementation Plan - Aggressive Simplification

## Overview
Replace 8+ overlapping classes with single ChatStore. Each phase removes at least one old abstraction.

## Phase 1: Create ChatStore Component

### Goal
Create core ChatStore implementation as foundation.

### Tasks:
1. Create `ChatStore` interface
2. Create `DefaultChatStore` implementation with:
   - Storage methods: `add()`, `get()`, `delete()`
   - Memory management: `getRecentWithinTokens()`, `pruneIfNeeded()`
   - API conversions: `toModelMessages()`
3. Create comprehensive tests

### Files to create:
```
- ai/driftkit/workflow/engine/chat/ChatStore.java
- ai/driftkit/workflow/engine/chat/DefaultChatStore.java
- ai/driftkit/workflow/engine/chat/MessageType.java (if not exists)
- test/.../chat/ChatStoreTest.java
```

### Validation:
- `mvn test -pl driftkit-workflows/driftkit-workflow-engine-core`
- All existing tests pass

## Phase 2: Replace ChatMemory in LLMAgent

### Goal
Remove ChatMemory abstraction entirely from agents.

### REMOVES:
- ❌ ChatMemory usage in LLMAgent
- ❌ ChatMemoryStore interface usage
- ❌ TokenWindowChatMemory

### Tasks:
1. Modify `LLMAgent` to use ChatStore directly:
   ```java
   private final ChatStore chatStore;
   private final String chatId;
   ```
2. Replace all ChatMemory operations with direct ChatStore calls
3. Remove ChatMemory from agent builders
4. Update all agent tests

### Files to modify:
```
- ai/driftkit/agents/llm/LLMAgent.java
- ai/driftkit/agents/llm/LLMAgentBuilder.java
- test/.../agents/llm/LLMAgentTest.java
```

### Validation:
- Agent tests pass with new implementation
- No references to ChatMemory in agent code

## Phase 3: Add Auto-tracking to WorkflowEngine

### Goal
Make WorkflowEngine automatically save all chat messages.

### REMOVES:
- ❌ Manual chat saving in workflows
- ❌ Need for ChatContextHelper in most cases

### Tasks:
1. Add ChatStore to WorkflowOrchestrator:
   ```java
   private final ChatStore chatStore;
   ```
2. Override `processStepResult()` to auto-save:
   - Suspend messages
   - Async immediate data
   - Finish results
3. Override `resume()` to auto-save user input
4. Update WorkflowEngineConfig to accept ChatStore

### Files to modify:
```
- ai/driftkit/workflow/engine/core/WorkflowOrchestrator.java
- ai/driftkit/workflow/engine/core/WorkflowEngine.java
- ai/driftkit/workflow/engine/domain/WorkflowEngineConfig.java
```

### Validation:
- ChatWorkflowExampleTest passes
- New test verifies auto-tracking

## Phase 4: Replace ChatHistoryMemoryStoreAdapter

### Goal
Remove the complex adapter pattern.

### REMOVES:
- ❌ ChatHistoryMemoryStoreAdapter
- ❌ Conversion complexity between ChatHistory and ChatMemory

### Tasks:
1. Delete `ChatHistoryMemoryStoreAdapter`
2. Update any code using the adapter to use ChatStore directly
3. Simplify workflow examples that used adapter

### Files to delete:
```
- ai/driftkit/common/adapters/ChatHistoryMemoryStoreAdapter.java
```

### Validation:
- No compilation errors
- Tests using adapter are updated to use ChatStore

## Phase 5: Simplify MemoryManagementService

### Goal
Make MemoryManagementService a thin wrapper around ChatStore.

### REMOVES:
- ❌ Complex coordination logic
- ❌ Duplicate storage operations

### Tasks:
1. Replace internal implementation with ChatStore:
   ```java
   public class MemoryManagementService {
       private final ChatStore chatStore;
       
       public void storeChatRequest(ChatRequest request) {
           chatStore.add(request.getChatId(), request.getPropertiesMap(), MessageType.USER);
       }
   }
   ```
2. Remove ChatMemory management from service
3. Simplify all methods to delegate to ChatStore

### Files to modify:
```
- ai/driftkit/workflow/engine/persistence/MemoryManagementService.java
- ai/driftkit/workflow/engine/persistence/inmemory/InMemoryMemoryManagementService.java
```

### Validation:
- DefaultWorkflowExecutionService tests pass
- Service methods work correctly

## Phase 6: Unify Message Types

### Goal
Simplify message type conversions.

### REMOVES:
- ❌ Complex Message entity (15+ fields)
- ❌ Inefficient JSON conversions

### Tasks:
1. Update code using `Message` to use `ChatMessage` directly
2. Remove unnecessary conversions
3. Simplify `ModelContentMessage` creation

### Files to modify:
```
- Various files using Message entity
- Conversion utilities
```

### Validation:
- All tests pass
- No JSON conversion for properties

## Phase 7: Clean Architecture Demo

### Goal
Create clean examples showing the simplified architecture.

### Tasks:
1. Create `SimplifiedChatWorkflow` example
2. Create `SimplifiedAgentExample` 
3. Create migration guide
4. Update documentation

### Files to create:
```
- examples/SimplifiedChatWorkflow.java
- examples/SimplifiedAgentExample.java
- MIGRATION_TO_CHATSTORE.md
```

## Testing Strategy Per Phase

### Phase 1:
```bash
mvn test -pl driftkit-workflows/driftkit-workflow-engine-core -Dtest=ChatStoreTest
```

### Phase 2:
```bash
mvn test -pl driftkit-agents -Dtest=LLMAgentTest
```

### Phase 3:
```bash
mvn test -pl driftkit-workflows/driftkit-workflow-engine-core -Dtest=ChatWorkflowExampleTest
```

### Phase 4-7:
```bash
mvn test -pl driftkit-workflows/driftkit-workflow-engine-core
```

## Success Metrics

After all phases:
- 8+ classes → 1 ChatStore
- Zero manual chat saving in workflows
- Direct ChatStore usage in agents
- No adapters or converters
- All tests pass
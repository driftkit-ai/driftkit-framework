# LLMAgent Integration Research for Workflow Engine Core

## Executive Summary

This document analyzes the LLMAgent framework found in `driftkit-workflows-core` and evaluates whether it should be integrated into `driftkit-workflow-engine-core` alongside the existing fluent API implementation.

## Current State Analysis

### LLMAgent Location and Architecture

The LLMAgent framework is located in:
- **Module**: `driftkit-workflows-core`
- **Package**: `ai.driftkit.workflows.core.agent`
- **Main Classes**:
  - `LLMAgent` - Core agent implementation with builder pattern
  - `LoopAgent` - Iterative refinement pattern
  - `SequentialAgent` - Multi-stage processing pattern
  - `AgentWorkflow` - Base workflow class using LLMAgent
  - `AgentResponse<T>` - Type-safe response wrapper
  - Tool-related classes for function calling

### LLMAgent Key Features

1. **Simplified API over ModelClient**
   - `executeText()` - Simple text chat
   - `executeWithImages()` - Multi-modal support
   - `executeForToolCalls()` - Manual tool execution
   - `executeWithTools()` - Automatic tool execution
   - `executeStructured()` - Type-safe JSON extraction
   - `executeWithPrompt()` - Template-based prompts

2. **Agent Composition Patterns**
   - **LoopAgent**: Iterative refinement with worker/evaluator pattern
   - **SequentialAgent**: Pipeline processing where output flows to next input
   - **AgentAsTool**: Agents can be used as tools by other agents

3. **Built-in Features**
   - Conversation memory management via `ChatMemory`
   - Request tracing with `RequestTracingProvider`
   - Integration with `PromptService` for templates
   - Tool registry for function calling
   - Type-safe responses with `AgentResponse<T>`

### Workflow Engine Core Current State

The `driftkit-workflow-engine-core` module has:

1. **Fluent API Features**
   - Direct method reference support: `.then(steps::processData)`
   - Parallel execution: `.parallel(step1, step2, step3)`
   - Multi-way branching: `.on(condition).is(value, flow).otherwise(flow)`
   - Try-catch-finally: `.tryStep(step).catch(handler).finally(cleanup)`
   - Async step handling with progress reporting

2. **Architecture**
   - Annotation-based workflow definition (`@Step`, `@AsyncStep`)
   - Graph-based execution model
   - Comprehensive type tracking
   - WorkflowContext for state management
   - Suspension/resume capabilities

## Integration Analysis

### Architectural Compatibility

1. **Different Paradigms**
   - **LLMAgent**: Imperative, direct execution model focused on AI interactions
   - **Workflow Engine**: Declarative, graph-based model for complex business workflows
   - The two serve different but complementary purposes

2. **Abstraction Levels**
   - **LLMAgent**: High-level abstraction for AI operations
   - **Workflow Engine**: Low-level orchestration of arbitrary business logic
   - LLMAgent could be used WITHIN workflow steps, not replace the workflow engine

3. **Use Case Focus**
   - **LLMAgent**: Optimized for AI agent interactions, tool calling, structured extraction
   - **Workflow Engine**: General-purpose workflow orchestration with type safety

### Integration Possibilities

#### Option 1: Keep Separate (Recommended)

**Rationale:**
- Clear separation of concerns
- Each module serves a distinct purpose
- No architectural conflicts or compromises
- Users can choose the right tool for their needs

**Usage Pattern:**
```java
// Use LLMAgent within a workflow step
@Component
public class AIWorkflowSteps {
    private final LLMAgent agent;
    
    @Step
    public StepResult<Analysis> analyzeContent(String content, WorkflowContext ctx) {
        AgentResponse<Analysis> response = agent.executeStructured(content, Analysis.class);
        return StepResult.success(response.getStructuredData());
    }
}
```

#### Option 2: Create Bridge Module

**Create**: `driftkit-workflow-engine-agents`

**Features:**
- Adapter classes to use LLMAgent patterns in workflows
- Pre-built workflow steps for common AI operations
- Integration utilities

**Example:**
```java
// Bridge utilities
public class AgentWorkflowSteps {
    public static StepDefinition<String, String> llmStep(LLMAgent agent) {
        return StepDefinition.of("llm-process", 
            (input, ctx) -> StepResult.success(agent.executeText(input).getText()));
    }
    
    public static StepDefinition<String, List<ToolExecutionResult>> toolStep(LLMAgent agent) {
        return StepDefinition.of("tool-execution",
            (input, ctx) -> StepResult.success(agent.executeWithTools(input).getToolResults()));
    }
}
```

#### Option 3: Direct Integration (Not Recommended)

**Challenges:**
- Would complicate the workflow engine's clean architecture
- Mixing concerns (general orchestration vs AI-specific operations)
- Increased module dependencies
- Potential for API confusion

## Recommendations

### 1. Maintain Separation

Keep LLMAgent in `driftkit-workflows-core` and workflow engine in `driftkit-workflow-engine-core`. They solve different problems:
- LLMAgent: Simplified AI agent interactions
- Workflow Engine: Complex business process orchestration

### 2. Usage Guidelines

Document how to use LLMAgent within workflow steps:

```java
@Workflow
public class CustomerSupportWorkflow {
    private final LLMAgent classifierAgent;
    private final LLMAgent responseAgent;
    
    @Step("classify-intent")
    public StepResult<IntentClassification> classifyIntent(CustomerQuery query, WorkflowContext ctx) {
        return StepResult.success(
            classifierAgent.executeStructured(query.getMessage(), IntentClassification.class)
                          .getStructuredData()
        );
    }
    
    @Step("generate-response") 
    public StepResult<String> generateResponse(IntentClassification intent, WorkflowContext ctx) {
        CustomerQuery query = ctx.getInput();
        Map<String, Object> vars = Map.of("intent", intent, "query", query);
        
        return StepResult.success(
            responseAgent.executeWithPrompt("customer-response-template", vars)
                        .getText()
        );
    }
}
```

### 3. Future Enhancements

Consider these future additions to workflow-engine-core that would complement LLMAgent usage:

1. **Built-in Retry Logic for AI Steps**
   ```java
   .then(aiStep)
   .withRetry(3, Duration.ofSeconds(2))
   ```

2. **Token/Cost Tracking**
   ```java
   .then(aiStep)
   .trackTokenUsage("gpt-4-tokens")
   ```

3. **AI-Specific Error Handling**
   ```java
   .tryStep(aiStep)
   .catchAIError(RateLimitException.class, handler)
   .catchAIError(TokenLimitException.class, handler)
   ```

### 4. Documentation Updates

Create clear documentation showing:
1. When to use LLMAgent directly vs within workflows
2. Best practices for AI steps in workflows
3. Example patterns combining both approaches
4. Performance considerations

## Conclusion

The LLMAgent framework and the workflow engine serve complementary but distinct purposes. Rather than merging them, they should remain separate modules that can be used together when needed. This maintains architectural clarity while providing maximum flexibility for users.

The workflow engine excels at orchestrating complex business processes with type safety and graph-based execution, while LLMAgent provides a clean, high-level API for AI interactions. Users benefit most from having both tools available and being able to combine them as needed for their specific use cases.

## Action Items

1. ✅ Keep modules separate
2. 📝 Create integration examples in documentation
3. 📝 Add cookbook section showing LLMAgent + Workflow patterns
4. 🔍 Consider future bridge module if usage patterns emerge
5. 📊 Monitor user feedback for integration needs
# Fluent API Type Flow Fix Plan

## Core Problem
When a branch is taken, the first step in the branch receives the workflow's original input (e.g., ChatRequest) instead of the output from the step before the branch (e.g., IntentAnalysis).

## Root Causes

1. **Branch Construction**: Branches are created as independent workflows, losing connection to the previous step's output
2. **Input Preparation**: The input search algorithm finds ANY compatible type, often the original workflow input
3. **Type Erasure**: Branch definitions don't preserve the expected input type from the previous step

## Solution Architecture

### 1. Enhanced Branch Edge Information
Instead of just marking edges as "branch", we need to include:
- The source step that produced the input for the branch
- The expected input type for branch steps

### 2. Modify Edge Class
```java
public record Edge(
    String from,
    String to,
    EdgeType type,
    Class<?> branchCondition,
    String inputSource  // NEW: ID of step whose output should be used
) {
    // Create branch edge with input source
    public static Edge branchWithSource(String from, String to, 
                                       Class<?> condition, String inputSource) {
        return new Edge(from, to, EdgeType.BRANCH, condition, inputSource);
    }
}
```

### 3. Update TypedBranchStep
- Track the previous step ID that provides input to branches
- Pass this information when creating branch edges

### 4. Fix Input Preparation
Modify the input preparation logic to:
1. For branch steps, check if there's an inputSource in the incoming edge
2. If yes, use ONLY the output from that specific step
3. Never fall back to workflow trigger data for non-initial steps in branches

### 5. Type-Safe Step Definitions
Ensure all steps have proper type information:
- Method references: Already extract types correctly
- Lambdas: Require explicit type declaration
- No Object types allowed except for truly generic operations

## Implementation Steps

1. **Update Edge class** to include inputSource field
2. **Modify TypedBranchStep** to:
   - Remember the previousStepId
   - Create branch edges with inputSource = previousStepId
3. **Update WorkflowOrchestrator** to:
   - Pass edge information to input preparation
   - Use inputSource when preparing input for branch steps
4. **Fix InputPreparer/prepareStepInput** to:
   - Accept edge information
   - Prioritize inputSource output over generic search
5. **Update test to use proper domain types** instead of ChatRequest/ChatResponse

## Expected Result
```
Step: extractIntent
  Input: ChatRequest
  Output: IntentAnalysis

Branch Decision
  Input: IntentAnalysis (from extractIntent)
  
  True Branch:
    Step: analyzeQuestion
      Input: IntentAnalysis (from extractIntent via inputSource)
      Output: QuestionAnalysis
  
  False Branch:
    Step: generateGenericResponse
      Input: IntentAnalysis (from extractIntent via inputSource)
      Output: GenericResponse
```

## Critical Constraints
- NO Object types in function signatures (except WorkflowContext, AsyncProgressReporter)
- All types must be concrete domain types
- Type information must flow through the entire graph
- Branches must receive the previous step's output, not workflow input
# Template Fixes for DriftKit CLI

## Chatbot Template Fixes

### 1. Fixed Imports in OnboardingWorkflow.java

**Before:**
```java
import ai.driftkit.chat.framework.workflow.AsyncStep;
import ai.driftkit.chat.framework.workflow.WorkflowStep;
import ai.driftkit.chat.framework.domain.*;
```

**After:**
```java
import ai.driftkit.chat.framework.annotations.AsyncStep;
import ai.driftkit.chat.framework.annotations.WorkflowStep;
import ai.driftkit.chat.framework.model.WorkflowContext;
import ai.driftkit.chat.framework.events.StepEvent;
```

### 2. Updated @WorkflowStep Annotation Parameters

**Before:**
```java
@WorkflowStep(
    stepId = "collect-basic-info",
    description = "Collect basic user information",
    schemaClass = BasicInfoSchema.class
)
```

**After:**
```java
@WorkflowStep(
    id = "collect-basic-info",
    description = "Collect basic user information",
    inputClass = BasicInfoSchema.class,
    requiresUserInput = true
)
```

### 3. Changed Method Signatures and Return Types

**Before:**
```java
public StepResponse collectBasicInfo(WorkflowContext context, Map<String, Object> input) {
    context.setVariable("userName", input.get("name"));
    return StepResponse.builder()
        .status(StepStatus.COMPLETED)
        .message("Thank you!")
        .nextStepId("setup-preferences")
        .build();
}
```

**After:**
```java
public StepEvent collectBasicInfo(WorkflowContext context, BasicInfoSchema input) {
    context.setData("userName", input.getName());
    return StepEvent.withMessage("Thank you!")
        .setNextStepId("setup-preferences");
}
```

### 4. Fixed @AsyncStep Annotation

**Before:**
```java
@AsyncStep(
    stepId = "human-review",
    description = "Human review of user profile",
    requiresHumanInput = true
)
```

**After:**
```java
@AsyncStep(
    forStep = "human-review",
    description = "Human review of user profile"
)
```

### 5. Updated ChatWebSocketController.java

- Changed imports from `ai.driftkit.chat.framework.domain.*` to proper model imports
- Replaced `WorkflowExecutor` with `ChatWorkflowService`
- Updated method calls to use the correct API
- Added helper methods for converting between data structures

### 6. Fixed WorkflowConfig.java

**Before:**
```java
@Bean
public WorkflowExecutor workflowExecutor(List<AnnotatedWorkflow> workflows) {
    WorkflowExecutor executor = new WorkflowExecutor();
    workflows.forEach(executor::registerWorkflow);
    return executor;
}
```

**After:**
```java
@PostConstruct
public void registerWorkflows() {
    workflows.forEach(workflow -> {
        WorkflowRegistry.registerWorkflow(workflow.getWorkflowId(), workflow);
    });
}
```

### 7. Updated SessionService.java

- Fixed import from `ai.driftkit.chat.framework.domain.WorkflowContext` to `ai.driftkit.chat.framework.model.WorkflowContext`

## Summary

All chatbot template files have been updated to use the correct DriftKit Chat Assistant Framework API. The changes ensure:

1. Correct package imports for annotations and model classes
2. Proper annotation parameters matching the framework's expectations
3. Correct method signatures and return types for workflow steps
4. Proper integration with the framework's service classes

These fixes ensure that projects created with the chatbot template will compile and run correctly with the DriftKit Chat Assistant Framework.
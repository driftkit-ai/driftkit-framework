# Summary of Fixes to DriftKit CLI

## Issues Fixed

### 1. Path Resolution Issue
**Problem**: When running `driftkit new` from within the driftkit-cli directory, template files were created in the wrong location (e.g., `/driftkit-cli/src/main/java/OnboardingWorkflow.java` instead of in the new project directory).

**Fix**: 
- Added `projectPath = projectPath.toAbsolutePath()` in `ProjectGenerator.generateProject()`
- Added `targetPath = targetPath.toAbsolutePath()` in `generateFileFromTemplate()`
- Ensured parent directories are created with `Files.createDirectories(targetPath.getParent())`

### 2. Template Configuration Management
**Problem**: Application.yml was being generated multiple times, and different templates needed different configurations.

**Fix**: 
- Centralized application.yml generation in the main `generateProject()` method
- Use template-specific configurations:
  ```java
  String appYmlTemplate = switch (template.toLowerCase()) {
      case "chatbot" -> "chatbot/application.yml";
      case "full_stack" -> "full_stack/application.yml";
      case "rag_pipeline" -> "rag_pipeline/application.yml";
      case "spring_ai" -> "spring_ai/application.yml";
      default -> "src/application.yml";
  };
  ```
- All configs are saved to the same location: `src/main/resources/application.yml`

### 3. Template Generation Improvements
**Problem**: Some template files were missing from generation, and unnecessary files were being included.

**Fix**:
- Removed WorkflowConfig.java generation (workflows self-register via AnnotatedWorkflow constructor)
- Removed SessionService.java generation (session management should be handled by framework)
- Added ResourceConfig.java generation for chatbot template
- Ensured all templates use the same MainApplication.java template for consistency

### 4. API Updates in Chatbot Template
**Problem**: Chatbot template was using incorrect imports and non-existent classes.

**Fix**:
- Updated imports to use correct packages:
  - `ai.driftkit.chat.framework.annotations.*` for annotations
  - `ai.driftkit.chat.framework.model.*` for model classes
  - `ai.driftkit.chat.framework.events.*` for event classes
- Changed WorkflowExecutor to ChatWorkflowService
- Updated method signatures to return StepEvent instead of StepResponse
- Fixed annotation parameters (@WorkflowStep uses `id` not `stepId`)

## Current State

The DriftKit CLI now correctly:
1. Creates projects in the intended directory regardless of where the command is run
2. Generates appropriate configuration files for each template type
3. Uses the correct DriftKit framework APIs in generated code
4. Maintains consistency across all project templates while allowing template-specific customizations
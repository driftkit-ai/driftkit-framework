package ai.driftkit.workflow.engine.builder;

import ai.driftkit.workflow.engine.core.*;
import ai.driftkit.workflow.engine.domain.WorkflowEngineConfig;
import ai.driftkit.workflow.engine.graph.WorkflowGraph;
import ai.driftkit.workflow.engine.persistence.inmemory.*;
import ai.driftkit.workflow.engine.async.InMemoryProgressTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowBuilderBranchTest {
    
    @Test
    @DisplayName("Should build workflow with simple branch")
    void testSimpleBranch() {
        WorkflowGraph<Integer, String> workflow = WorkflowBuilder
            .define("branch-workflow", Integer.class, String.class)
            .then("check", (Integer num) -> StepResult.continueWith(num), Integer.class, Integer.class)
            .branch(
                ctx -> ctx.step("check").output(Integer.class)
                    .map(n -> n > 0)
                    .orElse(false),
                
                // True branch - positive numbers
                positive -> positive
                    .then("positive", (Integer n) -> StepResult.finish("Positive: " + n), Integer.class, String.class),
                    
                // False branch - non-positive numbers
                nonPositive -> nonPositive
                    .then("non-positive", (Integer n) -> StepResult.finish("Non-positive: " + n), Integer.class, String.class)
            )
            .build();
        
        assertNotNull(workflow);
        // Should have initial step + 2 branch steps
        assertTrue(workflow.nodes().size() >= 3);
    }
    
    @Test
    @DisplayName("Should build workflow with nested branches")
    void testNestedBranches() {
        WorkflowGraph<String, String> workflow = WorkflowBuilder
            .define("nested-branch", String.class, String.class)
            .then("parse", (String input) -> {
                try {
                    int num = Integer.parseInt(input.trim());
                    return StepResult.continueWith(num);
                } catch (NumberFormatException e) {
                    return StepResult.fail("Invalid number");
                }
            }, String.class, Integer.class)
            .branch(
                ctx -> ctx.step("parse").succeeded(),
                
                // Success branch - further categorize the number
                success -> success.branch(
                    ctx -> ctx.step("parse").output(Integer.class)
                        .map(n -> n >= 100)
                        .orElse(false),
                    
                    // Large numbers
                    large -> large.then("large", (Integer n) -> StepResult.finish("Large: " + n), Integer.class, String.class),
                    
                    // Small numbers
                    small -> small.then("small", (Integer n) -> StepResult.finish("Small: " + n), Integer.class, String.class)
                ),
                
                // Failure branch
                failure -> failure.then("error", (Object ignored) -> StepResult.finish("Error: Invalid input"), Object.class, String.class)
            )
            .build();
        
        assertNotNull(workflow);
        assertTrue(workflow.nodes().size() >= 4);
    }
    
    @Test
    @DisplayName("Should build workflow with complex conditions")
    void testComplexBranchConditions() {
        record UserData(String role, int accessLevel) {}
        
        WorkflowGraph<UserData, String> workflow = WorkflowBuilder
            .define("complex-conditions", UserData.class, String.class)
            .then("validate", (UserData user) -> {
                if (user.role() == null || user.accessLevel() < 0) {
                    return StepResult.fail("Invalid user data");
                }
                return StepResult.continueWith(user);
            }, UserData.class, UserData.class)
            .branch(
                ctx -> {
                    return ctx.step("validate").output(UserData.class)
                        .map(user -> "admin".equals(user.role()) && user.accessLevel() >= 10)
                        .orElse(false);
                },
                
                // Admin with high access
                admin -> admin.then("admin-flow", 
                    (UserData user) -> StepResult.finish("Admin access granted"), UserData.class, String.class),
                    
                // Others
                other -> other.branch(
                    ctx -> ctx.step("validate").output(UserData.class)
                        .map(user -> user.accessLevel() >= 5)
                        .orElse(false),
                    
                    // Medium access
                    medium -> medium.then("medium-flow", 
                        (UserData user) -> StepResult.finish("Standard access granted"), UserData.class, String.class),
                        
                    // Low access
                    low -> low.then("low-flow", 
                        (UserData user) -> StepResult.finish("Limited access granted"), UserData.class, String.class)
                )
            )
            .build();
        
        assertNotNull(workflow);
        // Should have validate + 3 access level steps
        assertTrue(workflow.nodes().size() >= 4);
    }
    
    @Test
    @DisplayName("Should handle branch with single path")
    void testSinglePathBranch() {
        WorkflowGraph<Boolean, String> workflow = WorkflowBuilder
            .define("single-path", Boolean.class, String.class)
            .branch(
                ctx -> ctx.getTriggerData(Boolean.class),
                
                // Only true branch defined
                trueBranch -> trueBranch
                    .then("true-step", (Boolean b) -> StepResult.finish("Was true"), Boolean.class, String.class),
                    
                // False branch just finishes
                falseBranch -> falseBranch
                    .then("false-step", (Boolean b) -> StepResult.finish("Was false"), Boolean.class, String.class)
            )
            .build();
        
        assertNotNull(workflow);
        // Should have 2 branch steps + 1 decision step = 3
        assertEquals(3, workflow.nodes().size());
    }
    
    @Test
    @DisplayName("Should build workflow with multiple sequential branches")
    void testMultipleSequentialBranches() {
        WorkflowGraph<Integer, String> workflow = WorkflowBuilder
            .define("multi-branch", Integer.class, String.class)
            .then("start", (Integer n) -> StepResult.continueWith(n), Integer.class, Integer.class)
            
            // First branch - check if positive
            .branch(
                ctx -> ctx.step("start").output(Integer.class)
                    .map(n -> n > 0)
                    .orElse(false),
                    
                pos -> pos.then("positive", (Integer n) -> StepResult.continueWith(n), Integer.class, Integer.class),
                neg -> neg.then("negative", (Integer n) -> StepResult.continueWith(-n), Integer.class, Integer.class)
            )
            
            // Second branch - check if even
            .branch(
                ctx -> {
                    // Get output from either positive or negative step
                    Integer n = ctx.step("positive").output(Integer.class)
                        .or(() -> ctx.step("negative").output(Integer.class))
                        .orElse(0);
                    return n % 2 == 0;
                },
                
                even -> even.then("even", (Integer n) -> StepResult.finish("Even: " + n), Integer.class, String.class),
                odd -> odd.then("odd", (Integer n) -> StepResult.finish("Odd: " + n), Integer.class, String.class)
            )
            .build();
        
        assertNotNull(workflow);
        // start + 2 decision steps + positive/negative + even/odd = 7 steps
        assertEquals(7, workflow.nodes().size());
    }
    
    @Test
    @DisplayName("Should validate branch conditions")
    void testBranchValidation() {
        // Branch without condition should throw
        assertThrows(IllegalArgumentException.class, () ->
            WorkflowBuilder
                .define("invalid", String.class, String.class)
                .branch(
                    null,  // Invalid null condition
                    t -> t.then("true", (String s) -> StepResult.finish(s), String.class, String.class),
                    f -> f.then("false", (String s) -> StepResult.finish(s), String.class, String.class)
                )
                .build()
        );
    }
    
    @Nested
    @DisplayName("Missing Critical Test Scenarios")
    class MissingScenarios {
        
        private WorkflowEngine engine;
        
        @BeforeEach
        void setUp() {
            WorkflowEngineConfig config = WorkflowEngineConfig.builder()
                .stateRepository(new InMemoryWorkflowStateRepository())
                .progressTracker(new InMemoryProgressTracker())
                .chatSessionRepository(new InMemoryChatSessionRepository())
                .chatHistoryRepository(new InMemoryChatHistoryRepository())
                .asyncStepStateRepository(new InMemoryAsyncStepStateRepository())
                .suspensionDataRepository(new InMemorySuspensionDataRepository())
                .build();
            
            engine = new WorkflowEngine(config);
        }
        
        @Test
        @DisplayName("Should handle branch condition that throws exception")
        void testBranchConditionThrowsException() throws Exception {
            // Arrange - Create workflow with branch condition that can throw
            WorkflowGraph<String, String> workflow = WorkflowBuilder
                .define("exception-branch", String.class, String.class)
                .then("process", (String input) -> {
                    // Store data that might cause exception
                    return StepResult.continueWith(input);
                }, String.class, String.class)
                .branch(
                    ctx -> {
                        String data = ctx.step("process").output(String.class).orElse("");
                        if ("error".equals(data)) {
                            throw new RuntimeException("Branch condition error");
                        }
                        return data.startsWith("yes");
                    },
                    
                    yesBranch -> yesBranch.then("yes-path", 
                        (String s) -> StepResult.finish("Yes: " + s), String.class, String.class),
                    
                    noBranch -> noBranch.then("no-path", 
                        (String s) -> StepResult.finish("No: " + s), String.class, String.class)
                )
                .build();
            
            engine.register(workflow);
            
            // Act & Assert - Normal execution
            WorkflowEngine.WorkflowExecution<String> normalExec = engine.execute("exception-branch", "yes-data");
            String normalResult = normalExec.get(5, TimeUnit.SECONDS);
            assertEquals("Yes: yes-data", normalResult);
            
            // Act & Assert - Exception in branch condition
            WorkflowEngine.WorkflowExecution<String> errorExec = engine.execute("exception-branch", "error");
            ExecutionException exception = assertThrows(ExecutionException.class, 
                () -> errorExec.get(5, TimeUnit.SECONDS));
            
            assertTrue(exception.getCause() instanceof RuntimeException);
            assertTrue(exception.getCause().getMessage().contains("Branch condition error"));
        }
        
        @Test
        @DisplayName("Should handle branches with async conditions")
        void testAsyncBranchConditions() throws Exception {
            // Arrange - Simulate external service for async condition
            AtomicInteger serviceCallCount = new AtomicInteger(0);
            CompletableFuture<Boolean> asyncConditionCheck = new CompletableFuture<>();
            
            WorkflowGraph<Integer, String> workflow = WorkflowBuilder
                .define("async-branch", Integer.class, String.class)
                .then("store-data", (Integer id) -> {
                    // Store data for later async check
                    return StepResult.continueWith(id);
                }, Integer.class, Integer.class)
                .branch(
                    ctx -> {
                        // Simulate async condition check (e.g., checking external service)
                        Integer id = ctx.step("store-data").output(Integer.class).orElse(0);
                        
                        // Simulate delay for async check
                        try {
                            Thread.sleep(100);
                            serviceCallCount.incrementAndGet();
                            
                            // In real scenario, this would be an async call
                            return id % 2 == 0; // Even IDs take one path, odd another
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return false;
                        }
                    },
                    
                    evenBranch -> evenBranch.then("even-handler", 
                        (Integer id) -> StepResult.finish("Even ID processed: " + id), 
                        Integer.class, String.class),
                    
                    oddBranch -> oddBranch.then("odd-handler", 
                        (Integer id) -> StepResult.finish("Odd ID processed: " + id), 
                        Integer.class, String.class)
                )
                .build();
            
            engine.register(workflow);
            
            // Act - Execute with even ID
            WorkflowEngine.WorkflowExecution<String> evenExec = engine.execute("async-branch", 42);
            String evenResult = evenExec.get(5, TimeUnit.SECONDS);
            
            // Assert
            assertEquals("Even ID processed: 42", evenResult);
            assertEquals(1, serviceCallCount.get());
            
            // Act - Execute with odd ID
            WorkflowEngine.WorkflowExecution<String> oddExec = engine.execute("async-branch", 17);
            String oddResult = oddExec.get(5, TimeUnit.SECONDS);
            
            // Assert
            assertEquals("Odd ID processed: 17", oddResult);
            assertEquals(2, serviceCallCount.get());
            
            // Verify conditions were checked asynchronously (with delay)
            assertTrue(serviceCallCount.get() > 0, "Async condition should have been evaluated");
        }
        
        @Test
        @DisplayName("Should handle deeply nested branches (>3 levels)")
        void testDeeplyNestedBranches() throws Exception {
            // Arrange - Create workflow with 4 levels of nested branches
            WorkflowGraph<Integer, String> workflow = WorkflowBuilder
                .define("deep-branch", Integer.class, String.class)
                .then("start", (Integer n) -> StepResult.continueWith(n), Integer.class, Integer.class)
                
                // Level 1: Check if positive
                .branch(
                    ctx -> ctx.step("start").output(Integer.class).orElse(0) > 0,
                    
                    // Positive branch - Level 2: Check if > 100
                    positive -> positive.branch(
                        ctx -> ctx.step("start").output(Integer.class).orElse(0) > 100,
                        
                        // Large branch - Level 3: Check if > 1000
                        large -> large.branch(
                            ctx -> ctx.step("start").output(Integer.class).orElse(0) > 1000,
                            
                            // Very large branch - Level 4: Check if > 10000
                            veryLarge -> veryLarge.branch(
                                ctx -> ctx.step("start").output(Integer.class).orElse(0) > 10000,
                                
                                massive -> massive.then("massive", 
                                    (Integer n) -> StepResult.finish("Massive: " + n), 
                                    Integer.class, String.class),
                                    
                                justVeryLarge -> justVeryLarge.then("very-large", 
                                    (Integer n) -> StepResult.finish("Very Large: " + n), 
                                    Integer.class, String.class)
                            ),
                            
                            // Just large
                            justLarge -> justLarge.then("large", 
                                (Integer n) -> StepResult.finish("Large: " + n), 
                                Integer.class, String.class)
                        ),
                        
                        // Medium
                        medium -> medium.then("medium", 
                            (Integer n) -> StepResult.finish("Medium: " + n), 
                            Integer.class, String.class)
                    ),
                    
                    // Non-positive
                    nonPositive -> nonPositive.then("non-positive", 
                        (Integer n) -> StepResult.finish("Non-positive: " + n), 
                        Integer.class, String.class)
                )
                .build();
            
            engine.register(workflow);
            
            // Act & Assert - Test each path
            
            // Non-positive
            WorkflowEngine.WorkflowExecution<String> negExec = engine.execute("deep-branch", -5);
            assertEquals("Non-positive: -5", negExec.get(5, TimeUnit.SECONDS));
            
            // Medium (1-100)
            WorkflowEngine.WorkflowExecution<String> medExec = engine.execute("deep-branch", 50);
            assertEquals("Medium: 50", medExec.get(5, TimeUnit.SECONDS));
            
            // Large (101-1000)
            WorkflowEngine.WorkflowExecution<String> largeExec = engine.execute("deep-branch", 500);
            assertEquals("Large: 500", largeExec.get(5, TimeUnit.SECONDS));
            
            // Very Large (1001-10000)
            WorkflowEngine.WorkflowExecution<String> veryLargeExec = engine.execute("deep-branch", 5000);
            assertEquals("Very Large: 5000", veryLargeExec.get(5, TimeUnit.SECONDS));
            
            // Massive (>10000)
            WorkflowEngine.WorkflowExecution<String> massiveExec = engine.execute("deep-branch", 50000);
            assertEquals("Massive: 50000", massiveExec.get(5, TimeUnit.SECONDS));
        }
        
        @Test
        @DisplayName("Should handle branch with context-dependent conditions")
        void testContextDependentBranches() throws Exception {
            // Arrange - Workflow that branches based on accumulated context
            WorkflowGraph<String, String> workflow = WorkflowBuilder
                .define("context-branch", String.class, String.class)
                .then(StepDefinition.of("parse", (String input, WorkflowContext ctx) -> {
                    String[] parts = input.split(",");
                    ctx.setContextValue("item_count", parts.length);
                    ctx.setContextValue("total_length", input.length());
                    return StepResult.continueWith(parts);
                }).withTypes(String.class, String[].class))
                .branch(
                    ctx -> {
                        int itemCount = ctx.getInt("item_count");
                        int totalLength = ctx.getInt("total_length");
                        
                        // Complex condition based on multiple context values
                        return itemCount > 3 && totalLength > 20;
                    },
                    
                    complexBranch -> complexBranch
                        .then("process-complex", (String[] items) -> {
                            return StepResult.finish("Complex processing for " + items.length + " items");
                        }, String[].class, String.class),
                    
                    simpleBranch -> simpleBranch
                        .then("process-simple", (String[] items) -> {
                            return StepResult.finish("Simple processing for " + items.length + " items");
                        }, String[].class, String.class)
                )
                .build();
            
            engine.register(workflow);
            
            // Act & Assert - Simple case
            WorkflowEngine.WorkflowExecution<String> simpleExec = engine.execute("context-branch", "a,b,c");
            assertEquals("Simple processing for 3 items", simpleExec.get(5, TimeUnit.SECONDS));
            
            // Act & Assert - Complex case
            WorkflowEngine.WorkflowExecution<String> complexExec = engine.execute("context-branch", "apple,banana,cherry,dragonfruit,elderberry");
            assertEquals("Complex processing for 5 items", complexExec.get(5, TimeUnit.SECONDS));
        }
    }
}
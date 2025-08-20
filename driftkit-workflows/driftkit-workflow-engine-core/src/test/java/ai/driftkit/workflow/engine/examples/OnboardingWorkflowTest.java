package ai.driftkit.workflow.engine.examples;

import ai.driftkit.workflow.engine.annotations.*;
import ai.driftkit.workflow.engine.core.*;
import ai.driftkit.workflow.engine.domain.WorkflowEngineConfig;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import ai.driftkit.workflow.engine.persistence.inmemory.InMemoryWorkflowStateRepository;
import ai.driftkit.workflow.engine.schema.SchemaDescription;
import ai.driftkit.workflow.engine.schema.SchemaName;
import ai.driftkit.workflow.engine.schema.SchemaProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for onboarding workflow pattern
 */
@Slf4j
@DisplayName("Onboarding Workflow Tests")
class OnboardingWorkflowTest {

    private WorkflowEngine engine;
    
    @BeforeEach
    void setUp() {
        // Create engine configuration
        WorkflowEngineConfig config = WorkflowEngineConfig.builder()
            .stateRepository(new InMemoryWorkflowStateRepository())
            .coreThreads(2)
            .maxThreads(10)
            .build();
            
        // Initialize engine
        engine = new WorkflowEngine(config);
        
        // Register workflow
        OnboardingWorkflow workflow = new OnboardingWorkflow();
        engine.register(workflow);
    }
    
    @Test
    @DisplayName("Should complete full onboarding flow with level assessment")
    void testCompleteOnboardingFlow() throws Exception {
        String userId = "test-user-123";
        String chatId = "test-chat-456";
        
        // Start workflow
        var execution = engine.execute("test-onboarding", null, chatId + "_test", chatId);
        String runId = execution.getRunId();
        
        // Wait a bit for workflow to reach suspended state
        Thread.sleep(500);
        
        // Check initial suspension for self-assessment
        Optional<WorkflowInstance> instanceOpt = engine.getWorkflowInstance(runId);
        assertTrue(instanceOpt.isPresent());
        WorkflowInstance instance = instanceOpt.get();
        assertEquals(WorkflowInstance.WorkflowStatus.SUSPENDED, instance.getStatus());
        assertEquals("requestSelfAssessment", instance.getCurrentStepId());
        
        // Get current result to check suspension data
        Optional<ai.driftkit.workflow.engine.domain.WorkflowEvent> currentResult = engine.getCurrentResult(runId);
        assertTrue(currentResult.isPresent());
        
        // The promptToUser data should be in the workflow event
        // Since properties are Map<String, String>, we need to check if the suspension data
        // contains our prompt object
        // For suspended workflows, the actual data might be in a different structure
        
        // Verify we're suspended
        assertEquals(WorkflowInstance.WorkflowStatus.SUSPENDED, instance.getStatus());
        
        // In a real test scenario, we would need to access the suspension data
        // through the appropriate repository or API
        // For now, we'll just verify the workflow is in the expected state
        
        // Resume with self-assessment
        OnboardingWorkflow.SelfAssessment selfAssessment = new OnboardingWorkflow.SelfAssessment();
        selfAssessment.setLevel("BEGINNER");
        
        log.info("Before first resume - runId: {}", runId);
        execution = engine.resume(runId, selfAssessment);
        Thread.sleep(500);
        log.info("After first resume sleep");
        
        // Check suspension for first test question
        instanceOpt = engine.getWorkflowInstance(runId);
        assertTrue(instanceOpt.isPresent());
        instance = instanceOpt.get();
        assertEquals(WorkflowInstance.WorkflowStatus.SUSPENDED, instance.getStatus());
        assertEquals("processSelfAssessment", instance.getCurrentStepId(), "Should still be at processSelfAssessment when suspended");
        
        // Get current result for first question
        currentResult = engine.getCurrentResult(runId);
        assertTrue(currentResult.isPresent());
        
        // Verify workflow is still suspended with first question
        assertEquals(WorkflowInstance.WorkflowStatus.SUSPENDED, instance.getStatus());
        
        // Answer first question
        OnboardingWorkflow.TestAnswer answer1 = new OnboardingWorkflow.TestAnswer();
        answer1.setSelectedAnswer("Option A");
        
        execution = engine.resume(runId, answer1);
        Thread.sleep(500);
        
        // Check suspension for second question
        instanceOpt = engine.getWorkflowInstance(runId);
        assertTrue(instanceOpt.isPresent());
        instance = instanceOpt.get();
        log.info("After answering first question - Current step: {}, Status: {}", instance.getCurrentStepId(), instance.getStatus());
        assertEquals(WorkflowInstance.WorkflowStatus.SUSPENDED, instance.getStatus());
        assertEquals("processTestAnswer", instance.getCurrentStepId(), "Should be at processTestAnswer after answering first question");
        
        // Get current result for second question
        currentResult = engine.getCurrentResult(runId);
        assertTrue(currentResult.isPresent());
        
        // Verify workflow is still suspended with second question
        assertEquals(WorkflowInstance.WorkflowStatus.SUSPENDED, instance.getStatus());
        
        // The workflow should have progressed to the second question
        
        // Answer second question
        OnboardingWorkflow.TestAnswer answer2 = new OnboardingWorkflow.TestAnswer();
        answer2.setSelectedAnswer("Option A");
        
        execution = engine.resume(runId, answer2);
        Thread.sleep(500);
        
        // Check suspension for feedback
        instanceOpt = engine.getWorkflowInstance(runId);
        assertTrue(instanceOpt.isPresent());
        instance = instanceOpt.get();
        assertEquals(WorkflowInstance.WorkflowStatus.SUSPENDED, instance.getStatus());
        
        // Get current result for feedback question
        currentResult = engine.getCurrentResult(runId);
        assertTrue(currentResult.isPresent());
        
        // Verify workflow is still suspended waiting for feedback
        assertEquals(WorkflowInstance.WorkflowStatus.SUSPENDED, instance.getStatus());
        
        // The workflow should now be asking for feedback
        
        // Answer feedback
        OnboardingWorkflow.TestAnswer feedbackAnswer = new OnboardingWorkflow.TestAnswer();
        feedbackAnswer.setSelectedAnswer("GOOD");
        
        execution = engine.resume(runId, feedbackAnswer);
        
        // Wait for workflow to complete  
        Object finalResult = execution.get(5, TimeUnit.SECONDS);
        
        // Check workflow completed
        instanceOpt = engine.getWorkflowInstance(runId);
        assertTrue(instanceOpt.isPresent());
        instance = instanceOpt.get();
        assertEquals(WorkflowInstance.WorkflowStatus.COMPLETED, instance.getStatus());
        
        // Verify final result
        assertNotNull(finalResult);
        assertTrue(finalResult instanceof OnboardingWorkflow.OnboardingComplete);
        OnboardingWorkflow.OnboardingComplete result = (OnboardingWorkflow.OnboardingComplete) finalResult;
        assertEquals("BEGINNER", result.getSelfAssessmentLevel());
        assertEquals("A2", result.getDeterminedLevel());
        assertTrue(result.getMessage().contains("Onboarding completed"));
        assertEquals(2, result.getCorrectAnswers());
        assertEquals(2, result.getTotalQuestions());
    }
    
    /**
     * Test workflow implementation
     */
    @Workflow(
        id = "test-onboarding",
        version = "1.0",
        description = "Test onboarding workflow with level assessment"
    )
    public static class OnboardingWorkflow {
        
        @InitialStep
        @Step(nextSteps = {"processSelfAssessment"})
        public StepResult<AssessmentPrompt> requestSelfAssessment(WorkflowContext context) {
            log.info("Starting onboarding workflow - thread: {}", Thread.currentThread().getName());
            
            AssessmentPrompt prompt = new AssessmentPrompt();
            prompt.setMessage("Please assess your current level");
            prompt.setOptions(Arrays.asList("BEGINNER", "INTERMEDIATE", "ADVANCED"));
            
            return StepResult.suspend(prompt, SelfAssessment.class);
        }
        
        @Step(nextSteps = {"processTestAnswer"})
        public StepResult<TestQuestion> processSelfAssessment(WorkflowContext context, SelfAssessment assessment) {
            log.info("processSelfAssessment executing - User selected level: {} - thread: {}", assessment.getLevel(), Thread.currentThread().getName());
            
            context.setContextValue("selfAssessmentLevel", assessment.getLevel());
            context.setContextValue("currentQuestionIndex", 0);
            context.setContextValue("correctAnswers", 0);
            
            // Return first question - suspend waiting for answer
            TestQuestion firstQuestion = getNextQuestion(context);
            return StepResult.suspend(firstQuestion, TestAnswer.class);
        }
        
        @Step(nextSteps = {"processTestAnswer"})
        public StepResult<?> processTestAnswer(WorkflowContext context, TestAnswer answer) {
            log.info("processTestAnswer executing - User answered: {} - thread: {}", answer.getSelectedAnswer(), Thread.currentThread().getName());
            
            // Check if this is feedback
            String currentCategory = context.getString("currentCategory");
            if ("feedback".equals(currentCategory)) {
                context.setContextValue("feedback", answer.getSelectedAnswer());
                return completeOnboarding(context);
            }
            
            // Process regular answer
            Integer currentIndex = context.getInt("currentQuestionIndex");
            Integer correctAnswers = context.getInt("correctAnswers");
            
            // Simple logic: Option A is always correct for testing
            if ("Option A".equals(answer.getSelectedAnswer())) {
                correctAnswers++;
                context.setContextValue("correctAnswers", correctAnswers);
            }
            
            // Move to next question
            currentIndex++;
            context.setContextValue("currentQuestionIndex", currentIndex);
            
            // Get next question or complete
            if (currentIndex < 2) { // We have 2 test questions
                TestQuestion nextQuestion = getNextQuestion(context);
                return StepResult.suspend(nextQuestion, TestAnswer.class);
            } else {
                // Ask for feedback
                TestQuestion feedbackQuestion = getFeedbackQuestion(context);
                return StepResult.suspend(feedbackQuestion, TestAnswer.class);
            }
        }
        
        
        private TestQuestion getNextQuestion(WorkflowContext context) {
            Integer index = context.getInt("currentQuestionIndex");
            
            TestQuestion question = new TestQuestion();
            
            if (index == 0) {
                question.setCategory("grammar");
                question.setQuestionText("What is the correct form?");
                question.setOptions(Arrays.asList("Option A", "Option B", "Option C"));
            } else {
                question.setCategory("vocabulary");
                question.setQuestionText("What does this word mean?");
                question.setOptions(Arrays.asList("Option A", "Option B", "Option C"));
            }
            
            question.setCompletionPercentage(calculateProgress(context));
            question.setConfirmationQuestion(false);
            context.setContextValue("currentCategory", question.getCategory());
            
            return question;
        }
        
        private TestQuestion getFeedbackQuestion(WorkflowContext context) {
            TestQuestion feedback = new TestQuestion();
            feedback.setCategory("feedback");
            feedback.setQuestionText("How was your test experience?");
            feedback.setOptions(Arrays.asList("BAD", "GOOD", "BEST"));
            feedback.setCompletionPercentage(100);
            
            context.setContextValue("currentCategory", "feedback");
            
            return feedback;
        }
        
        private StepResult<OnboardingComplete> completeOnboarding(WorkflowContext context) {
            String selfAssessment = context.getString("selfAssessmentLevel");
            Integer correctAnswers = context.getInt("correctAnswers");
            String feedback = context.getString("feedback");
            
            // Simple level determination
            String determinedLevel = correctAnswers >= 2 ? "A2" : "A1";
            
            OnboardingComplete complete = new OnboardingComplete();
            complete.setSelfAssessmentLevel(selfAssessment);
            complete.setDeterminedLevel(determinedLevel);
            complete.setFeedback(feedback);
            complete.setCorrectAnswers(correctAnswers);
            complete.setTotalQuestions(2);
            complete.setMessage("Onboarding completed! Your level is " + determinedLevel);
            
            return StepResult.finish(complete);
        }
        
        private int calculateProgress(WorkflowContext context) {
            Integer currentIndex = context.getInt("currentQuestionIndex");
            // 0 questions = 25%, 1 question = 50%, 2 questions = 75%, feedback = 100%
            return (currentIndex + 1) * 25;
        }
        
        // Data classes
        
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @SchemaName("assessmentPrompt")
        @SchemaDescription("Prompt for self-assessment")
        public static class AssessmentPrompt {
            @SchemaProperty(description = "Prompt message")
            private String message;
            
            @SchemaProperty(description = "Available options")
            private List<String> options;
        }
        
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @SchemaName("selfAssessment")
        @SchemaDescription("User's self-assessment selection")
        public static class SelfAssessment {
            @SchemaProperty(description = "Selected level", required = true)
            private String level;
        }
        
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @SchemaName("testAnswer")
        @SchemaDescription("Answer to test question")
        public static class TestAnswer {
            @SchemaProperty(description = "Selected answer", required = true)
            private String selectedAnswer;
        }
        
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @SchemaName("onboardingComplete")
        @SchemaDescription("Onboarding completion data")
        public static class OnboardingComplete {
            @SchemaProperty(description = "Self-assessed level")
            private String selfAssessmentLevel;
            
            @SchemaProperty(description = "Determined level from test")
            private String determinedLevel;
            
            @SchemaProperty(description = "User feedback")
            private String feedback;
            
            @SchemaProperty(description = "Number of correct answers")
            private int correctAnswers;
            
            @SchemaProperty(description = "Total number of questions")
            private int totalQuestions;
            
            @SchemaProperty(description = "Completion message")
            private String message;
        }
    }
    
    // Shared test question class
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @SchemaName("testQuestion")
    @SchemaDescription("Test question presentation")
    public static class TestQuestion {
        @SchemaProperty(description = "Question category")
        private String category;
        
        @SchemaProperty(description = "Question text")
        private String questionText;
        
        @SchemaProperty(description = "Available options")
        private List<String> options;
        
        @SchemaProperty(description = "Completion percentage")
        private int completionPercentage;
        
        @SchemaProperty(description = "Is this a confirmation question")
        private boolean isConfirmationQuestion;
    }
}
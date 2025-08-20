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
 * Test for onboarding workflow pattern using type-based routing without explicit nextClasses
 */
@Slf4j
@DisplayName("Onboarding Workflow Tests with Type-Based Routing")
class OnboardingWorkflowTypeBasedTest {

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
    @DisplayName("Should complete full onboarding flow with type-based routing")
    void testCompleteOnboardingFlowWithTypeBasedRouting() throws Exception {
        String userId = "test-user-123";
        String chatId = "test-chat-456";
        
        // Start workflow
        var execution = engine.execute("type-based-onboarding", null, chatId + "_test", chatId);
        String runId = execution.getRunId();
        
        // Wait a bit for workflow to reach suspended state
        Thread.sleep(500);
        
        // Check initial suspension for self-assessment
        Optional<WorkflowInstance> instanceOpt = engine.getWorkflowInstance(runId);
        assertTrue(instanceOpt.isPresent());
        WorkflowInstance instance = instanceOpt.get();
        assertEquals(WorkflowInstance.WorkflowStatus.SUSPENDED, instance.getStatus());
        assertEquals("requestSelfAssessment", instance.getCurrentStepId());
        
        // Resume with self-assessment
        OnboardingWorkflow.SelfAssessment selfAssessment = new OnboardingWorkflow.SelfAssessment();
        selfAssessment.setLevel("INTERMEDIATE");
        
        log.info("Resuming with self-assessment");
        execution = engine.resume(runId, selfAssessment);
        Thread.sleep(500);
        
        // Check suspension for first test question
        instanceOpt = engine.getWorkflowInstance(runId);
        assertTrue(instanceOpt.isPresent());
        instance = instanceOpt.get();
        assertEquals(WorkflowInstance.WorkflowStatus.SUSPENDED, instance.getStatus());
        assertEquals("processSelfAssessment", instance.getCurrentStepId(), "Should still be at processSelfAssessment when suspended");
        
        // Answer first question
        OnboardingWorkflow.TestAnswer answer1 = new OnboardingWorkflow.TestAnswer();
        answer1.setSelectedAnswer("Option B");
        
        log.info("Answering first question");
        execution = engine.resume(runId, answer1);
        Thread.sleep(500);
        
        // Check suspension for second question
        instanceOpt = engine.getWorkflowInstance(runId);
        assertTrue(instanceOpt.isPresent());
        instance = instanceOpt.get();
        assertEquals(WorkflowInstance.WorkflowStatus.SUSPENDED, instance.getStatus());
        assertEquals("processTestAnswer", instance.getCurrentStepId(), "Should be at processTestAnswer after answering first question");
        
        // Answer second question
        OnboardingWorkflow.TestAnswer answer2 = new OnboardingWorkflow.TestAnswer();
        answer2.setSelectedAnswer("Option C");
        
        log.info("Answering second question");
        execution = engine.resume(runId, answer2);
        Thread.sleep(500);
        
        // Check suspension for third question
        instanceOpt = engine.getWorkflowInstance(runId);
        assertTrue(instanceOpt.isPresent());
        instance = instanceOpt.get();
        assertEquals(WorkflowInstance.WorkflowStatus.SUSPENDED, instance.getStatus());
        
        // Answer third question
        OnboardingWorkflow.TestAnswer answer3 = new OnboardingWorkflow.TestAnswer();
        answer3.setSelectedAnswer("Option A");
        
        log.info("Answering third question");
        execution = engine.resume(runId, answer3);
        Thread.sleep(500);
        
        // Check suspension for feedback
        instanceOpt = engine.getWorkflowInstance(runId);
        assertTrue(instanceOpt.isPresent());
        instance = instanceOpt.get();
        assertEquals(WorkflowInstance.WorkflowStatus.SUSPENDED, instance.getStatus());
        
        // Answer feedback question
        OnboardingWorkflow.TestAnswer feedbackAnswer = new OnboardingWorkflow.TestAnswer();
        feedbackAnswer.setSelectedAnswer("EXCELLENT");
        
        log.info("Providing feedback");
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
        assertEquals("INTERMEDIATE", result.getSelfAssessmentLevel());
        assertEquals("B1", result.getDeterminedLevel()); // With 2 correct answers (B and C)
        assertTrue(result.getMessage().contains("Onboarding completed"));
        assertEquals(2, result.getCorrectAnswers());
        assertEquals(3, result.getTotalQuestions());
        assertEquals("EXCELLENT", result.getFeedback());
    }
    
    /**
     * Test workflow implementation without explicit nextClasses - relies on type-based routing
     */
    @Workflow(
        id = "type-based-onboarding",
        version = "1.0",
        description = "Test onboarding workflow with type-based routing"
    )
    public static class OnboardingWorkflow {
        
        @InitialStep
        public StepResult<AssessmentPrompt> requestSelfAssessment(WorkflowContext context) {
            log.info("Starting onboarding workflow with type-based routing");
            
            AssessmentPrompt prompt = new AssessmentPrompt();
            prompt.setMessage("Please assess your current language level");
            prompt.setOptions(Arrays.asList("BEGINNER", "INTERMEDIATE", "ADVANCED"));
            
            return StepResult.suspend(prompt, SelfAssessment.class);
        }
        
        @Step
        public StepResult<TestQuestion> processSelfAssessment(WorkflowContext context, SelfAssessment assessment) {
            log.info("Processing self-assessment: {}", assessment.getLevel());
            
            context.setContextValue("selfAssessmentLevel", assessment.getLevel());
            context.setContextValue("currentQuestionIndex", 0);
            context.setContextValue("correctAnswers", 0);
            
            // Return first question - suspend waiting for answer
            TestQuestion firstQuestion = getNextQuestion(context);
            return StepResult.suspend(firstQuestion, TestAnswer.class);
        }
        
        @Step
        public StepResult<?> processTestAnswer(WorkflowContext context, TestAnswer answer) {
            log.info("Processing answer: {}", answer.getSelectedAnswer());
            
            // Check if this is feedback
            String currentCategory = context.getString("currentCategory");
            if ("feedback".equals(currentCategory)) {
                context.setContextValue("feedback", answer.getSelectedAnswer());
                return completeOnboarding(context);
            }
            
            // Process regular answer
            Integer currentIndex = context.getInt("currentQuestionIndex");
            Integer correctAnswers = context.getInt("correctAnswers");
            
            // More complex scoring logic: B and C are correct answers
            if ("Option B".equals(answer.getSelectedAnswer()) || "Option C".equals(answer.getSelectedAnswer())) {
                correctAnswers++;
                context.setContextValue("correctAnswers", correctAnswers);
            }
            
            // Move to next question
            currentIndex++;
            context.setContextValue("currentQuestionIndex", currentIndex);
            
            // Get next question or complete
            if (currentIndex < 3) { // We have 3 test questions
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
            
            switch (index) {
                case 0:
                    question.setCategory("grammar");
                    question.setQuestionText("Which sentence is grammatically correct?");
                    question.setOptions(Arrays.asList("Option A", "Option B", "Option C"));
                    break;
                case 1:
                    question.setCategory("vocabulary");
                    question.setQuestionText("What is the meaning of 'ubiquitous'?");
                    question.setOptions(Arrays.asList("Option A", "Option B", "Option C"));
                    break;
                case 2:
                    question.setCategory("comprehension");
                    question.setQuestionText("What is the main idea of the passage?");
                    question.setOptions(Arrays.asList("Option A", "Option B", "Option C"));
                    break;
            }
            
            question.setCompletionPercentage(calculateProgress(context));
            question.setConfirmationQuestion(false);
            context.setContextValue("currentCategory", question.getCategory());
            
            return question;
        }
        
        private TestQuestion getFeedbackQuestion(WorkflowContext context) {
            TestQuestion feedback = new TestQuestion();
            feedback.setCategory("feedback");
            feedback.setQuestionText("How would you rate your test experience?");
            feedback.setOptions(Arrays.asList("POOR", "GOOD", "EXCELLENT"));
            feedback.setCompletionPercentage(100);
            
            context.setContextValue("currentCategory", "feedback");
            
            return feedback;
        }
        
        private StepResult<OnboardingComplete> completeOnboarding(WorkflowContext context) {
            String selfAssessment = context.getString("selfAssessmentLevel");
            Integer correctAnswers = context.getInt("correctAnswers");
            String feedback = context.getString("feedback");
            
            // Determine level based on correct answers
            String determinedLevel;
            if (correctAnswers >= 3) {
                determinedLevel = "C1";
            } else if (correctAnswers >= 2) {
                determinedLevel = "B1";
            } else if (correctAnswers >= 1) {
                determinedLevel = "A2";
            } else {
                determinedLevel = "A1";
            }
            
            OnboardingComplete complete = new OnboardingComplete();
            complete.setSelfAssessmentLevel(selfAssessment);
            complete.setDeterminedLevel(determinedLevel);
            complete.setFeedback(feedback);
            complete.setCorrectAnswers(correctAnswers);
            complete.setTotalQuestions(3);
            complete.setMessage("Onboarding completed! Your assessed level is " + determinedLevel);
            
            return StepResult.finish(complete);
        }
        
        private int calculateProgress(WorkflowContext context) {
            Integer currentIndex = context.getInt("currentQuestionIndex");
            // 0 questions = 20%, 1 question = 40%, 2 questions = 60%, 3 questions = 80%, feedback = 100%
            return (currentIndex + 1) * 20;
        }
        
        // Data classes
        
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @SchemaName("assessmentPrompt")
        @SchemaDescription("Prompt for language level self-assessment")
        public static class AssessmentPrompt {
            @SchemaProperty(description = "Prompt message to display")
            private String message;
            
            @SchemaProperty(description = "Available level options")
            private List<String> options;
        }
        
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @SchemaName("selfAssessment")
        @SchemaDescription("User's self-assessment of their language level")
        public static class SelfAssessment {
            @SchemaProperty(description = "Selected language level", required = true)
            private String level;
        }
        
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @SchemaName("testAnswer")
        @SchemaDescription("Answer to a test question")
        public static class TestAnswer {
            @SchemaProperty(description = "Selected answer option", required = true)
            private String selectedAnswer;
        }
        
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @SchemaName("onboardingComplete")
        @SchemaDescription("Onboarding completion result")
        public static class OnboardingComplete {
            @SchemaProperty(description = "User's self-assessed level")
            private String selfAssessmentLevel;
            
            @SchemaProperty(description = "System-determined level from test")
            private String determinedLevel;
            
            @SchemaProperty(description = "User's feedback about the test")
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
        @SchemaProperty(description = "Question category (grammar, vocabulary, etc.)")
        private String category;
        
        @SchemaProperty(description = "The question text")
        private String questionText;
        
        @SchemaProperty(description = "Available answer options")
        private List<String> options;
        
        @SchemaProperty(description = "Progress percentage (0-100)")
        private int completionPercentage;
        
        @SchemaProperty(description = "Whether this is a confirmation question")
        private boolean isConfirmationQuestion;
    }
}
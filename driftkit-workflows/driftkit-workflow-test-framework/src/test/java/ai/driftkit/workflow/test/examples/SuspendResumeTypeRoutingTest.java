package ai.driftkit.workflow.test.examples;

import ai.driftkit.workflow.engine.annotations.*;
import ai.driftkit.workflow.engine.core.*;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import ai.driftkit.workflow.engine.chat.ChatContextHelper;
import ai.driftkit.workflow.engine.schema.SchemaName;
import ai.driftkit.workflow.engine.schema.SchemaDescription;
import ai.driftkit.workflow.engine.schema.SchemaProperty;
import ai.driftkit.workflow.test.core.AnnotationWorkflowTest;
import ai.driftkit.common.domain.Language;
import ai.driftkit.common.domain.chat.ChatRequest;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test demonstrating suspend/resume with type-based routing.
 * This test verifies that when a step suspends and expects a specific type on resume,
 * the framework can automatically route to the correct step based on the input type.
 */
@Slf4j
@DisplayName("Suspend/Resume Type-Based Routing Tests")
public class SuspendResumeTypeRoutingTest extends AnnotationWorkflowTest {
    
    private SurveyWorkflow workflow;
    
    @BeforeEach
    void setUp() {
        workflow = new SurveyWorkflow();
        registerWorkflow(workflow);
    }
    
    @Test
    @DisplayName("Should route to correct step after suspend based on input type")
    void testTypeBasedRoutingAfterSuspend() throws Exception {
        // Create ChatRequest with user info
        ChatRequest request = new ChatRequest();
        request.setChatId(UUID.randomUUID().toString());
        request.setUserId("test-user-" + System.currentTimeMillis());
        request.setLanguage(Language.ENGLISH);
        
        // Execute workflow - should suspend asking for assessment
        var execution = executeAndExpectSuspend("survey-workflow", request, Duration.ofSeconds(5));
        String runId = execution.getRunId();
        
        // Verify userId was initialized from ChatRequest
        WorkflowInstance instance = getWorkflowInstance(runId);
        assertNotNull(instance);
        assertEquals(request.getUserId(), ChatContextHelper.getUserId(instance.getContext()));
        
        // Get suspension data
        var context = instance.getContext();
        var lastStepId = context.getLastStepId();
        var welcomeMsg = context.getStepResult(lastStepId, WelcomeMessage.class);
        assertNotNull(welcomeMsg);
        assertEquals("survey.welcome", welcomeMsg.getMessageId());
        
        // Resume with assessment selection
        AssessmentSelection selection = new AssessmentSelection();
        selection.setLevel(AssessmentLevel.INTERMEDIATE);
        
        Object questionResult = resumeWorkflow(runId, selection);
        assertNotNull(questionResult);
        assertTrue(questionResult instanceof QuestionPresentation);
        
        QuestionPresentation question = (QuestionPresentation) questionResult;
        assertEquals("What is your favorite programming language?", question.getQuestionText());
        assertNotNull(question.getOptions());
        assertFalse(question.getOptions().isEmpty());
        
        // Answer the question
        AnswerSubmission answer = new AnswerSubmission();
        answer.setAnswer("Java");
        
        Object result = resumeWorkflow(runId, answer);
        assertNotNull(result);
        
        // The workflow should complete after answering the question
        assertTrue(result instanceof SurveyComplete, 
            "Expected SurveyComplete but got " + result.getClass().getSimpleName());
        
        SurveyComplete complete = (SurveyComplete) result;
        assertEquals(AssessmentLevel.INTERMEDIATE, complete.getAssessmentLevel());
        assertEquals("Java", complete.getFavoriteLanguage());
        assertEquals(request.getUserId(), complete.getUserId());
    }
    
    @Test
    @DisplayName("Should verify step execution flow")
    void testStepExecutionFlow() throws Exception {
        ChatRequest request = new ChatRequest();
        request.setChatId(UUID.randomUUID().toString());
        request.setUserId("flow-test-user");
        request.setLanguage(Language.ENGLISH);
        
        var execution = executeAndExpectSuspend("survey-workflow", request);
        String runId = execution.getRunId();
        
        // Verify initial step executed
        assertions.assertStep("survey-workflow", "startSurvey").wasExecuted();
        
        // Resume with assessment
        AssessmentSelection selection = new AssessmentSelection();
        selection.setLevel(AssessmentLevel.BEGINNER);
        
        resumeWorkflow(runId, selection);
        
        // Verify assessment step executed
        assertions.assertStep("survey-workflow", "processAssessment").wasExecuted();
        
        // Answer question
        AnswerSubmission answer = new AnswerSubmission();
        answer.setAnswer("Python");
        
        resumeWorkflow(runId, answer);
        
        // Verify answer step executed
        assertions.assertStep("survey-workflow", "processAnswer").wasExecuted();
        
        // Verify all steps executed once
        assertions.assertStep("survey-workflow", "startSurvey").wasExecutedTimes(1);
        assertions.assertStep("survey-workflow", "processAssessment").wasExecutedTimes(1);
        assertions.assertStep("survey-workflow", "processAnswer").wasExecutedTimes(1);
    }
    
    /**
     * Test workflow that demonstrates type-based routing after suspend.
     * IMPORTANT: Initial step that suspends should NOT have nextSteps annotation
     * when using type-based routing for resume.
     */
    @Workflow(
        id = "survey-workflow",
        version = "1.0",
        description = "Survey workflow with suspend/resume and type-based routing"
    )
    public static class SurveyWorkflow {
        
        @InitialStep
        // NOTE: No @Step(nextSteps = {...}) here! 
        // The framework will use type-based routing when resumed
        public StepResult<WelcomeMessage> startSurvey(WorkflowContext context) {
            log.info("Starting survey workflow");
            
            String userId = ChatContextHelper.getUserId(context);
            log.info("User ID from context: {}", userId);
            
            WelcomeMessage welcome = new WelcomeMessage();
            welcome.setMessageId("survey.welcome");
            welcome.setProperties(Map.of(
                "userId", userId != null ? userId : "unknown"
            ));
            
            // Store survey start time
            context.setStepOutput("surveyStartTime", System.currentTimeMillis());
            
            // Suspend and expect AssessmentSelection when resumed
            return StepResult.suspend(welcome, AssessmentSelection.class);
        }
        
        @Step(
            id = "processAssessment",
            nextClasses = {QuestionPresentation.class}
        )
        public StepResult<QuestionPresentation> processAssessment(
                WorkflowContext context, 
                AssessmentSelection selection) {
            log.info("Processing assessment level: {}", selection.getLevel());
            
            // Store assessment in context
            context.setStepOutput("assessmentLevel", selection.getLevel());
            
            // Present question based on level
            QuestionPresentation question = new QuestionPresentation();
            question.setQuestionId("q1");
            question.setQuestionText("What is your favorite programming language?");
            question.setOptions(Arrays.asList("Java", "Python", "JavaScript", "Go", "Rust"));
            
            // Suspend and expect AnswerSubmission when resumed
            return StepResult.suspend(question, AnswerSubmission.class);
        }
        
        @Step(
            id = "processAnswer",
            nextClasses = {SurveyComplete.class}
        )
        public StepResult<SurveyComplete> processAnswer(
                WorkflowContext context, 
                AnswerSubmission answer) {
            log.info("Processing answer: {}", answer.getAnswer());
            
            // Get data from context
            Long startTime = context.getStepResult("surveyStartTime", Long.class);
            AssessmentLevel level = context.getStepResult("assessmentLevel", AssessmentLevel.class);
            String userId = ChatContextHelper.getUserId(context);
            
            // Complete survey
            SurveyComplete complete = new SurveyComplete();
            complete.setUserId(userId);
            complete.setAssessmentLevel(level);
            complete.setFavoriteLanguage(answer.getAnswer());
            complete.setCompletionTime(System.currentTimeMillis() - startTime);
            complete.setMessage("Thank you for completing the survey!");
            
            return StepResult.finish(complete);
        }
    }
    
    // Domain classes
    
    public enum AssessmentLevel {
        BEGINNER,
        INTERMEDIATE,
        ADVANCED,
        EXPERT
    }
    
    @Data
    @SchemaName("WelcomeMessage")
    @SchemaDescription("Initial welcome message")
    public static class WelcomeMessage {
        @SchemaProperty(description = "Message identifier", required = true)
        private String messageId;
        
        @SchemaProperty(description = "Additional properties")
        private Map<String, Object> properties;
    }
    
    @Data
    @SchemaName("AssessmentSelection")
    @SchemaDescription("User's self-assessment selection")
    public static class AssessmentSelection {
        @SchemaProperty(description = "Selected assessment level", required = true)
        private AssessmentLevel level;
    }
    
    @Data
    @SchemaName("QuestionPresentation")
    @SchemaDescription("Question to present to user")
    public static class QuestionPresentation {
        @SchemaProperty(description = "Question ID", required = true)
        private String questionId;
        
        @SchemaProperty(description = "Question text", required = true)
        private String questionText;
        
        @SchemaProperty(description = "Available options")
        private List<String> options;
    }
    
    @Data
    @SchemaName("AnswerSubmission")
    @SchemaDescription("User's answer submission")
    public static class AnswerSubmission {
        @SchemaProperty(description = "Selected answer", required = true)
        private String answer;
    }
    
    @Data
    @SchemaName("SurveyComplete")
    @SchemaDescription("Survey completion data")
    public static class SurveyComplete {
        @SchemaProperty(description = "User ID")
        private String userId;
        
        @SchemaProperty(description = "Assessment level")
        private AssessmentLevel assessmentLevel;
        
        @SchemaProperty(description = "Favorite programming language")
        private String favoriteLanguage;
        
        @SchemaProperty(description = "Time to complete in milliseconds")
        private Long completionTime;
        
        @SchemaProperty(description = "Completion message")
        private String message;
    }
}
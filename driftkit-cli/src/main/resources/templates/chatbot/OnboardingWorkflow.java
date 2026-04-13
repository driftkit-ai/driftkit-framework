package /*PACKAGE_NAME*/.workflow;

import ai.driftkit.workflow.engine.annotations.InitialStep;
import ai.driftkit.workflow.engine.annotations.Step;
import ai.driftkit.workflow.engine.annotations.Workflow;
import ai.driftkit.workflow.engine.core.StepResult;
import ai.driftkit.workflow.engine.core.WorkflowContext;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@Workflow(id = "user-onboarding", version = "1.0", 
          description = "Multi-step user onboarding workflow with human review")
public class OnboardingWorkflow {

    // Sealed interface for type-safe workflow branching
    public sealed interface OnboardingState 
        permits CollectingBasicInfo, CollectingPreferences, WaitingForReview, OnboardingComplete {}
    
    public record CollectingBasicInfo() implements OnboardingState {}
    
    public record CollectingPreferences(BasicInfo basicInfo) implements OnboardingState {}
    
    public record WaitingForReview(BasicInfo basicInfo, Preferences preferences) implements OnboardingState {}
    
    public record OnboardingComplete(String userId, String message) implements OnboardingState {}

    // Input data structures
    @Data
    public static class OnboardingInput {
        private String sessionId;
        private String intent;
    }

    @Data
    public static class BasicInfo {
        private String name;
        private String email;
        private String company;
    }

    @Data
    public static class Preferences {
        private String language;
        private boolean notifications;
        private String theme;
    }

    @Data
    public static class ReviewResponse {
        private boolean approved;
        private String userId;
        private String comments;
    }

    @InitialStep
    public StepResult<OnboardingState> startOnboarding(OnboardingInput input, WorkflowContext context) {
        log.info("Starting onboarding workflow for session: {}", input.getSessionId());
        
        // Suspend to collect basic info
        return new StepResult.Suspend<>(
            "Welcome to our platform! Let's start by collecting some basic information. " +
            "Please provide your name, email, and company.",
            Map.of("step", "collect-basic-info", "sessionId", input.getSessionId())
        );
    }

    @Step
    public StepResult<OnboardingState> collectBasicInfo(BasicInfo input, WorkflowContext context) {
        log.info("Collecting basic user info: {}", input);
        
        // Validate input
        if (input.getName() == null || input.getEmail() == null) {
            return new StepResult.Suspend<>(
                "Please provide both your name and email address.",
                Map.of("step", "collect-basic-info", "retry", true)
            );
        }
        
        // Continue to preferences collection
        return new StepResult.Continue<>(new CollectingPreferences(input));
    }

    @Step
    public StepResult<OnboardingState> collectPreferences(CollectingPreferences state, WorkflowContext context) {
        log.info("Moving to preferences collection for user: {}", state.basicInfo().getName());
        
        // Suspend to collect preferences
        return new StepResult.Suspend<>(
            "Thank you! Now let's set up your preferences. " +
            "Please specify your language preference, notification settings, and theme.",
            Map.of("step", "setup-preferences", "email", state.basicInfo().getEmail())
        );
    }

    @Step
    public StepResult<OnboardingState> setupPreferences(Preferences input, WorkflowContext context) {
        log.info("Setting up preferences: {}", input);
        
        // Get basic info from previous step
        BasicInfo basicInfo = context.getStepResult("collectBasicInfo", BasicInfo.class);
        
        // Move to review state
        return new StepResult.Continue<>(new WaitingForReview(basicInfo, input));
    }

    @Step
    public StepResult<OnboardingState> initiateReview(WaitingForReview state, WorkflowContext context) {
        log.info("Initiating human review for user: {}", state.basicInfo().getEmail());
        
        // Create review request
        Map<String, Object> reviewData = Map.of(
            "userName", state.basicInfo().getName(),
            "email", state.basicInfo().getEmail(),
            "company", state.basicInfo().getCompany(),
            "language", state.preferences().getLanguage(),
            "notifications", state.preferences().isNotifications(),
            "theme", state.preferences().getTheme(),
            "requiresReview", true
        );
        
        // Suspend for human review
        return new StepResult.Suspend<>(
            "Great! Your profile is being reviewed by our team. You'll receive a notification once approved.",
            reviewData
        );
    }

    @Step
    public StepResult<OnboardingState> processReviewResult(ReviewResponse review, WorkflowContext context) {
        log.info("Processing review result: approved={}, userId={}", review.isApproved(), review.getUserId());
        
        if (!review.isApproved()) {
            return new StepResult.Finish<>(
                new OnboardingComplete(null, "Your application was not approved. " + review.getComments())
            );
        }
        
        return new StepResult.Continue<>(
            new OnboardingComplete(review.getUserId(), "Welcome aboard! Your account is now active.")
        );
    }

    @Step
    public StepResult<Map<String, Object>> completeOnboarding(OnboardingComplete state, WorkflowContext context) {
        log.info("Completing onboarding for user: {}", state.userId());
        
        Map<String, Object> result = Map.of(
            "success", state.userId() != null,
            "userId", state.userId() != null ? state.userId() : "",
            "message", state.message(),
            "workflowRunId", context.runId()
        );
        
        return new StepResult.Finish<>(result);
    }
}
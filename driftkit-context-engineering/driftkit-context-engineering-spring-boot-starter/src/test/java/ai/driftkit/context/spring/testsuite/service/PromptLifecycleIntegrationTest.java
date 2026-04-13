package ai.driftkit.context.spring.testsuite.service;

import ai.driftkit.common.domain.Language;
import ai.driftkit.common.domain.Prompt;
import ai.driftkit.common.domain.Prompt.State;
import ai.driftkit.context.core.service.PromptServiceBase;
import ai.driftkit.context.spring.testsuite.domain.EvaluationRun;
import ai.driftkit.context.spring.testsuite.domain.PromptMethodConfig;
import ai.driftkit.context.spring.testsuite.repository.PromptMethodConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests the prompt lifecycle integration logic that auto-promotes/rejects
 * prompts based on test run results.
 *
 * Tests handlePromptLifecycleAfterRun() via reflection since it's private.
 */
@ExtendWith(MockitoExtension.class)
class PromptLifecycleIntegrationTest {

    @Mock private PromptServiceBase promptService;
    @Mock private PromptMethodConfigRepository configRepository;

    private Object evaluationService;
    private Method handleLifecycleMethod;

    @BeforeEach
    void setUp() throws Exception {
        // Create EvaluationService with nulls for dependencies we don't need
        var constructor = EvaluationService.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);

        // EvaluationService has @RequiredArgsConstructor with many deps
        // We only need promptService and promptMethodConfigRepository
        // Create instance and inject via reflection
        evaluationService = constructor.newInstance(
                null, null, null, null, null, null, null, null,
                promptService, configRepository);

        handleLifecycleMethod = EvaluationService.class.getDeclaredMethod(
                "handlePromptLifecycleAfterRun", EvaluationRun.class, EvaluationRun.RunStatus.class);
        handleLifecycleMethod.setAccessible(true);
    }

    private void invokeHandleLifecycle(EvaluationRun run, EvaluationRun.RunStatus status) throws Exception {
        handleLifecycleMethod.invoke(evaluationService, run, status);
    }

    @Nested
    class CompletedRun {

        @Test
        void allPassed_noManualReview_autoPromotesToCurrent() throws Exception {
            Prompt draft = makeDraftPrompt("linked-id", "method1");

            when(promptService.getPromptById("linked-id")).thenReturn(Optional.of(draft));
            when(configRepository.findById("method1")).thenReturn(Optional.of(
                    PromptMethodConfig.builder().method("method1").requireManualReview(false).build()));
            when(promptService.getPromptsByMethodsAndState(List.of("method1"), State.CURRENT))
                    .thenReturn(List.of());
            when(promptService.savePrompt(any())).thenAnswer(inv -> inv.getArgument(0));

            EvaluationRun run = makeRun("linked-id");

            invokeHandleLifecycle(run, EvaluationRun.RunStatus.COMPLETED);

            ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
            verify(promptService).savePrompt(captor.capture());
            assertEquals(State.CURRENT, captor.getValue().getState());
        }

        @Test
        void allPassed_manualReviewRequired_setsManualTesting() throws Exception {
            Prompt draft = makeDraftPrompt("linked-id", "method1");

            when(promptService.getPromptById("linked-id")).thenReturn(Optional.of(draft));
            when(configRepository.findById("method1")).thenReturn(Optional.of(
                    PromptMethodConfig.builder().method("method1").requireManualReview(true).build()));
            when(promptService.savePrompt(any())).thenAnswer(inv -> inv.getArgument(0));

            invokeHandleLifecycle(makeRun("linked-id"), EvaluationRun.RunStatus.COMPLETED);

            ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
            verify(promptService).savePrompt(captor.capture());
            assertEquals(State.MANUAL_TESTING, captor.getValue().getState());
        }

        @Test
        void autoPromote_replacesOldCurrent() throws Exception {
            Prompt draft = makeDraftPrompt("new-id", "method1");
            Prompt oldCurrent = new Prompt();
            oldCurrent.setId("old-id");
            oldCurrent.setMethod("method1");
            oldCurrent.setState(State.CURRENT);
            oldCurrent.setLanguage(Language.GENERAL);

            when(promptService.getPromptById("new-id")).thenReturn(Optional.of(draft));
            when(configRepository.findById("method1")).thenReturn(Optional.empty()); // no config = no manual review
            when(promptService.getPromptsByMethodsAndState(List.of("method1"), State.CURRENT))
                    .thenReturn(List.of(oldCurrent));
            when(promptService.savePrompt(any())).thenAnswer(inv -> inv.getArgument(0));

            invokeHandleLifecycle(makeRun("new-id"), EvaluationRun.RunStatus.COMPLETED);

            ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
            verify(promptService, times(2)).savePrompt(captor.capture());

            // First save: old prompt → REPLACED
            assertEquals(State.REPLACED, captor.getAllValues().get(0).getState());
            assertEquals("old-id", captor.getAllValues().get(0).getId());

            // Second save: new prompt → CURRENT
            assertEquals(State.CURRENT, captor.getAllValues().get(1).getState());
        }
    }

    @Nested
    class FailedRun {

        @Test
        void failed_revertsToDraft() throws Exception {
            Prompt testing = makeDraftPrompt("linked-id", "method1");
            testing.setState(State.AUTO_TESTING);

            when(promptService.getPromptById("linked-id")).thenReturn(Optional.of(testing));
            when(promptService.savePrompt(any())).thenAnswer(inv -> inv.getArgument(0));

            invokeHandleLifecycle(makeRun("linked-id"), EvaluationRun.RunStatus.FAILED);

            ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
            verify(promptService).savePrompt(captor.capture());
            assertEquals(State.DRAFT, captor.getValue().getState());
        }
    }

    @Nested
    class PendingRun {

        @Test
        void pending_setsManualTesting() throws Exception {
            Prompt testing = makeDraftPrompt("linked-id", "method1");
            testing.setState(State.AUTO_TESTING);

            when(promptService.getPromptById("linked-id")).thenReturn(Optional.of(testing));
            when(promptService.savePrompt(any())).thenAnswer(inv -> inv.getArgument(0));

            invokeHandleLifecycle(makeRun("linked-id"), EvaluationRun.RunStatus.PENDING);

            ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
            verify(promptService).savePrompt(captor.capture());
            assertEquals(State.MANUAL_TESTING, captor.getValue().getState());
        }
    }

    @Nested
    class NoLinkedPrompt {

        @Test
        void noLinkedPromptId_doesNothing() throws Exception {
            EvaluationRun run = new EvaluationRun();
            run.setId("run1");
            // linkedPromptId is null

            invokeHandleLifecycle(run, EvaluationRun.RunStatus.COMPLETED);

            verify(promptService, never()).getPromptById(any());
            verify(promptService, never()).savePrompt(any());
        }

        @Test
        void promptNotInAutoTesting_doesNothing() throws Exception {
            Prompt current = new Prompt();
            current.setId("id1");
            current.setState(State.CURRENT); // not AUTO_TESTING

            when(promptService.getPromptById("id1")).thenReturn(Optional.of(current));

            invokeHandleLifecycle(makeRun("id1"), EvaluationRun.RunStatus.COMPLETED);

            verify(promptService, never()).savePrompt(any());
        }
    }

    // --- Helpers ---

    private Prompt makeDraftPrompt(String id, String method) {
        Prompt p = new Prompt();
        p.setId(id);
        p.setMethod(method);
        p.setState(State.AUTO_TESTING);
        p.setLanguage(Language.GENERAL);
        return p;
    }

    private EvaluationRun makeRun(String linkedPromptId) {
        EvaluationRun run = new EvaluationRun();
        run.setId("run-1");
        run.setLinkedPromptId(linkedPromptId);
        return run;
    }
}

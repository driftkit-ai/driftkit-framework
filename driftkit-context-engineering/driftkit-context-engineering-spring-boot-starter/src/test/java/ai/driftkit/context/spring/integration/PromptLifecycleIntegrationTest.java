package ai.driftkit.context.spring.integration;

import ai.driftkit.common.domain.Language;
import ai.driftkit.common.domain.Prompt;
import ai.driftkit.common.domain.Prompt.State;
import ai.driftkit.context.core.service.PromptService;
import ai.driftkit.context.spring.testsuite.domain.EvaluationRun;
import ai.driftkit.context.spring.testsuite.domain.PromptMethodConfig;
import ai.driftkit.context.spring.testsuite.repository.PromptMethodConfigRepository;
import ai.driftkit.context.spring.testsuite.service.EvaluationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests handlePromptLifecycleAfterRun() — the core logic that connects
 * test results to prompt state changes.
 */
@ExtendWith(MockitoExtension.class)
class PromptLifecycleIntegrationTest {

    @Mock private PromptService promptService;
    @Mock private PromptMethodConfigRepository configRepository;

    private EvaluationService evaluationService;
    private Method handleLifecycleMethod;

    @BeforeEach
    void setUp() throws Exception {
        evaluationService = createServiceWithInjectedDeps();
        handleLifecycleMethod = EvaluationService.class.getDeclaredMethod(
                "handlePromptLifecycleAfterRun", EvaluationRun.class, EvaluationRun.RunStatus.class);
        handleLifecycleMethod.setAccessible(true);
    }

    private EvaluationService createServiceWithInjectedDeps() throws Exception {
        // @RequiredArgsConstructor generates a constructor with all final fields in declaration order.
        // We pass our mocks for promptService and configRepository, null for everything else.
        var ctor = EvaluationService.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        var paramTypes = ctor.getParameterTypes();
        Object[] args = new Object[paramTypes.length];

        // Find and fill the right parameter positions by type
        for (int i = 0; i < paramTypes.length; i++) {
            if (paramTypes[i].isAssignableFrom(promptService.getClass())) {
                args[i] = promptService;
            } else if (paramTypes[i].isAssignableFrom(configRepository.getClass())) {
                args[i] = configRepository;
            }
        }

        return (EvaluationService) ctor.newInstance(args);
    }

    private void invoke(EvaluationRun run, EvaluationRun.RunStatus status) throws Exception {
        handleLifecycleMethod.invoke(evaluationService, run, status);
    }

    @Nested
    class CompletedNoManualReview {
        @Test
        void autoPromotesToCurrent() throws Exception {
            Prompt prompt = makeTestingPrompt("p1", "m1");
            when(promptService.getPromptById("p1")).thenReturn(Optional.of(prompt));
            when(configRepository.findById("m1")).thenReturn(Optional.of(
                    PromptMethodConfig.builder().method("m1").requireManualReview(false).build()));
            when(promptService.getPromptsByMethodsAndState(List.of("m1"), State.CURRENT)).thenReturn(List.of());
            when(promptService.savePrompt(any())).thenAnswer(inv -> inv.getArgument(0));

            invoke(makeRun("p1"), EvaluationRun.RunStatus.COMPLETED);

            ArgumentCaptor<Prompt> c = ArgumentCaptor.forClass(Prompt.class);
            verify(promptService).savePrompt(c.capture());
            assertEquals(State.CURRENT, c.getValue().getState());
        }

        @Test
        void replacesOldCurrent() throws Exception {
            Prompt newP = makeTestingPrompt("new", "m1");
            Prompt old = new Prompt(); old.setId("old"); old.setMethod("m1"); old.setState(State.CURRENT); old.setLanguage(Language.GENERAL);

            when(promptService.getPromptById("new")).thenReturn(Optional.of(newP));
            when(configRepository.findById("m1")).thenReturn(Optional.empty());
            when(promptService.getPromptsByMethodsAndState(List.of("m1"), State.CURRENT)).thenReturn(List.of(old));
            when(promptService.savePrompt(any())).thenAnswer(inv -> inv.getArgument(0));

            invoke(makeRun("new"), EvaluationRun.RunStatus.COMPLETED);

            ArgumentCaptor<Prompt> c = ArgumentCaptor.forClass(Prompt.class);
            verify(promptService, times(2)).savePrompt(c.capture());
            assertEquals(State.REPLACED, c.getAllValues().get(0).getState());
            assertEquals(State.CURRENT, c.getAllValues().get(1).getState());
        }
    }

    @Nested
    class CompletedWithManualReview {
        @Test
        void setsManualTesting() throws Exception {
            Prompt p = makeTestingPrompt("p1", "m1");
            when(promptService.getPromptById("p1")).thenReturn(Optional.of(p));
            when(configRepository.findById("m1")).thenReturn(Optional.of(
                    PromptMethodConfig.builder().method("m1").requireManualReview(true).build()));
            when(promptService.savePrompt(any())).thenAnswer(inv -> inv.getArgument(0));

            invoke(makeRun("p1"), EvaluationRun.RunStatus.COMPLETED);

            ArgumentCaptor<Prompt> c = ArgumentCaptor.forClass(Prompt.class);
            verify(promptService).savePrompt(c.capture());
            assertEquals(State.MANUAL_TESTING, c.getValue().getState());
        }
    }

    @Nested
    class FailedRun {
        @Test
        void revertsToDraft() throws Exception {
            Prompt p = makeTestingPrompt("p1", "m1");
            when(promptService.getPromptById("p1")).thenReturn(Optional.of(p));
            when(promptService.savePrompt(any())).thenAnswer(inv -> inv.getArgument(0));

            invoke(makeRun("p1"), EvaluationRun.RunStatus.FAILED);

            ArgumentCaptor<Prompt> c = ArgumentCaptor.forClass(Prompt.class);
            verify(promptService).savePrompt(c.capture());
            assertEquals(State.DRAFT, c.getValue().getState());
        }
    }

    @Nested
    class PendingRun {
        @Test
        void setsManualTesting() throws Exception {
            Prompt p = makeTestingPrompt("p1", "m1");
            when(promptService.getPromptById("p1")).thenReturn(Optional.of(p));
            when(promptService.savePrompt(any())).thenAnswer(inv -> inv.getArgument(0));

            invoke(makeRun("p1"), EvaluationRun.RunStatus.PENDING);

            ArgumentCaptor<Prompt> c = ArgumentCaptor.forClass(Prompt.class);
            verify(promptService).savePrompt(c.capture());
            assertEquals(State.MANUAL_TESTING, c.getValue().getState());
        }
    }

    @Nested
    class EdgeCases {
        @Test void noLinkedId_doesNothing() throws Exception {
            EvaluationRun run = new EvaluationRun(); run.setId("r1");
            invoke(run, EvaluationRun.RunStatus.COMPLETED);
            verify(promptService, never()).getPromptById(any());
        }

        @Test void notAutoTesting_doesNothing() throws Exception {
            Prompt p = new Prompt(); p.setId("p1"); p.setState(State.CURRENT);
            when(promptService.getPromptById("p1")).thenReturn(Optional.of(p));
            invoke(makeRun("p1"), EvaluationRun.RunStatus.COMPLETED);
            verify(promptService, never()).savePrompt(any());
        }

        @Test void promptNotFound_doesNothing() throws Exception {
            when(promptService.getPromptById("gone")).thenReturn(Optional.empty());
            invoke(makeRun("gone"), EvaluationRun.RunStatus.COMPLETED);
            verify(promptService, never()).savePrompt(any());
        }
    }

    private Prompt makeTestingPrompt(String id, String method) {
        Prompt p = new Prompt(); p.setId(id); p.setMethod(method);
        p.setState(State.AUTO_TESTING); p.setLanguage(Language.GENERAL); return p;
    }

    private EvaluationRun makeRun(String linkedPromptId) {
        EvaluationRun r = new EvaluationRun(); r.setId("run-1"); r.setLinkedPromptId(linkedPromptId); return r;
    }
}

package ai.driftkit.context.services;

import ai.driftkit.common.domain.Language;
import ai.driftkit.common.domain.Prompt;
import ai.driftkit.common.domain.Prompt.State;
import ai.driftkit.context.services.repository.PromptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MongodbPromptServiceTest {

    @Mock
    private PromptRepository repository;

    private MongodbPromptService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new MongodbPromptService();
        // Inject mock repository via reflection (bypasses ApplicationContextProvider)
        Field repoField = MongodbPromptService.class.getDeclaredField("promptRepository");
        repoField.setAccessible(true);
        repoField.set(service, repository);
    }

    @Nested
    class SavePrompt {

        @Test
        void firstVersion_setsVersion1() {
            when(repository.findByMethodAndLanguageAndState("test", Language.GENERAL, State.CURRENT))
                    .thenReturn(Optional.empty());
            when(repository.findByMethod("test")).thenReturn(List.of());
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Prompt prompt = new Prompt();
            prompt.setMethod("test");
            prompt.setMessage("hello");
            prompt.setLanguage(Language.GENERAL);

            Prompt saved = service.savePrompt(prompt);

            assertEquals(1, saved.getVersion());
            assertNotNull(saved.getId());
            assertEquals(State.CURRENT, saved.getState());
        }

        @Test
        void differentMessage_incrementsVersionAndReplacesOld() {
            Prompt existing = new Prompt();
            existing.setId("old-id");
            existing.setMethod("test");
            existing.setMessage("old text");
            existing.setVersion(3);
            existing.setState(State.CURRENT);
            existing.setLanguage(Language.GENERAL);

            when(repository.findByMethodAndLanguageAndState("test", Language.GENERAL, State.CURRENT))
                    .thenReturn(Optional.of(existing));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Prompt newPrompt = new Prompt();
            newPrompt.setMethod("test");
            newPrompt.setMessage("new text");
            newPrompt.setLanguage(Language.GENERAL);

            Prompt saved = service.savePrompt(newPrompt);

            // New prompt gets version 4
            assertEquals(4, saved.getVersion());
            assertNotEquals("old-id", saved.getId());

            // Old prompt was saved as REPLACED
            ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
            verify(repository, times(2)).save(captor.capture());
            Prompt replacedSave = captor.getAllValues().get(0);
            assertEquals(State.REPLACED, replacedSave.getState());
            assertEquals("old-id", replacedSave.getId());
        }

        @Test
        void sameMessage_reusesIdAndVersion() {
            Prompt existing = new Prompt();
            existing.setId("existing-id");
            existing.setMethod("test");
            existing.setMessage("same text");
            existing.setVersion(5);
            existing.setState(State.CURRENT);
            existing.setLanguage(Language.GENERAL);

            when(repository.findByMethodAndLanguageAndState("test", Language.GENERAL, State.CURRENT))
                    .thenReturn(Optional.of(existing));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Prompt newPrompt = new Prompt();
            newPrompt.setMethod("test");
            newPrompt.setMessage("same text");
            newPrompt.setLanguage(Language.GENERAL);

            Prompt saved = service.savePrompt(newPrompt);

            assertEquals("existing-id", saved.getId());
            assertEquals(5, saved.getVersion());
            // Only one save call (no REPLACED save)
            verify(repository, times(1)).save(any());
        }

        @Test
        void nullState_defaultsToCurrent() {
            when(repository.findByMethodAndLanguageAndState(any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(repository.findByMethod(any())).thenReturn(List.of());
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Prompt prompt = new Prompt();
            prompt.setMethod("test");
            prompt.setMessage("text");
            prompt.setLanguage(Language.GENERAL);
            prompt.setState(null);

            Prompt saved = service.savePrompt(prompt);

            assertEquals(State.CURRENT, saved.getState());
        }

        @Test
        void explicitDraftState_preserved() {
            when(repository.findByMethodAndLanguageAndState(any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(repository.findByMethod(any())).thenReturn(List.of());
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Prompt prompt = new Prompt();
            prompt.setMethod("test");
            prompt.setMessage("draft text");
            prompt.setLanguage(Language.GENERAL);
            prompt.setState(State.DRAFT);

            Prompt saved = service.savePrompt(prompt);

            assertEquals(State.DRAFT, saved.getState());
        }

        @Test
        void existingReplacedVersions_maxVersionCalculated() {
            Prompt replaced1 = new Prompt();
            replaced1.setVersion(2);
            replaced1.setLanguage(Language.GENERAL);
            Prompt replaced2 = new Prompt();
            replaced2.setVersion(5);
            replaced2.setLanguage(Language.GENERAL);

            when(repository.findByMethodAndLanguageAndState("test", Language.GENERAL, State.CURRENT))
                    .thenReturn(Optional.empty());
            when(repository.findByMethod("test")).thenReturn(List.of(replaced1, replaced2));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Prompt prompt = new Prompt();
            prompt.setMethod("test");
            prompt.setMessage("new");
            prompt.setLanguage(Language.GENERAL);

            Prompt saved = service.savePrompt(prompt);

            assertEquals(6, saved.getVersion()); // max(2,5) + 1
        }
    }

    @Nested
    class DeletePrompt {

        @Test
        void deleteCurrent_promotesLatestReplaced() {
            Prompt current = new Prompt();
            current.setId("current-id");
            current.setMethod("test");
            current.setState(State.CURRENT);
            current.setLanguage(Language.GENERAL);

            Prompt replaced1 = new Prompt();
            replaced1.setId("old1");
            replaced1.setLanguage(Language.GENERAL);
            replaced1.setUpdatedTime(1000);

            Prompt replaced2 = new Prompt();
            replaced2.setId("old2");
            replaced2.setLanguage(Language.GENERAL);
            replaced2.setUpdatedTime(2000); // newer

            when(repository.findById("current-id")).thenReturn(Optional.of(current));
            when(repository.findByMethodAndState("test", State.REPLACED))
                    .thenReturn(List.of(replaced1, replaced2));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Prompt deleted = service.deletePrompt("current-id");

            assertEquals("current-id", deleted.getId());
            verify(repository).deleteById("current-id");

            // replaced2 (newer) should be promoted to CURRENT
            ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
            verify(repository).save(captor.capture());
            assertEquals("old2", captor.getValue().getId());
            assertEquals(State.CURRENT, captor.getValue().getState());
        }

        @Test
        void deleteReplaced_noPromotion() {
            Prompt replaced = new Prompt();
            replaced.setId("replaced-id");
            replaced.setMethod("test");
            replaced.setState(State.REPLACED);

            when(repository.findById("replaced-id")).thenReturn(Optional.of(replaced));

            service.deletePrompt("replaced-id");

            verify(repository).deleteById("replaced-id");
            // No promotion needed — verify save never called
            verify(repository, never()).save(any());
        }

        @Test
        void deleteNonExistent_returnsNull() {
            when(repository.findById("nope")).thenReturn(Optional.empty());

            Prompt result = service.deletePrompt("nope");

            assertNull(result);
            verify(repository, never()).deleteById(any());
        }

        @Test
        void deleteCurrent_noReplacedExists_noPromotion() {
            Prompt current = new Prompt();
            current.setId("cur");
            current.setMethod("test");
            current.setState(State.CURRENT);
            current.setLanguage(Language.GENERAL);

            when(repository.findById("cur")).thenReturn(Optional.of(current));
            when(repository.findByMethodAndState("test", State.REPLACED)).thenReturn(List.of());

            service.deletePrompt("cur");

            verify(repository).deleteById("cur");
            verify(repository, never()).save(any());
        }
    }
}

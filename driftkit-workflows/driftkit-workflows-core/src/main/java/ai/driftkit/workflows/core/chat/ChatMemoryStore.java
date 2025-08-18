package ai.driftkit.workflows.core.chat;

import java.util.List;

/**
 * ChatMemoryStore defines the interface for persisting chat messages.
 */
public interface ChatMemoryStore {
    List<Message> getMessages(String id, int limit);
    void updateMessages(String id, List<Message> messages);
    void deleteMessages(String id);
}

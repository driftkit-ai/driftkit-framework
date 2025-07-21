package ai.driftkit.common.service;

import ai.driftkit.common.domain.Message;

import java.util.List;

/**
 * ChatMemoryStore defines the interface for persisting chat messages.
 */
public interface ChatMemoryStore {
    List<Message> getMessages(String id, int limit);
    void updateMessages(String id, List<Message> messages);
    void deleteMessages(String id);
}

package ai.driftkit.common.service;

import ai.driftkit.common.domain.Message;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * InMemoryChatMemoryStore is a simple in-memory implementation of ChatMemoryStore.
 */
public class InMemoryChatMemoryStore implements ChatMemoryStore {
    private final Map<String, List<Message>> store = new HashMap<>();

    @Override
    public List<Message> getMessages(String id, int limit) {
        return store.getOrDefault(id, new LinkedList<>());
    }

    @Override
    public void updateMessages(String id, List<Message> messages) {
        store.put(id, new LinkedList<>(messages));
    }

    @Override
    public void deleteMessages(String id) {
        store.remove(id);
    }
}

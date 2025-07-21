package ai.driftkit.common.service;

import ai.driftkit.common.domain.Message;

import java.util.List;

/**
 * ChatMemory defines the contract for a chat memory system.
 */
public interface ChatMemory {
    Object id();

    void add(Message message);

    List<Message> messages();

    void clear();
}

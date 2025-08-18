package ai.driftkit.workflows.core.chat;

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

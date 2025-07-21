package ai.driftkit.workflows.spring.service;

import ai.driftkit.common.domain.*;
import ai.driftkit.common.service.ChatMemory;
import ai.driftkit.common.service.TokenWindowChatMemory;
import ai.driftkit.common.utils.SimpleTokenizer;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Sort.Direction;

import java.util.List;
import java.util.Optional;

public class LLMMemoryProvider {

    private final ChatService chatService;
    private final TasksService tasksService;

    public LLMMemoryProvider(ChatService chatService, TasksService tasksService) {
        this.chatService = chatService;
        this.tasksService = tasksService;
    }

    public void update(String chatId, List<MessageTask> messages) {
        tasksService.saveTasks(chatId, messages);
    }

    public ChatMemory get(String chatId) {
        Optional<Chat> chatOpt = chatService.getChat(String.valueOf(chatId));

        if (chatOpt.isEmpty() || chatOpt.get().getMemoryLength() == 0) {
            return TokenWindowChatMemory.withMaxTokens(1000, new SimpleTokenizer());
        }

        Chat chat = chatOpt.get();

        ChatMemory chatMemory = TokenWindowChatMemory.withMaxTokens(
                chat.getMemoryLength(),
                new SimpleTokenizer()
        );

        if (StringUtils.isNotBlank(chat.getSystemMessage())) {
            chatMemory.add(new Message(
                    null,
                    chat.getSystemMessage(),
                    ChatMessageType.SYSTEM,
                    MessageType.TEXT,
                    null,
                    null,
                    null,
                    null,
                    null,
                    chat.getCreatedTime(),
                    chat.getCreatedTime(),
                    null
            ));
        }

        List<Message> messages = tasksService.getMessagesList(chat.getChatId(), 0, 300, Direction.DESC);

        for (Message message : messages) {
            chatMemory.add(message);
        }

        return chatMemory;
    }
}

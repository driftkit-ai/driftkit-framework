package ai.driftkit.workflows.spring.service;

import ai.driftkit.common.domain.Chat;
import ai.driftkit.common.domain.ChatRequest;
import ai.driftkit.common.domain.Language;
import ai.driftkit.workflows.spring.domain.ChatEntity;
import ai.driftkit.workflows.spring.repository.ChatRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ChatService {
    public static final String SYSTEM_CHAT_ID = "system";
    public static final String SYSTEM_ENGLISH_CHAT_ID = "system_eng";

    @Autowired
    private ChatRepository chatRepository;

    @PostConstruct
    public void init() {
        Optional<ChatEntity> systemChat = chatRepository.findById(SYSTEM_CHAT_ID);
        save(Chat.builder()
                .chatId(SYSTEM_ENGLISH_CHAT_ID)
                .language(Language.ENGLISH)
                .createdTime(System.currentTimeMillis())
                .systemMessage(null)
                .name(SYSTEM_ENGLISH_CHAT_ID)
                .hidden(true)
                .build());

        if (systemChat.isPresent()) {
            return;
        }

        save(Chat.builder()
                .chatId(SYSTEM_CHAT_ID)
                .language(Language.SPANISH)
                .createdTime(System.currentTimeMillis())
                .systemMessage(null)
                .name(SYSTEM_CHAT_ID)
                .hidden(true)
                .build());

        save(Chat.builder()
                .chatId(SYSTEM_ENGLISH_CHAT_ID)
                .language(Language.ENGLISH)
                .createdTime(System.currentTimeMillis())
                .systemMessage(null)
                .name(SYSTEM_ENGLISH_CHAT_ID)
                .hidden(true)
                .build());
    }

    public List<Chat> getChats() {
        return chatRepository.findChatsByHiddenIsFalse()
                .stream()
                .map(ChatEntity::toChat)
                .toList();
    }

    public Optional<Chat> getChat(String chatId) {
        return chatRepository.findById(chatId)
                .map(ChatEntity::toChat);
    }

    public Chat createChat(ChatRequest request) {
        Chat chat = Chat.builder()
                .chatId(Optional.ofNullable(request.getId()).orElse(UUID.randomUUID().toString()))
                .language(request.getLanguage())
                .createdTime(System.currentTimeMillis())
                .systemMessage(request.getSystemMessage())
                .memoryLength(request.getMemoryLength())
                .name(request.getName())
                .build();

        save(chat);

        return chat;
    }

    public Chat save(Chat chat) {
        if (chat.getCreatedTime() == 0) {
            chat.setCreatedTime(System.currentTimeMillis());
        }
        ChatEntity entity = ChatEntity.fromChat(chat);
        ChatEntity savedEntity = chatRepository.save(entity);
        return savedEntity.toChat();
    }
}
package ai.driftkit.workflows.spring.service;

import ai.driftkit.common.domain.*;
import ai.driftkit.workflows.spring.domain.ChatEntity;
import ai.driftkit.workflows.spring.domain.MessageTaskEntity;
import ai.driftkit.common.domain.Language;
import ai.driftkit.workflows.spring.repository.ChatRepository;
import ai.driftkit.workflows.spring.repository.MessageTaskRepository;
import ai.driftkit.workflows.spring.service.AIService.LLMTaskFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class TasksService {

    @Autowired
    private MessageTaskRepository messageTaskRepository;

    @Autowired
    private ChatRepository chatRepository;

    @Autowired
    private AIService aiService;

    private ExecutorService exec;

    @PostConstruct
    public void init() {
        this.exec = Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
    }

    public LLMTaskFuture addTask(MessageTask messageTask) {
        Chat chat = null;
        if (messageTask.getChatId() != null) {
            Optional<ChatEntity> chatOpt = chatRepository.findById(messageTask.getChatId());
            if (chatOpt.isPresent()) {
                chat = chatOpt.get();
            }
        }

        String systemMessage = messageTask.getSystemMessage() != null || chat == null ? messageTask.getSystemMessage() : chat.getSystemMessage();

        Language language = messageTask.getLanguage() != null || chat == null ? messageTask.getLanguage() : chat.getLanguage();
        if (language == null) {
            language = Language.SPANISH; // default
        }

        messageTask.setSystemMessage(systemMessage);
        messageTask.setLanguage(language);
        MessageTaskEntity entity = MessageTaskEntity.fromMessageTask(messageTask);
        messageTaskRepository.save(entity);

        return new LLMTaskFuture(
                messageTask.getMessageId(),
                exec.submit(() -> aiService.chat(messageTask))
        );
    }

    public MessageTask rate(String messageId, Grade grade, String comment) {
        MessageTaskEntity entity = messageTaskRepository.findById(messageId).orElse(null);

        if (entity == null) {
            return null;
        }

        entity.setGradeComment(comment);
        entity.setGrade(grade);
        messageTaskRepository.save(entity);
        return MessageTaskEntity.toMessageTask(entity);
    }

    public Optional<MessageTask> getTaskByMessageId(String messageId) {
        return messageTaskRepository.findById(messageId)
                .map(MessageTaskEntity::toMessageTask);
    }

    public List<MessageTask> getTasksByChatId(String chatId, int skip, int limit, Direction direction) {
        int page = skip / limit;
        PageRequest pageRequest = PageRequest.of(page, limit, Sort.by(direction, "createdTime"));
        Page<MessageTaskEntity> messageTaskPage = messageTaskRepository.findByChatId(chatId, pageRequest);
        return messageTaskPage.getContent().stream()
                .map(MessageTaskEntity::toMessageTask)
                .collect(Collectors.toList());
    }

    public List<MessageTask> getTasks(int skip, int limit, Direction direction) {
        int page = skip / limit;
        PageRequest pageRequest = PageRequest.of(page, limit, Sort.by(direction, "createdTime"));
        Page<MessageTaskEntity> messageTaskPage = messageTaskRepository.findAll(pageRequest);
        return messageTaskPage.getContent().stream()
                .map(MessageTaskEntity::toMessageTask)
                .collect(Collectors.toList());
    }

    @NotNull
    public List<Message> getMessagesList(String chatId, Integer skip, Integer limit, Direction direction) {
        return getTasksByChatId(chatId, skip, limit, direction)
                .stream()
                .flatMap(TasksService::toMessages)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @org.jetbrains.annotations.NotNull
    private static Stream<Message> toMessages(MessageTask e) {
        Message result = null;

        if (e.getResult() == null) {
            if (e.getImageTaskId() != null) {
                result = new Message(
                        e.getMessageId(),
                        null,
                        ChatMessageType.AI,
                        MessageType.IMAGE,
                        e.getImageTaskId(),
                        e.getGrade(),
                        e.getGradeComment(),
                        e.getWorkflow(),
                        e.getContextJson(),
                        e.getResponseTime(),
                        e.getCreatedTime(),
                        e.getResponseTime(),
                        null // Image messages don't have tokenLogprobs
                );
            }
        } else {
            result = new Message(
                    e.getMessageId(),
                    e.getResult(),
                    ChatMessageType.AI,
                    MessageType.TEXT,
                    e.getImageTaskId(),
                    e.getGrade(),
                    e.getGradeComment(),
                    e.getWorkflow(),
                    e.getContextJson(),
                    e.getResponseTime(),
                    e.getCreatedTime(),
                    e.getResponseTime(),
                    e.getLogProbs()
            );
        }

        return Stream.of(
                e.toUserMessage(),
                result
        );
    }

    public void saveTasks(String chatId, List<MessageTask> messages) {
        List<MessageTaskEntity> entities = messages.stream()
                .map(message -> {
                    message.setChatId(chatId);
                    return MessageTaskEntity.fromMessageTask(message);
                })
                .collect(Collectors.toList());

        messageTaskRepository.saveAll(entities);
    }
}
package ai.driftkit.common.domain;

public interface ChatItem {

    String getMessage();

    String getMessageId();

    long getCreatedTime();

    Grade getGrade();

    String getGradeComment();

    MessageType getMessageType();
    
    ChatMessageType type();
    
    String text();
}
package org.interview.chat.memory;

import org.interview.chat.entity.ChatMessageEntity;
import org.interview.chat.entity.ChatSessionEntity;
import org.interview.chat.repository.ChatMessageRepository;
import org.interview.chat.repository.ChatSessionRepository;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JpaChatMemoryRepository implements ChatMemoryRepository {

    private final ChatMessageRepository messageRepo;
    private final ChatSessionRepository sessionRepo;

    public JpaChatMemoryRepository(ChatMessageRepository messageRepo, ChatSessionRepository sessionRepo) {
        this.messageRepo = messageRepo;
        this.sessionRepo = sessionRepo;
    }

    @Override
    public List<String> findConversationIds() {
        return sessionRepo.findAll().stream()
                .map(ChatSessionEntity::getSessionId)
                .toList();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        return messageRepo.findBySessionIdOrderByCreatedAtAsc(conversationId).stream()
                .map(this::toSpringMessage)
                .toList();
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        if (!sessionRepo.existsBySessionId(conversationId)) {
            ChatSessionEntity session = new ChatSessionEntity();
            session.setSessionId(conversationId);
            sessionRepo.save(session);
        }

        List<ChatMessageEntity> entities = messages.stream()
                .map(msg -> {
                    ChatMessageEntity e = new ChatMessageEntity();
                    e.setSessionId(conversationId);
                    e.setRole(msg.getMessageType().getValue());
                    e.setContent(msg.getText());
                    return e;
                })
                .toList();

        messageRepo.saveAll(entities);
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        messageRepo.deleteBySessionId(conversationId);
        sessionRepo.findBySessionId(conversationId).ifPresent(sessionRepo::delete);
    }

    private Message toSpringMessage(ChatMessageEntity entity) {
        String role = entity.getRole();
        if (MessageType.USER.getValue().equals(role)) {
            return new UserMessage(entity.getContent());
        }
        return new AssistantMessage(entity.getContent());
    }
}

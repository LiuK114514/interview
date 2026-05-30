package org.interview.chat.repository;

import org.interview.chat.entity.ChatSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatSessionRepository extends JpaRepository<ChatSessionEntity, Long> {
    Optional<ChatSessionEntity> findBySessionId(String sessionId);
    boolean existsBySessionId(String sessionId);
}

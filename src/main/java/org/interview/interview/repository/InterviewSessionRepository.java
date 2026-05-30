package org.interview.interview.repository;

import org.interview.interview.entity.InterviewSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InterviewSessionRepository extends JpaRepository<InterviewSessionEntity, Long> {
    Optional<InterviewSessionEntity> findBySessionId(String sessionId);
}

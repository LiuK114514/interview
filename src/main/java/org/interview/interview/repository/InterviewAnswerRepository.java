package org.interview.interview.repository;

import org.interview.interview.entity.InterviewAnswerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InterviewAnswerRepository extends JpaRepository<InterviewAnswerEntity, Long> {
    List<InterviewAnswerEntity> findBySessionIdOrderByQuestionIndex(String sessionId);
}

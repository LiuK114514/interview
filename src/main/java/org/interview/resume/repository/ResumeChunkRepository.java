package org.interview.resume.repository;

import org.interview.resume.entity.ResumeChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResumeChunkRepository extends JpaRepository<ResumeChunkEntity, Long> {
    List<ResumeChunkEntity> findByResumeIdOrderByChunkIndex(Long resumeId);
    void deleteByResumeId(Long resumeId);
}

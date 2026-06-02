package org.interview.resume.repository;

import org.interview.resume.entity.ResumeAnalysisEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResumeAnalysisRepository extends JpaRepository<ResumeAnalysisEntity, Long> {
    Optional<ResumeAnalysisEntity> findFirstByResumeIdOrderByAnalyzedAtDesc(Long resumeId);
    List<ResumeAnalysisEntity> findByResumeIdOrderByAnalyzedAtDesc(Long resumeId);
    void deleteByResumeId(Long resumeId);
}

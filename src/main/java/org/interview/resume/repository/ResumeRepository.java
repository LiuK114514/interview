package org.interview.resume.repository;

import org.interview.resume.entity.ResumeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ResumeRepository extends JpaRepository<ResumeEntity, Long> {
    Optional<ResumeEntity> findByFileHash(String fileHash);
    boolean existsByFileHash(String fileHash);
}

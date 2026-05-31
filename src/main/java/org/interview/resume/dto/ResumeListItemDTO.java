package org.interview.resume.dto;

import java.time.LocalDateTime;

public record ResumeListItemDTO(
    Long id,
    String filename,
    Long fileSize,
    LocalDateTime uploadedAt,
    Integer accessCount,
    Integer latestScore,
    LocalDateTime lastAnalyzedAt,
    String analyzeStatus,
    String analyzeError
) {}

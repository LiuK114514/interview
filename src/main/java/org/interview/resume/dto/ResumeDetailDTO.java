package org.interview.resume.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ResumeDetailDTO(
    Long id,
    String filename,
    Long fileSize,
    String contentType,
    String storageUrl,
    LocalDateTime uploadedAt,
    Integer accessCount,
    String resumeText,
    String analyzeStatus,
    String analyzeError,
    List<AnalysisHistoryDTO> analyses
) {
    public record AnalysisHistoryDTO(
        Long id,
        Integer overallScore,
        Integer contentScore,
        Integer structureScore,
        Integer skillMatchScore,
        Integer expressionScore,
        Integer projectScore,
        String summary,
        LocalDateTime analyzedAt,
        List<String> strengths,
        List<Object> suggestions
    ) {}
}

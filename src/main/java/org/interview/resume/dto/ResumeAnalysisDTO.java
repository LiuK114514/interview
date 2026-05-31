package org.interview.resume.dto;

import java.util.List;

public record ResumeAnalysisDTO(
    int overallScore,
    ScoreDetailDTO scoreDetail,
    String summary,
    List<String> strengths,
    List<SuggestionDTO> suggestions
) {
    public record ScoreDetailDTO(
        int contentScore,
        int structureScore,
        int skillMatchScore,
        int expressionScore,
        int projectScore
    ) {}

    public record SuggestionDTO(
        String category,
        String priority,
        String issue,
        String recommendation
    ) {}
}

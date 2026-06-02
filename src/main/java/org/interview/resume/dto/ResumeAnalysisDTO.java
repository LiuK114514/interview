package org.interview.resume.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ResumeAnalysisDTO(
        @JsonProperty("overallScore") int overallScore,
        @JsonProperty("scoreDetail") ScoreDetailDTO scoreDetail,
        @JsonProperty("summary") String summary,
        @JsonProperty("strengths") List<String> strengths,
        @JsonProperty("suggestions") List<SuggestionDTO> suggestions
) {
    public record ScoreDetailDTO(
            @JsonProperty("contentScore") int contentScore,
            @JsonProperty("structureScore") int structureScore,
            @JsonProperty("skillMatchScore") int skillMatchScore,
            @JsonProperty("expressionScore") int expressionScore,
            @JsonProperty("projectScore") int projectScore
    ) {}

    public record SuggestionDTO(
            @JsonProperty("category") String category,
            @JsonProperty("priority") String priority,
            @JsonProperty("issue") String issue,
            @JsonProperty("recommendation") String recommendation
    ) {}
}
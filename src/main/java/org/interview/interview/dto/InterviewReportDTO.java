package org.interview.interview.dto;

import java.util.List;

public record InterviewReportDTO(
    int totalScore,
    String overallFeedback,
    List<String> strengths,
    List<String> improvementSuggestions,
    List<CategoryScore> categoryBreakdown
) {
    public record CategoryScore(String category, int averageScore, String suggestion) {}
}

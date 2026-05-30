package org.interview.interview.dto;

import java.util.List;

public record GenerateReportResponse(
    String sessionId,
    int totalScore,
    String overallFeedback,
    List<String> strengths,
    List<String> improvementSuggestions,
    List<InterviewReportDTO.CategoryScore> categoryBreakdown
) {}

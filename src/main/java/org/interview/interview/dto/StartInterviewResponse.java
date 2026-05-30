package org.interview.interview.dto;

import java.util.List;

public record StartInterviewResponse(String sessionId, int totalQuestions, List<QuestionDTO> questions) {
}

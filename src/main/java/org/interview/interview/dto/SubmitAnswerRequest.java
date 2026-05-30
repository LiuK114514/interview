package org.interview.interview.dto;

public record SubmitAnswerRequest(String sessionId, int questionIndex, String answer) {
}
